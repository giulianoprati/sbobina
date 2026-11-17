package it.intesys.audiototext.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WaitingMessagesTest {
    private val all = listOf("a", "b", "c", "d", "e")

    @Test fun same_moment_gives_same_message() =
        assertEquals(WaitingMessages.at(all, 7, 3_000), WaitingMessages.at(all, 7, 7_999))

    @Test fun message_changes_after_the_rotation_interval() =
        assertNotEquals(WaitingMessages.at(all, 7, 0), WaitingMessages.at(all, 7, WaitingMessages.ROTATION_MS))

    @Test fun sequence_wraps_around() =
        assertEquals(WaitingMessages.at(all, 3, 0), WaitingMessages.at(all, 3, all.size * WaitingMessages.ROTATION_MS))

    @Test fun negative_seed_and_elapsed_are_safe() {
        assertTrue(WaitingMessages.at(all, -12345, -5) in all)
    }
}
