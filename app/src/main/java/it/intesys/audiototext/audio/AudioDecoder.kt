package it.intesys.audiototext.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import it.intesys.audiototext.R
import it.intesys.audiototext.text.LocalizedException
import it.intesys.audiototext.text.UiText
import it.intesys.audiototext.text.uiText
import java.io.File
import java.nio.ByteOrder

class AudioDecodeException(uiText: UiText, cause: Throwable? = null) : LocalizedException(uiText, cause)

// Loops without any decoded output before giving up (~1 minute): protects against a codec that never signals the end.
private const val MAX_IDLE_LOOPS = 3000

class AudioDecoder(private val context: Context) {

    companion object {
        /** Deletes copies left in [cacheDir] if the process died mid-decode. Returns how many were removed. */
        fun deleteStaleTempFiles(cacheDir: File): Int =
            cacheDir.listFiles { f -> f.isFile && f.name.startsWith("share") && f.name.endsWith(".audio") }
                ?.count { it.delete() } ?: 0
    }

    /** Decodes any audio the platform supports (incl. WhatsApp .opus) to mono 16 kHz float PCM. */
    fun decode(uri: Uri, isCancelled: () -> Boolean = { false }): FloatArray {
        // The share permission on the URI may expire: copy to cache first.
        val tmp = File.createTempFile("share", ".audio", context.cacheDir)
        try {
            val input = try {
                context.contentResolver.openInputStream(uri)
            } catch (e: Exception) {
                throw AudioDecodeException(UiText.Res(R.string.err_audio_open), e)
            } ?: throw AudioDecodeException(UiText.Res(R.string.err_audio_open))
            input.use { src -> tmp.outputStream().use { src.copyTo(it) } }
            if (tmp.length() == 0L) throw AudioDecodeException(UiText.Res(R.string.err_audio_empty))
            return try {
                decodeFile(tmp, isCancelled)
            } catch (e: IllegalStateException) {
                // MediaCodec.CodecException is an IllegalStateException with an English, technical message
                throw AudioDecodeException(UiText.Res(R.string.err_audio_decode), e)
            }
        } finally {
            tmp.delete()
        }
    }

    private fun decodeFile(file: File, isCancelled: () -> Boolean): FloatArray {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            try {
                extractor.setDataSource(file.absolutePath)
            } catch (e: Exception) {
                throw AudioDecodeException(UiText.Res(R.string.err_audio_format), e)
            }
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: throw AudioDecodeException(UiText.Res(R.string.err_audio_no_track))
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME)!!
            codec = try {
                MediaCodec.createDecoderByType(mime).also {
                    it.configure(format, null, null, 0)
                    it.start()
                }
            } catch (e: Exception) {
                throw AudioDecodeException(uiText(R.string.err_audio_unsupported, mime), e)
            }

            var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            // Convert each decoded buffer straight to 16 kHz mono floats: keeping the full-rate
            // PCM would cost ~0.5 MB per second of audio and overflow the heap on long notes.
            var converter: PcmConverter.Streaming? = null
            var chunk = ShortArray(0)
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var idleLoops = 0
            while (!outputDone) {
                if (isCancelled()) throw AudioDecodeException(UiText.Res(R.string.err_audio_cancelled))
                if (++idleLoops > MAX_IDLE_LOOPS) throw AudioDecodeException(UiText.Res(R.string.err_audio_decode_failed))
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val buf = codec.getInputBuffer(inIdx)!!
                        val size = extractor.readSampleData(buf, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIdx, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIdx = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    outIdx >= 0 -> {
                        idleLoops = 0
                        val out = codec.getOutputBuffer(outIdx)!!
                        if (info.size > 0) {
                            out.position(info.offset)
                            out.limit(info.offset + info.size)
                            val shorts = out.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                            val n = shorts.remaining()
                            if (chunk.size < n) chunk = ShortArray(n)
                            shorts.get(chunk, 0, n)
                            // Parameters come from an untrusted file: reject nonsense before it reaches the converter.
                            if (sampleRate < 1 || channels < 1) throw AudioDecodeException(UiText.Res(R.string.err_audio_decode))
                            val conv = converter ?: PcmConverter.Streaming(sampleRate, channels).also { converter = it }
                            conv.feed(chunk, n)
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val f = codec.outputFormat
                        sampleRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                }
            }
            val samples = converter?.finish() ?: FloatArray(0)
            if (samples.isEmpty()) throw AudioDecodeException(UiText.Res(R.string.err_audio_empty))
            return samples
        } finally {
            codec?.runCatching { stop(); release() }
            extractor.release()
        }
    }
}
