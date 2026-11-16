package it.intesys.audiototext.crash

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import it.intesys.audiototext.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Breadcrumbs of the work in progress: if the process dies, the next start can tell what it was doing. */
interface RunTrace {
    /** A run starts; replaces the previous trace. Never put audio names or transcribed text here. */
    fun begin(info: String)
    fun note(msg: String)
    /** The run ended in any way the app itself handled (result, error, cancel). */
    fun end()

    object None : RunTrace {
        override fun begin(info: String) {}
        override fun note(msg: String) {}
        override fun end() {}
    }
}

/** How the previous process of the app ended, as reported by Android (API 30+). */
data class ExitRecord(
    val timestamp: Long,
    val reason: Int,
    val reasonName: String,
    val description: String?,
    val rssKb: Long,
    val pssKb: Long,
)

/**
 * Leaves evidence for the next launch, because a native crash or a low-memory kill cannot be caught in-process:
 *  - an uncaught Java/Kotlin exception is written to disk by a default handler;
 *  - while work is running a small trace file exists; if it is still there at the next start, the process died;
 *  - Android's own exit reason for the previous process is added to the report.
 * [collectPending] turns whatever was left into one `.log` file the user can send.
 */
class CrashReporter(
    private val dir: File,
    private val header: () -> String,
    private val exits: () -> List<ExitRecord>,
    private val now: () -> Long = System::currentTimeMillis,
) : RunTrace {
    private val traceFile = File(dir, "run.trace")
    private val uncaughtFile = File(dir, "uncaught.txt")
    private val lock = Any()

    override fun begin(info: String) = io { traceFile.writeText(stamp(info)) }
    override fun note(msg: String) = io { traceFile.appendText(stamp(msg)) }
    override fun end() = io { traceFile.delete() }

    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            recordUncaught(thread, error)
            previous?.uncaughtException(thread, error)
        }
    }

    fun recordUncaught(thread: Thread, error: Throwable) = io {
        val sw = StringWriter()
        error.printStackTrace(PrintWriter(sw))
        uncaughtFile.writeText("Thread: ${thread.name}\n$sw")
    }

    /** Builds the report for an abnormal end of the previous run, or null if it ended normally. */
    fun collectPending(): File? = synchronized(lock) {
        val uncaught = uncaughtFile.takeIf { it.isFile }?.readText()
        val trace = traceFile.takeIf { it.isFile }?.readText()
        uncaughtFile.delete(); traceFile.delete()
        if (uncaught == null && trace == null) return null

        val exitList = runCatching(exits).getOrDefault(emptyList())
        // Swiped away or stopped by the user while working: not a bug worth a report.
        if (uncaught == null && exitList.firstOrNull()?.reason in BENIGN_REASONS) return null

        val text = buildString {
            appendLine("== Sbobina: log di arresto anomalo ==")
            appendLine(runCatching(header).getOrDefault("(info dispositivo non disponibili)"))
            appendLine()
            appendLine("-- Eccezione non gestita --")
            appendLine(uncaught ?: "nessuna (il processo è stato terminato dal sistema o da un errore nativo)")
            appendLine()
            appendLine("-- Cosa stava facendo l'app (più recente in fondo) --")
            appendLine(trace ?: "nessuna operazione in corso")
            appendLine()
            appendLine("-- Motivo di chiusura dichiarato da Android (più recente per primo) --")
            if (exitList.isEmpty()) appendLine("non disponibile")
            exitList.forEach {
                appendLine("${formatTime(it.timestamp)} ${it.reasonName} rss=${it.rssKb}kB pss=${it.pssKb}kB ${it.description ?: ""}".trimEnd())
            }
        }
        dir.mkdirs()
        val file = File(dir, "sbobina-crash-${FILE_TIME.format(Instant.ofEpochMilli(now()).atZone(ZoneId.systemDefault()))}.log")
        file.writeText(text)
        pruneOldReports(keep = file)
        file
    }

    private fun pruneOldReports(keep: File) {
        dir.listFiles { f -> f.name.startsWith("sbobina-crash-") && f.name.endsWith(".log") }
            ?.sortedByDescending { it.name }
            ?.drop(MAX_REPORTS)
            ?.filter { it != keep }
            ?.forEach { it.delete() }
    }

    private fun stamp(msg: String) = "${formatTime(now())} $msg\n"

    private fun formatTime(ms: Long) =
        LINE_TIME.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

    /** Writing evidence must never become a crash of its own. */
    private fun io(block: () -> Unit) {
        synchronized(lock) {
            try {
                dir.mkdirs()
                block()
            } catch (_: Exception) {
            }
        }
    }

    companion object {
        private const val MAX_REPORTS = 3
        private val LINE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT)
        private val FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT)

        /** EXIT_SELF, PERMISSION_CHANGE, USER_REQUESTED, USER_STOPPED (ApplicationExitInfo constants, API 30). */
        private val BENIGN_REASONS = setOf(1, 8, 10, 11)

        @Volatile private var instance: CrashReporter? = null

        fun get(context: Context): CrashReporter = instance ?: synchronized(this) {
            instance ?: create(context.applicationContext).also { instance = it }
        }

        private fun create(app: Context) = CrashReporter(
            dir = File(app.filesDir, "crash"),
            header = { deviceHeader(app) },
            exits = { exitRecords(app) },
        )

        private fun deviceHeader(app: Context): String {
            val mem = ActivityManager.MemoryInfo().also {
                (app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(it)
            }
            return buildString {
                appendLine("App: v${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE}) ${BuildConfig.GIT_HASH}")
                appendLine("Dispositivo: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
                appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}), ABI ${Build.SUPPORTED_ABIS.joinToString()}")
                append("RAM: totale ${mem.totalMem shr 20} MB, libera ${mem.availMem shr 20} MB, soglia di sistema ${mem.threshold shr 20} MB")
            }
        }

        private fun exitRecords(app: Context): List<ExitRecord> {
            if (Build.VERSION.SDK_INT < 30) return emptyList()
            val am = app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            return am.getHistoricalProcessExitReasons(app.packageName, 0, 3).map {
                ExitRecord(it.timestamp, it.reason, reasonName(it.reason), it.description, it.rss, it.pss)
            }
        }

        private fun reasonName(reason: Int) = when (reason) {
            ApplicationExitInfo.REASON_CRASH -> "CRASH (eccezione Java)"
            ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE (errore nel codice nativo)"
            ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY (chiuso per poca memoria)"
            ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED (ucciso da un segnale)"
            ApplicationExitInfo.REASON_ANR -> "ANR (app non risponde)"
            ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE_USAGE"
            ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "INITIALIZATION_FAILURE"
            ApplicationExitInfo.REASON_EXIT_SELF -> "EXIT_SELF"
            ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED"
            ApplicationExitInfo.REASON_USER_STOPPED -> "USER_STOPPED"
            ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "PERMISSION_CHANGE"
            ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "DEPENDENCY_DIED"
            else -> "ALTRO ($reason)"
        }
    }
}
