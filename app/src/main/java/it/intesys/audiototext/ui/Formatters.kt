package it.intesys.audiototext.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Formatters {
    /** 52_000 -> "00:52"; one hour or more -> "1:02:15". */
    fun duration(ms: Long): String {
        val total = (maxOf(ms, 0L) / 1000)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format(Locale.ITALY, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.ITALY, "%02d:%02d", m, s)
    }

    /** "Elaborato in 18 s · 2,9× la velocità reale" (ratio omitted when the audio length is unknown). */
    fun stats(elapsedMs: Long, audioMs: Long): String {
        val secs = (elapsedMs + 500) / 1000
        val took = when {
            elapsedMs < 1000 -> "meno di 1 s"
            secs < 60 -> "$secs s"
            else -> "${secs / 60} min ${secs % 60} s"
        }
        val base = "Elaborato in $took"
        if (audioMs <= 0 || elapsedMs <= 0) return base
        val ratio = audioMs.toDouble() / elapsedMs
        return base + " · " + String.format(Locale.ITALY, "%.1f", ratio) + "× la velocità reale"
    }

    /** "14 nov 2023, 22:13" in the given zone (the device's by default). */
    fun dateTime(ms: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        DATE_TIME.withZone(zone).format(Instant.ofEpochMilli(ms))

    private val DATE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ITALIAN)

    /** "ggml-large-v3-turbo-q5_0.bin" -> "large-v3-turbo-q5_0". */
    fun modelLabel(fileName: String): String = fileName.removePrefix("ggml-").removeSuffix(".bin")
}
