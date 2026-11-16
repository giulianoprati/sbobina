#include <jni.h>
#include <atomic>
#include <string>
#include <android/log.h>
#include "whisper.h"

#define TAG "WhisperJNI"

namespace {

constexpr float kNoSpeechThreshold = 0.6f;

struct Handle {
    whisper_context *ctx = nullptr;
    std::atomic<bool> cancel{false};
};

struct SegmentCtx {
    JavaVM *vm;
    jobject listener;   // global ref to it.intesys.audiototext.whisper.SegmentListener
    jmethodID method;   // onSegment([BJ)V
};

// Segments Whisper itself flags as "probably not speech" are the classic hallucinations
// ("Sottotitoli a cura di...", "Grazie a tutti") produced over noise or quiet tails.
bool is_speech(whisper_context *ctx, int i) {
    return whisper_full_get_segment_no_speech_prob(ctx, i) <= kNoSpeechThreshold;
}

bool abort_cb(void *user) {
    return static_cast<Handle *>(user)->cancel.load(std::memory_order_relaxed);
}

jbyteArray to_bytes(JNIEnv *env, const std::string &s) {
    jbyteArray out = env->NewByteArray(static_cast<jsize>(s.size()));
    if (!out) return nullptr;
    env->SetByteArrayRegion(out, 0, static_cast<jsize>(s.size()),
                            reinterpret_cast<const jbyte *>(s.data()));
    return out;
}

// Called by whisper.cpp on the thread that runs whisper_full, once per finished window.
void on_new_segment(whisper_context *ctx, whisper_state *, int n_new, void *user) {
    auto *p = static_cast<SegmentCtx *>(user);
    if (!p || !p->listener || !p->method) return;
    JNIEnv *env = nullptr;
    bool attached = false;
    if (p->vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) {
        if (p->vm->AttachCurrentThread(&env, nullptr) != JNI_OK) return;
        attached = true;
    }
    int total = whisper_full_n_segments(ctx);
    for (int i = total - n_new; i < total; i++) {
        if (i < 0) continue;
        // Non-speech segments are reported with empty text so the progress bar still advances over silence.
        const std::string text = is_speech(ctx, i) ? whisper_full_get_segment_text(ctx, i) : "";
        jbyteArray bytes = to_bytes(env, text);
        if (!bytes) {          // out of memory: an exception is pending, clear it and stop streaming
            env->ExceptionClear();
            break;
        }
        jlong end_ms = static_cast<jlong>(whisper_full_get_segment_t1(ctx, i)) * 10;  // t1 is in 10 ms units
        env->CallVoidMethod(p->listener, p->method, bytes, end_ms);
        if (env->ExceptionCheck()) env->ExceptionClear();
        env->DeleteLocalRef(bytes);
    }
    if (attached) p->vm->DetachCurrentThread();
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_it_intesys_audiototext_whisper_WhisperBridge_nativeInit(JNIEnv *env, jobject, jstring path) {
    const char *cpath = env->GetStringUTFChars(path, nullptr);
    whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = false;
    whisper_context *ctx = whisper_init_from_file_with_params(cpath, cparams);
    env->ReleaseStringUTFChars(path, cpath);
    if (!ctx) return 0;
    auto *h = new Handle();
    h->ctx = ctx;
    return reinterpret_cast<jlong>(h);
}

extern "C" JNIEXPORT void JNICALL
Java_it_intesys_audiototext_whisper_WhisperBridge_nativeFree(JNIEnv *, jobject, jlong ptr) {
    auto *h = reinterpret_cast<Handle *>(ptr);
    if (!h) return;
    whisper_free(h->ctx);
    delete h;
}

// Safe from any thread while nativeTranscribe is running: only flips an atomic read by abort_cb.
extern "C" JNIEXPORT void JNICALL
Java_it_intesys_audiototext_whisper_WhisperBridge_nativeCancel(JNIEnv *, jobject, jlong ptr) {
    auto *h = reinterpret_cast<Handle *>(ptr);
    if (h) h->cancel.store(true);
}

// Returns the transcription as UTF-8 bytes; null on failure or cancellation. Bytes, not jstring:
// JNI strings use modified UTF-8 and would break on 4-byte characters.
extern "C" JNIEXPORT jbyteArray JNICALL
Java_it_intesys_audiototext_whisper_WhisperBridge_nativeTranscribe(
        JNIEnv *env, jobject, jlong ptr, jfloatArray samples, jstring language, jint threads,
        jobject listener) {
    auto *h = reinterpret_cast<Handle *>(ptr);
    if (!h || !h->ctx) return nullptr;
    whisper_context *ctx = h->ctx;

    const char *lang = env->GetStringUTFChars(language, nullptr);
    if (!lang) return nullptr;
    jsize n = env->GetArrayLength(samples);
    jfloat *data = env->GetFloatArrayElements(samples, nullptr);
    if (!data) {
        env->ReleaseStringUTFChars(language, lang);
        return nullptr;
    }

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.language = lang;
    params.n_threads = threads;
    params.translate = false;
    params.print_progress = false;
    params.print_realtime = false;
    params.print_timestamps = false;
    params.print_special = false;
    params.suppress_nst = true;
    params.no_context = true;   // avoids repetition loops carried across 30 s windows
    params.abort_callback = abort_cb;
    params.abort_callback_user_data = h;

    SegmentCtx sctx{nullptr, nullptr, nullptr};
    if (listener) {
        env->GetJavaVM(&sctx.vm);
        sctx.listener = env->NewGlobalRef(listener);
        jclass cls = env->GetObjectClass(listener);
        sctx.method = env->GetMethodID(cls, "onSegment", "([BJ)V");
        env->DeleteLocalRef(cls);
        if (sctx.method) {
            params.new_segment_callback = on_new_segment;
            params.new_segment_callback_user_data = &sctx;
        } else {
            env->ExceptionClear();
        }
    }

    int rc = whisper_full(ctx, params, data, n);

    if (sctx.listener) env->DeleteGlobalRef(sctx.listener);
    env->ReleaseFloatArrayElements(samples, data, JNI_ABORT);
    env->ReleaseStringUTFChars(language, lang);

    if (h->cancel.load()) return nullptr;
    if (rc != 0) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "whisper_full failed: %d", rc);
        return nullptr;
    }

    std::string text;
    int segs = whisper_full_n_segments(ctx);
    for (int i = 0; i < segs; i++) {
        if (!is_speech(ctx, i)) continue;
        text += whisper_full_get_segment_text(ctx, i);
    }
    return to_bytes(env, text);
}
