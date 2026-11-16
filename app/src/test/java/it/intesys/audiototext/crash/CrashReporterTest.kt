package it.intesys.audiototext.crash

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CrashReporterTest {
    @get:Rule val tmp = TemporaryFolder()
    private var clock = 1_700_000_000_000L

    private fun reporter(dir: File = tmp.newFolder(), exits: List<ExitRecord> = emptyList()) =
        CrashReporter(dir, header = { "App: test" }, exits = { exits }, now = { clock++ })

    private fun exit(reason: Int, name: String) = ExitRecord(clock, reason, name, "desc", 100, 90)

    @Test fun clean_run_leaves_nothing_to_report() {
        val r = reporter()
        r.begin("start"); r.note("x"); r.end()
        assertNull(r.collectPending())
    }

    @Test fun nothing_happened_means_no_report() = assertNull(reporter().collectPending())

    @Test fun unfinished_run_is_reported_with_breadcrumbs_and_exit_reason() {
        val dir = tmp.newFolder()
        reporter(dir).apply { begin("trascrizione avviata"); note("caricamento del modello") }
        // new process: a fresh reporter over the same folder
        val file = reporter(dir, listOf(exit(3, "LOW_MEMORY"))).collectPending()
        assertNotNull(file)
        assertTrue(file!!.name.endsWith(".log"))
        val text = file.readText()
        assertTrue(text.contains("App: test"))
        assertTrue(text.contains("trascrizione avviata"))
        assertTrue(text.contains("caricamento del modello"))
        assertTrue(text.contains("LOW_MEMORY"))
    }

    @Test fun report_is_produced_once() {
        val dir = tmp.newFolder()
        reporter(dir).begin("start")
        val second = reporter(dir)
        assertNotNull(second.collectPending())
        assertNull(second.collectPending())
    }

    @Test fun uncaught_exception_is_reported_with_stack_trace() {
        val dir = tmp.newFolder()
        reporter(dir).recordUncaught(Thread.currentThread(), IllegalStateException("boom"))
        val text = reporter(dir).collectPending()!!.readText()
        assertTrue(text.contains("IllegalStateException: boom"))
    }

    @Test fun user_swiping_the_app_away_mid_run_is_not_a_crash() {
        val dir = tmp.newFolder()
        reporter(dir).begin("start")
        assertNull(reporter(dir, listOf(exit(10, "USER_REQUESTED"))).collectPending())
    }

    @Test fun uncaught_exception_is_reported_even_if_exit_reason_looks_benign() {
        val dir = tmp.newFolder()
        reporter(dir).recordUncaught(Thread.currentThread(), RuntimeException("x"))
        assertNotNull(reporter(dir, listOf(exit(1, "EXIT_SELF"))).collectPending())
    }

    @Test fun only_the_latest_three_reports_are_kept() {
        val dir = tmp.newFolder()
        repeat(5) {
            clock += 2_000
            reporter(dir).begin("run $it")
            reporter(dir).collectPending()
        }
        val logs = dir.listFiles { f -> f.name.endsWith(".log") }!!
        assertTrue(logs.size == 3)
        assertFalse(File(dir, "run.trace").exists())
    }

    @Test fun failing_header_does_not_prevent_the_report() {
        val dir = tmp.newFolder()
        CrashReporter(dir, header = { error("no") }, exits = { emptyList() }).begin("start")
        val file = CrashReporter(dir, header = { error("no") }, exits = { error("no") }).collectPending()
        assertNotNull(file)
    }
}
