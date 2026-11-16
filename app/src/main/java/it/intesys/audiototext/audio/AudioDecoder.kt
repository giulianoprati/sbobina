package it.intesys.audiototext.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File

class AudioDecodeException(message: String, cause: Throwable? = null) : Exception(message, cause)

class AudioDecoder(private val context: Context) {

    /** Decodes any audio the platform supports (incl. WhatsApp .opus) to mono 16 kHz float PCM. */
    fun decode(uri: Uri): FloatArray {
        // The share permission on the URI may expire: copy to cache first.
        val tmp = File.createTempFile("share", ".audio", context.cacheDir)
        try {
            val input = try {
                context.contentResolver.openInputStream(uri)
            } catch (e: Exception) {
                throw AudioDecodeException("Impossibile aprire il file audio", e)
            } ?: throw AudioDecodeException("Impossibile aprire il file audio")
            input.use { src -> tmp.outputStream().use { src.copyTo(it) } }
            if (tmp.length() == 0L) throw AudioDecodeException("Il file audio è vuoto")
            return decodeFile(tmp)
        } finally {
            tmp.delete()
        }
    }

    private fun decodeFile(file: File): FloatArray {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            try {
                extractor.setDataSource(file.absolutePath)
            } catch (e: Exception) {
                throw AudioDecodeException("Formato audio non riconosciuto", e)
            }
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: throw AudioDecodeException("Nessuna traccia audio nel file")
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME)!!
            codec = try {
                MediaCodec.createDecoderByType(mime).also {
                    it.configure(format, null, null, 0)
                    it.start()
                }
            } catch (e: Exception) {
                throw AudioDecodeException("Formato audio non supportato ($mime)", e)
            }

            var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val pcmBytes = ByteArrayOutputStream()
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            while (!outputDone) {
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
                        val out = codec.getOutputBuffer(outIdx)!!
                        val chunk = ByteArray(info.size)
                        out.get(chunk)
                        pcmBytes.write(chunk)
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
            val bytes = pcmBytes.toByteArray()
            if (bytes.isEmpty()) throw AudioDecodeException("Il file audio è vuoto")
            val shorts = ShortArray(bytes.size / 2)
            java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
            return PcmConverter.toMono16k(shorts, sampleRate, channels)
        } finally {
            codec?.runCatching { stop(); release() }
            extractor.release()
        }
    }
}
