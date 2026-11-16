package it.intesys.audiototext.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WaitingMessagesTest {
    @Test fun same_moment_gives_same_message() =
        assertEquals(WaitingMessages.at(7, 3_000), WaitingMessages.at(7, 7_999))

    @Test fun message_changes_after_the_rotation_interval() =
        assertNotEquals(WaitingMessages.at(7, 0), WaitingMessages.at(7, WaitingMessages.ROTATION_MS))

    @Test fun sequence_wraps_around() {
        val n = WaitingMessages.ALL.size
        assertEquals(WaitingMessages.at(3, 0), WaitingMessages.at(3, n * WaitingMessages.ROTATION_MS))
    }

    @Test fun negative_seed_and_elapsed_are_safe() {
        assertTrue(WaitingMessages.at(-12345, -5) in WaitingMessages.ALL)
    }

    @Test fun messages_are_distinct() = assertEquals(WaitingMessages.ALL.size, WaitingMessages.ALL.toSet().size)
}
