package it.intesys.audiototext.ui

/** Light-hearted lines (the `waiting_messages` string array) shown, one at a time, while a transcription is running. */
object WaitingMessages {
    const val ROTATION_MS = 8_000L

    /**
     * The message for a given moment. [seed] picks where the sequence starts (so each run opens with
     * a different line), [elapsedMs] advances it every [ROTATION_MS].
     */
    fun at(messages: List<String>, seed: Long, elapsedMs: Long): String {
        val step = (maxOf(elapsedMs, 0L) / ROTATION_MS)
        val start = Math.floorMod(seed, messages.size.toLong())
        return messages[((start + step) % messages.size).toInt()]
    }
}
