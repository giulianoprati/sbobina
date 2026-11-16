package it.intesys.audiototext.ui

import it.intesys.audiototext.audio.AudioDecodeException
import it.intesys.audiototext.model.Downloader
import it.intesys.audiototext.model.ModelManager
import it.intesys.audiototext.model.ModelSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    @get:Rule val tmp = TemporaryFolder()
    private val dispatcher = UnconfinedTestDispatcher()

    private val spec = object : ModelSpec {
        override val fileName = "m.bin"
        override val url = "http://x/m.bin"
        override val expectedBytes = 10L
    }
    private val audio = SharedAudio(handle = "uri", displayName = "vocale.opus")
    private val speech = FloatArray(16000) { if (it % 2 == 0) 0.2f else -0.2f }

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private class OkDownloader : Downloader {
        override suspend fun download(url: String, target: File, onProgress: (Float) -> Unit) {
            onProgress(0.5f); target.writeBytes(ByteArray(10))
        }
    }

    private class FailingDownloader : Downloader {
        override suspend fun download(url: String, target: File, onProgress: (Float) -> Unit) {
            throw IOException("offline")
        }
    }

    private fun models(downloader: Downloader = OkDownloader(), ready: Boolean = true): ModelManager {
        val m = ModelManager(tmp.newFolder(), downloader)
        if (ready) m.modelFile(spec).writeBytes(ByteArray(10))
        return m
    }

    private fun vm(
        models: ModelManager = models(),
        loader: AudioLoader = AudioLoader { speech },
        transcriber: Transcriber = Transcriber { _, _ -> "ciao a tutti" },
    ) = MainViewModel(loader, transcriber, models, spec, dispatcher)

    @Test fun without_model_state_is_NeedsModel() =
        assertEquals(UiState.NeedsModel, vm(models(ready = false)).state.value)

    @Test fun with_model_initial_state_is_Idle() =
        assertEquals(UiState.Idle, vm().state.value)

    @Test fun received_audio_moves_to_Ready() {
        val v = vm()
        v.onAudioReceived(audio)
        assertEquals(UiState.Ready("vocale.opus"), v.state.value)
        assertEquals("vocale.opus", v.audioName.value)
    }

    @Test fun transcribe_moves_through_Transcribing_to_Done() {
        val seen = mutableListOf<UiState>()
        lateinit var v: MainViewModel
        v = vm(transcriber = Transcriber { _, p -> seen += v.state.value; p(50); "ciao a tutti" })
        v.onAudioReceived(audio)
        v.onTranscribeClicked()
        assertTrue(seen.single() is UiState.Transcribing)
        assertEquals(UiState.Done("ciao a tutti"), v.state.value)
    }

    @Test fun decode_failure_gives_Error_with_message() {
        val v = vm(loader = AudioLoader { throw AudioDecodeException("Formato audio non riconosciuto") })
        v.onAudioReceived(audio)
        v.onTranscribeClicked()
        assertEquals(UiState.Error("Formato audio non riconosciuto"), v.state.value)
    }

    @Test fun empty_transcription_gives_Error_Nessun_parlato_rilevato() {
        val v = vm(transcriber = Transcriber { _, _ -> "   " })
        v.onAudioReceived(audio)
        v.onTranscribeClicked()
        assertEquals(UiState.Error("Nessun parlato rilevato"), v.state.value)
    }

    @Test fun silent_audio_gives_Error_without_running_transcriber() {
        var ran = false
        val v = vm(loader = AudioLoader { FloatArray(16000) }, transcriber = Transcriber { _, _ -> ran = true; "x" })
        v.onAudioReceived(audio)
        v.onTranscribeClicked()
        assertEquals(UiState.Error("Nessun parlato rilevato"), v.state.value)
        assertFalse(ran)
    }

    @Test fun transcriber_crash_gives_Error_not_exception() {
        val v = vm(transcriber = Transcriber { _, _ -> throw IllegalStateException("Trascrizione fallita") })
        v.onAudioReceived(audio)
        v.onTranscribeClicked()
        assertEquals(UiState.Error("Trascrizione fallita"), v.state.value)
    }

    @Test fun download_then_pending_audio_becomes_Ready() {
        val v = vm(models(ready = false))
        v.onAudioReceived(audio)
        assertEquals(UiState.NeedsModel, v.state.value)
        v.onDownloadModelClicked()
        assertEquals(UiState.Ready("vocale.opus"), v.state.value)
    }

    @Test fun download_failure_gives_Error_and_retry_is_possible() {
        val m = models(FailingDownloader(), ready = false)
        val v = vm(m)
        v.onDownloadModelClicked()
        assertTrue((v.state.value as UiState.Error).message.contains("Download interrotto"))
        v.onDownloadModelClicked() // must not be stuck
        assertTrue(v.state.value is UiState.Error)
    }

    @Test fun audio_received_while_transcribing_is_ignored() {
        lateinit var v: MainViewModel
        v = vm(transcriber = Transcriber { _, _ ->
            v.onAudioReceived(SharedAudio("other", "altro.opus")); "testo"
        })
        v.onAudioReceived(audio)
        v.onTranscribeClicked()
        assertEquals("vocale.opus", v.audioName.value)
    }
}
