package it.intesys.audiototext.ui

import it.intesys.audiototext.R
import it.intesys.audiototext.text.UiText
import it.intesys.audiototext.text.uiText
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Formatters {
    /** 52_000 -> "00:52"; one hour or more -> "1:02:15". Digits only, so the same in every language. */
    fun duration(ms: Long): String {
        val total = (maxOf(ms, 0L) / 1000)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.ROOT, "%02d:%02d", m, s)
    }

    /** 8_100_000 -> "2 h 15 min"; 120_000 -> "2 min"; under a minute -> "less than 1 min". */
    fun approx(ms: Long): UiText {
        val mins = maxOf(ms, 0L) / 60_000
        return when {
            mins < 1 -> UiText.Res(R.string.fmt_less_than_min)
            mins < 60 -> uiText(R.string.fmt_min, mins.toInt())
            mins % 60 == 0L -> uiText(R.string.fmt_hours, (mins / 60).toInt())
            else -> uiText(R.string.fmt_hours_min, (mins / 60).toInt(), (mins % 60).toInt())
        }
    }

    /** 12345 -> "12,345" in English, "12.345" in Italian. */
    fun count(n: Int, locale: Locale = Locale.getDefault()): String = String.format(locale, "%,d", n)

    /** "Processed in 18 s · 2.9× real time" (ratio omitted when the audio length is unknown). */
    fun stats(elapsedMs: Long, audioMs: Long, locale: Locale = Locale.getDefault()): UiText {
        val secs = (elapsedMs + 500) / 1000
        val took = when {
            elapsedMs < 1000 -> UiText.Res(R.string.fmt_less_than_sec)
            secs < 60 -> uiText(R.string.fmt_sec, secs.toInt())
            else -> uiText(R.string.fmt_min_sec, (secs / 60).toInt(), (secs % 60).toInt())
        }
        if (audioMs <= 0 || elapsedMs <= 0) return uiText(R.string.stats_took, took)
        val ratio = String.format(locale, "%.1f", audioMs.toDouble() / elapsedMs)
        return uiText(R.string.stats_took_ratio, took, ratio)
    }

    /** "14 Nov 2023, 22:13" / "14 nov 2023, 22:13" in the given zone and locale (the device's by default). */
    fun dateTime(
        ms: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault(),
    ): String = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", locale).withZone(zone).format(Instant.ofEpochMilli(ms))

    /** "ggml-large-v3-turbo-q5_0.bin" -> "large-v3-turbo-q5_0". */
    fun modelLabel(fileName: String): String = fileName.removePrefix("ggml-").removeSuffix(".bin")
}
