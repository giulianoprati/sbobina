package it.intesys.audiototext.ui

import it.intesys.audiototext.R
import it.intesys.audiototext.text.LocalizedException
import it.intesys.audiototext.text.UiText
import it.intesys.audiototext.audio.AudioDecodeException
import it.intesys.audiototext.history.FakeHistoryStore
import it.intesys.audiototext.history.HistoryEntry
import it.intesys.audiototext.history.HistoryStore
import it.intesys.audiototext.model.ModelCorruptException
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
import org.junit.Assert.assertNull
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
    private var nowMs = 1_000L

    private val spec = object : ModelSpec {
        override val fileName = "ggml-m.bin"
        override val url = "http://x/m.bin"
        override val expectedBytes = 10L
    }
    private val sharedAudio = SharedAudio(handle = "uri", displayName = "vocale.opus", durationMs = 52_000)
    private val speech16k = FloatArray(16000) { if (it % 2 == 0) 0.2f else -0.2f }
    private val speech52s = FloatArray(16000 * 52) { if (it % 2 == 0) 0.2f else -0.2f }

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

    private class FakeTranscriber(val body: (FloatArray, TranscriptionListener) -> String) : Transcriber {
        var cancelCalls = 0
        override fun transcribe(samples: FloatArray, listener: TranscriptionListener) = body(samples, listener)
        override fun cancel() { cancelCalls++ }
    }

    private fun models(downloader: Downloader = OkDownloader(), ready: Boolean = true): ModelManager {
        val m = ModelManager(tmp.newFolder(), downloader)
        if (ready) m.modelFile(spec).writeBytes(ByteArray(10))
        return m
    }

    private val history = FakeHistoryStore()
    private val resultsReady = mutableListOf<HistoryEntry>()
    private var idCounter = 0

    private fun vm(
        models: ModelManager = models(),
        loader: AudioLoader = AudioLoader { _, _ -> speech16k },
        transcriber: Transcriber = FakeTranscriber { _, _ -> "ciao a tutti" },
        busy: (Boolean) -> Unit = {},
        historyStore: HistoryStore = history,
        choices: List<ModelSpec> = listOf(spec),
        chosen: MutableList<ModelSpec> = mutableListOf(),
        availableMemory: () -> Long = { Long.MAX_VALUE },
    ) = MainViewModel(
        choices = choices,
        onSpecChosen = { chosen += it },
        availableMemory = availableMemory,
        loader = loader,
        transcriber = transcriber,
        models = models,
        initialSpec = spec,
        history = historyStore,
        workDispatcher = dispatcher,
        onBusyChanged = busy,
        onResultReady = { resultsReady += it },
        newId = { "id${++idCounter}" },
        now = { nowMs },
    )

    @Test fun shared_time_is_recorded_when_the_file_arrives_not_when_transcribed() {
        nowMs = 5_000
        val v = vm()
        v.onAudioReceived(sharedAudio)
        assertEquals(5_000L, v.audio.value!!.sharedAtMs)
        nowMs = 9_000
        v.onTranscribeClicked()
        assertEquals(5_000L, history.entries.value.single().sharedAtMs)
        assertEquals(9_000L, history.entries.value.single().transcribedAtMs)
    }

    @Test fun done_is_saved_to_history_with_all_fields() {
        val v = vm(loader = AudioLoader { _, _ -> speech52s })
        v.transcribeAudio()
        val e = history.entries.value.single()
        assertEquals("id1", e.id)
        assertEquals("vocale.opus", e.fileName)
        assertEquals("ciao a tutti", e.text)
        assertEquals(52_000L, e.audioDurationMs)
        assertEquals((v.state.value as UiState.Done).elapsedMs, e.elapsedMs)
    }

    @Test fun result_ready_callback_gets_the_saved_entry() {
        vm().transcribeAudio()
        assertEquals(history.entries.value, resultsReady)
    }

    @Test fun errors_are_not_saved_or_notified() {
        vm(transcriber = FakeTranscriber { _, _ -> "  " }).transcribeAudio()
        vm(loader = AudioLoader { _, _ -> throw AudioDecodeException(UiText.Plain("x")) }).transcribeAudio()
        assertTrue(history.entries.value.isEmpty())
        assertTrue(resultsReady.isEmpty())
    }

    @Test fun cancelled_run_is_not_saved_or_notified() {
        lateinit var v: MainViewModel
        v = vm(transcriber = FakeTranscriber { _, _ -> v.onCancelClicked(); "testo" })
        v.transcribeAudio()
        assertTrue(history.entries.value.isEmpty())
        assertTrue(resultsReady.isEmpty())
    }

    @Test fun history_failure_does_not_lose_the_result() {
        val broken = FakeHistoryStore().apply { failOnAdd = true }
        val v = vm(historyStore = broken)
        v.transcribeAudio()
        assertEquals("ciao a tutti", (v.state.value as UiState.Done).text)
    }

    @Test fun corrupt_model_error_offers_download() {
        val msg = UiText.Res(R.string.err_model_damaged_redownload)
        val v = vm(transcriber = FakeTranscriber { _, _ -> throw ModelCorruptException(msg) })
        v.transcribeAudio()
        assertEquals(UiState.Error(msg, Retry.DOWNLOAD), v.state.value)
    }

    @Test fun first_use_model_verification_is_reported_as_its_own_phase() {
        lateinit var v: MainViewModel
        var phase: Phase? = null
        v = vm(transcriber = FakeTranscriber { _, l ->
            l.onVerifyingModel()
            phase = (v.state.value as UiState.Transcribing).phase
            "ok"
        })
        v.transcribeAudio()
        assertEquals(Phase.VERIFYING_MODEL, phase)
    }

    @Test fun failing_notification_callback_does_not_crash_or_hide_the_result() {
        val v = MainViewModel(
            loader = AudioLoader { _, _ -> speech16k },
            transcriber = FakeTranscriber { _, _ -> "ciao" },
            models = models(), initialSpec = spec, history = history,
            workDispatcher = dispatcher,
            onResultReady = { throw SecurityException("no permission") },
            newId = { "x" }, now = { nowMs },
        )
        v.transcribeAudio()
        assertEquals("ciao", (v.state.value as UiState.Done).text)
    }

    @Test fun nothing_is_saved_when_cancel_wins_the_race_before_publishing() {
        lateinit var v: MainViewModel
        val store = object : HistoryStore by FakeHistoryStore() {
            override fun add(entry: HistoryEntry) { throw AssertionError("must not save a cancelled run") }
        }
        v = vm(transcriber = FakeTranscriber { _, _ -> v.onCancelClicked(); "testo" }, historyStore = store)
        v.transcribeAudio()
        assertEquals(UiState.Ready("vocale.opus"), v.state.value)
    }

    @Test fun decoder_can_see_the_cancel_request() {
        lateinit var v: MainViewModel
        var seen = false
        var ran = false
        v = vm(
            loader = AudioLoader { _, isCancelled -> v.onCancelClicked(); seen = isCancelled(); speech16k },
            transcriber = FakeTranscriber { _, _ -> ran = true; "x" },
        )
        v.transcribeAudio()
        assertTrue(seen)
        assertFalse(ran)
        assertEquals(UiState.Ready("vocale.opus"), v.state.value)
    }

    private fun MainViewModel.transcribeAudio() { onAudioReceived(sharedAudio); onTranscribeClicked() }

    @Test fun without_model_state_is_NeedsModel() =
        assertEquals(UiState.NeedsModel, vm(models(ready = false)).state.value)

    @Test fun with_model_initial_state_is_Idle() = assertEquals(UiState.Idle, vm().state.value)

    @Test fun model_label_comes_from_spec() = assertEquals("m", vm().modelLabel)

    private val other = object : ModelSpec {
        override val fileName = "ggml-o.bin"
        override val url = "http://x/o.bin"
        override val expectedBytes = 10L
    }

    @Test fun choosing_a_downloaded_model_keeps_the_shared_audio_ready_and_is_reported() {
        val m = models()
        m.modelFile(other).writeBytes(ByteArray(10))
        val chosen = mutableListOf<ModelSpec>()
        val v = vm(models = m, choices = listOf(spec, other), chosen = chosen)
        v.onAudioReceived(sharedAudio)
        v.onModelChosen(other)
        assertEquals(other, v.spec.value)
        assertEquals("o", v.modelLabel)
        assertEquals(UiState.Ready("vocale.opus"), v.state.value)
        assertEquals(listOf(other), chosen)
    }

    @Test fun choosing_a_model_not_yet_downloaded_asks_for_download() {
        val v = vm(choices = listOf(spec, other))
        v.onAudioReceived(sharedAudio)
        v.onModelChosen(other)
        assertEquals(UiState.NeedsModel, v.state.value)
    }

    @Test fun download_fetches_the_chosen_model() {
        val m = models()
        val v = vm(models = m, choices = listOf(spec, other))
        v.onModelChosen(other)
        v.onDownloadModelClicked()
        assertTrue(m.isReady(other))
        assertEquals(UiState.Idle, v.state.value)
    }

    @Test fun choosing_is_ignored_while_transcribing_and_for_unknown_models() {
        val chosen = mutableListOf<ModelSpec>()
        lateinit var v: MainViewModel
        var duringRun: ModelSpec? = null
        val t = FakeTranscriber { _, _ ->
            v.onModelChosen(other)
            duringRun = v.spec.value
            "testo"
        }
        v = vm(transcriber = t, choices = listOf(spec, other), chosen = chosen)
        v.transcribeAudio()
        assertEquals(spec, duringRun)
        v.onModelChosen(object : ModelSpec {
            override val fileName = "x.bin"; override val url = "u"; override val expectedBytes = 1L
        })
        assertEquals(spec, v.spec.value)
        assertTrue(chosen.isEmpty())
    }

    @Test fun options_are_hidden_by_default_and_toggled_by_tapping_the_model_name() {
        val v = vm(choices = listOf(spec, other))
        assertFalse(v.pickerOpen.value)
        v.onModelLabelClicked()
        assertTrue(v.pickerOpen.value)
        v.onModelLabelClicked()
        assertFalse(v.pickerOpen.value)
    }

    @Test fun on_a_fresh_install_options_show_until_a_choice_is_made_even_if_it_is_the_default() {
        val chosen = mutableListOf<ModelSpec>()
        val v = MainViewModel(
            loader = AudioLoader { _, _ -> speech16k },
            transcriber = FakeTranscriber { _, _ -> "x" },
            models = models(ready = false), initialSpec = spec, history = history,
            choices = listOf(spec, other), onSpecChosen = { chosen += it }, askModel = true,
        )
        assertTrue(v.pickerOpen.value)
        v.onModelChosen(spec)
        assertFalse(v.pickerOpen.value)
        assertEquals(listOf(spec), chosen)  // the default is saved too, so the question is not asked again
    }

    @Test fun picking_a_model_hides_the_options_again() {
        val v = vm(choices = listOf(spec, other))
        v.onModelLabelClicked()
        v.onModelChosen(other)
        assertFalse(v.pickerOpen.value)
    }

    @Test fun the_model_name_cannot_be_toggled_while_transcribing() {
        lateinit var v: MainViewModel
        var openDuring = true
        v = vm(transcriber = FakeTranscriber { _, _ -> v.onModelLabelClicked(); openDuring = v.pickerOpen.value; "x" })
        v.transcribeAudio()
        assertFalse(openDuring)
    }

    @Test fun memory_warning_follows_available_memory_and_audio_length() {
        var avail = Long.MAX_VALUE
        val v = vm(availableMemory = { avail })
        v.onAudioReceived(sharedAudio)
        assertNull(v.memoryWarning())
        avail = 1L
        assertTrue(v.memoryWarning() != null)
    }

    @Test fun received_audio_moves_to_Ready_with_name_and_duration() {
        val v = vm()
        v.onAudioReceived(sharedAudio)
        assertEquals(UiState.Ready("vocale.opus"), v.state.value)
        assertEquals(AudioInfo("vocale.opus", 52_000, 1_000), v.audio.value)
    }

    @Test fun missing_metadata_duration_is_filled_from_decoded_samples() {
        val v = vm(loader = AudioLoader { _, _ -> speech52s })
        v.onAudioReceived(SharedAudio("uri", "v.opus", durationMs = null))
        assertNull(v.audio.value!!.durationMs)
        v.onTranscribeClicked()
        assertEquals(52_000L, v.audio.value!!.durationMs)
    }

    @Test fun phases_stream_text_and_progress() {
        val seen = mutableListOf<UiState.Transcribing>()
        lateinit var v: MainViewModel
        val t = FakeTranscriber { _, l ->
            fun snap() { seen += v.state.value as UiState.Transcribing }
            snap()
            l.onLoadingModel(); snap()
            l.onModelLoaded(); snap()
            l.onSegment(" ciao", 26_000); snap()
            l.onSegment(" a tutti", 52_000); snap()
            "ciao a tutti"
        }
        v = vm(loader = AudioLoader { _, _ -> speech52s }, transcriber = t)
        v.transcribeAudio()

        assertEquals(Phase.PREPARING, seen[0].phase)
        assertNull(seen[0].progress)
        assertEquals(1_000L, seen[0].startedAtMs)
        assertEquals(Phase.LOADING_MODEL, seen[1].phase)
        assertEquals(Phase.TRANSCRIBING, seen[2].phase)
        assertNull(seen[2].progress)
        assertEquals(0.5f, seen[3].progress!!, 1e-6f)
        assertEquals(" ciao", seen[3].partialText)
        assertEquals(1.0f, seen[4].progress!!, 1e-6f)
        assertEquals(" ciao a tutti", seen[4].partialText)
    }

    @Test fun done_includes_text_and_stats() {
        val t = FakeTranscriber { _, _ -> nowMs += 18_000; "ciao a tutti" }
        val v = vm(loader = AudioLoader { _, _ -> speech52s }, transcriber = t)
        v.transcribeAudio()
        assertEquals(
            UiState.Done("ciao a tutti", 18_000, 52_000),
            v.state.value,
        )
    }

    @Test fun decode_failure_gives_Error_with_message() {
        val v = vm(loader = AudioLoader { _, _ -> throw AudioDecodeException(UiText.Res(R.string.err_audio_format)) })
        v.transcribeAudio()
        assertEquals(UiState.Error(UiText.Res(R.string.err_audio_format)), v.state.value)
    }

    @Test fun empty_transcription_gives_Error_no_speech_detected() {
        val v = vm(transcriber = FakeTranscriber { _, _ -> "   " })
        v.transcribeAudio()
        assertEquals(UiState.Error(UiText.Res(R.string.err_no_speech)), v.state.value)
    }

    @Test fun silent_audio_gives_Error_without_running_transcriber() {
        var ran = false
        val v = vm(loader = AudioLoader { _, _ -> FloatArray(16000) }, transcriber = FakeTranscriber { _, _ -> ran = true; "x" })
        v.transcribeAudio()
        assertEquals(UiState.Error(UiText.Res(R.string.err_no_speech)), v.state.value)
        assertFalse(ran)
    }

    @Test fun our_own_localized_failure_message_is_shown() {
        val v = vm(transcriber = FakeTranscriber { _, _ -> throw LocalizedException(UiText.Res(R.string.err_transcription_failed)) })
        v.transcribeAudio()
        assertEquals(UiState.Error(UiText.Res(R.string.err_transcription_failed)), v.state.value)
    }

    @Test fun unexpected_exception_gives_generic_message_with_type() {
        val v = vm(transcriber = FakeTranscriber { _, _ -> throw RuntimeException("boom") })
        v.transcribeAudio()
        assertEquals(UiState.Error(UiText.Res(R.string.err_unexpected, listOf("RuntimeException"))), v.state.value)
    }

    @Test fun out_of_memory_on_long_audio_gives_Error_not_crash() {
        val v = vm(loader = AudioLoader { _, _ -> throw OutOfMemoryError() })
        v.transcribeAudio()
        assertEquals(UiState.Error(UiText.Res(R.string.err_audio_too_long)), v.state.value)
    }

    @Test fun download_then_pending_audio_becomes_Ready() {
        val v = vm(models(ready = false))
        v.onAudioReceived(sharedAudio)
        assertEquals(UiState.NeedsModel, v.state.value)
        v.onDownloadModelClicked()
        assertEquals(UiState.Ready("vocale.opus"), v.state.value)
    }

    @Test fun download_failure_offers_download_retry_and_is_not_stuck() {
        val v = vm(models(FailingDownloader(), ready = false))
        v.onDownloadModelClicked()
        val e = v.state.value as UiState.Error
        assertEquals(UiText.Res(R.string.err_download_interrupted), e.message)
        assertEquals(Retry.DOWNLOAD, e.retry)
        v.onDownloadModelClicked()
        assertTrue(v.state.value is UiState.Error)
    }

    @Test fun audio_received_while_transcribing_is_ignored() {
        lateinit var v: MainViewModel
        v = vm(transcriber = FakeTranscriber { _, _ ->
            v.onAudioReceived(SharedAudio("other", "altro.opus")); "testo"
        })
        v.transcribeAudio()
        assertEquals("vocale.opus", v.audio.value!!.name)
    }

    @Test fun cancel_during_transcription_returns_to_Ready_and_stops_the_engine() {
        lateinit var v: MainViewModel
        val t = FakeTranscriber { _, _ ->
            v.onCancelClicked()
            assertEquals(UiState.Cancelling, v.state.value)
            throw IllegalStateException("Trascrizione annullata")
        }
        v = vm(transcriber = t)
        v.transcribeAudio()
        assertEquals(UiState.Ready("vocale.opus"), v.state.value)
        assertEquals(1, t.cancelCalls)
    }

    @Test fun cancelled_run_never_shows_a_result_even_if_engine_returns_text() {
        lateinit var v: MainViewModel
        v = vm(transcriber = FakeTranscriber { _, _ -> v.onCancelClicked(); "testo parziale" })
        v.transcribeAudio()
        assertEquals(UiState.Ready("vocale.opus"), v.state.value)
    }

    @Test fun cancel_when_not_transcribing_is_a_noop() {
        val t = FakeTranscriber { _, _ -> "x" }
        val v = vm(transcriber = t)
        v.onAudioReceived(sharedAudio)
        v.onCancelClicked()
        assertEquals(UiState.Ready("vocale.opus"), v.state.value)
        assertEquals(0, t.cancelCalls)
    }

    @Test fun can_transcribe_again_after_cancel() {
        lateinit var v: MainViewModel
        var first = true
        v = vm(transcriber = FakeTranscriber { _, _ ->
            if (first) { first = false; v.onCancelClicked(); "x" } else "ok"
        })
        v.transcribeAudio()
        v.onTranscribeClicked()
        assertTrue(v.state.value is UiState.Done)
    }

    @Test fun late_duration_fills_in_for_same_audio() {
        val v = vm()
        v.onAudioReceived(SharedAudio("uri", "v.opus", durationMs = null))
        v.onAudioDurationKnown("uri", 52_000)
        assertEquals(AudioInfo("v.opus", 52_000, 1_000), v.audio.value)
    }

    @Test fun late_duration_for_other_audio_is_ignored() {
        val v = vm()
        v.onAudioReceived(SharedAudio("uri", "v.opus", durationMs = null))
        v.onAudioDurationKnown("older-uri", 9_000)
        assertNull(v.audio.value!!.durationMs)
    }

    @Test fun cancel_is_visible_to_the_engine_through_the_listener() {
        lateinit var v: MainViewModel
        var before = true
        var after = false
        v = vm(transcriber = FakeTranscriber { _, l ->
            before = l.isCancelled()
            v.onCancelClicked()
            after = l.isCancelled()
            throw IllegalStateException("Trascrizione annullata")
        })
        v.transcribeAudio()
        assertFalse(before)
        assertTrue(after)
    }

    @Test fun cancel_after_result_cannot_overwrite_it() {
        val t = FakeTranscriber { _, _ -> "ciao" }
        val v = vm(transcriber = t)
        v.transcribeAudio()
        val done = v.state.value
        v.onCancelClicked()
        assertEquals(done, v.state.value)
        assertEquals(0, t.cancelCalls)
    }

    @Test fun engine_progress_cannot_overwrite_Cancelling() {
        lateinit var v: MainViewModel
        var seen: UiState? = null
        v = vm(transcriber = FakeTranscriber { _, l ->
            v.onCancelClicked()
            l.onModelLoaded(); l.onSegment(" x", 1_000)
            seen = v.state.value
            "x"
        })
        v.transcribeAudio()
        assertEquals(UiState.Cancelling, seen)
    }

    @Test fun busy_callback_wraps_transcription_once() {
        val calls = mutableListOf<Boolean>()
        vm(busy = { calls += it }).transcribeAudio()
        assertEquals(listOf(true, false), calls)
    }

    @Test fun busy_callback_released_after_failure() {
        val calls = mutableListOf<Boolean>()
        vm(loader = AudioLoader { _, _ -> throw AudioDecodeException(UiText.Plain("x")) }, busy = { calls += it }).transcribeAudio()
        assertEquals(listOf(true, false), calls)
    }

    @Test fun busy_callback_wraps_download() {
        val calls = mutableListOf<Boolean>()
        vm(models(ready = false), busy = { calls += it }).onDownloadModelClicked()
        assertEquals(listOf(true, false), calls)
    }
}
