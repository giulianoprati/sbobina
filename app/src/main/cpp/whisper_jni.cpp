#include <jni.h>
#include <string>
#include <android/log.h>
#include "whisper.h"

#define TAG "WhisperJNI"

namespace {
struct ProgressCtx {
    JavaVM *vm;
    jobject listener;   // global ref to it.intesys.audiototext.whisper.ProgressListener
    jmethodID method;
};

void on_progress(struct whisper_context *, struct whisper_state *, int progress, void *user) {
    auto *p = static_cast<ProgressCtx *>(user);
    if (!p || !p->listener) return;
    JNIEnv *env = nullptr;
    bool attached = false;
    if (p->vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) {
        if (p->vm->AttachCurrentThread(&env, nullptr) != JNI_OK) return;
        attached = true;
    }
    env->CallVoidMethod(p->listener, p->method, static_cast<jint>(progress));
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
    return reinterpret_cast<jlong>(ctx);
}

extern "C" JNIEXPORT void JNICALL
Java_it_intesys_audiototext_whisper_WhisperBridge_nativeFree(JNIEnv *, jobject, jlong ptr) {
    if (ptr) whisper_free(reinterpret_cast<whisper_context *>(ptr));
}

// Returns the transcription as UTF-8 bytes (null on failure). Bytes, not jstring:
// JNI strings use modified UTF-8 and would break on 4-byte characters.
extern "C" JNIEXPORT jbyteArray JNICALL
Java_it_intesys_audiototext_whisper_WhisperBridge_nativeTranscribe(
        JNIEnv *env, jobject, jlong ptr, jfloatArray samples, jstring language, jint threads,
        jobject listener) {
    auto *ctx = reinterpret_cast<whisper_context *>(ptr);
    if (!ctx) return nullptr;

    const char *lang = env->GetStringUTFChars(language, nullptr);
    jsize n = env->GetArrayLength(samples);
    jfloat *data = env->GetFloatArrayElements(samples, nullptr);

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

    ProgressCtx pctx{nullptr, nullptr, nullptr};
    if (listener) {
        env->GetJavaVM(&pctx.vm);
        pctx.listener = env->NewGlobalRef(listener);
        jclass cls = env->GetObjectClass(listener);
        pctx.method = env->GetMethodID(cls, "onProgress", "(I)V");
        params.progress_callback = on_progress;
        params.progress_callback_user_data = &pctx;
    }

    int rc = whisper_full(ctx, params, data, n);

    if (pctx.listener) env->DeleteGlobalRef(pctx.listener);
    env->ReleaseFloatArrayElements(samples, data, JNI_ABORT);
    env->ReleaseStringUTFChars(language, lang);
    if (rc != 0) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "whisper_full failed: %d", rc);
        return nullptr;
    }

    std::string text;
    int segs = whisper_full_n_segments(ctx);
    for (int i = 0; i < segs; i++) text += whisper_full_get_segment_text(ctx, i);

    jbyteArray out = env->NewByteArray(static_cast<jsize>(text.size()));
    env->SetByteArrayRegion(out, 0, static_cast<jsize>(text.size()),
                            reinterpret_cast<const jbyte *>(text.data()));
    return out;
}
