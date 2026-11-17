package it.intesys.audiototext.model

import android.content.SharedPreferences
import it.intesys.audiototext.R
import it.intesys.audiototext.text.UiText

/** Pure rules for picking a model and warning when memory looks short. */
object ModelAdvisor {
    /** Phones report a bit less than the nominal RAM (a "6 GB" phone shows about 5.5 GiB). */
    const val TURBO_MIN_TOTAL_RAM: Long = 5L * 1024 * 1024 * 1024

    /** 16 samples/ms * 4 bytes, doubled: the Java array plus the copy JNI may make of it. */
    private const val AUDIO_BYTES_PER_MS = 16L * 4 * 2

    fun recommend(totalRamBytes: Long): ModelKind =
        if (totalRamBytes >= TURBO_MIN_TOTAL_RAM) ModelKind.TURBO else ModelKind.SMALL

    /** Null when memory looks sufficient; otherwise a message for the user (a warning, never a block). */
    fun warning(spec: ModelSpec, availableBytes: Long, audioDurationMs: Long?): UiText? {
        val needed = spec.workingSetBytes + (audioDurationMs ?: 0L) * AUDIO_BYTES_PER_MS
        if (availableBytes >= needed) return null
        return UiText.Res(if (spec === ModelKind.TURBO) R.string.warn_memory_turbo else R.string.warn_memory)
    }
}

/** Remembers the model the user picked. */
class ModelPrefs(private val prefs: SharedPreferences) {
    fun saved(): ModelKind? =
        prefs.getString(KEY, null)?.let { name -> ModelKind.entries.firstOrNull { it.name == name } }

    fun save(spec: ModelSpec) {
        (spec as? ModelKind)?.let { prefs.edit().putString(KEY, it.name).apply() }
    }

    private companion object {
        const val KEY = "model"
    }
}
