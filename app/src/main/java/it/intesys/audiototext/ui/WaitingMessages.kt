package it.intesys.audiototext.ui

/** Light-hearted lines shown, one at a time, while a transcription is running. */
object WaitingMessages {
    const val ROTATION_MS = 8_000L

    val ALL: List<String> = listOf(
        "Whisper sta sussurrando alla RAM…",
        "Il modello sta ascoltando… due volte, per sicurezza.",
        "Contando le virgole una a una…",
        "Mettendo gli accenti dove servono.",
        "Decifrando anche i «cioè» e gli «eeeh».",
        "Nessun professore è stato disturbato.",
        "Rubando il lavoro al dattilografo, con gentilezza.",
        "Tutto in locale: l'audio non lascia il telefono.",
        "Un attimo, sto prendendo appunti anch'io.",
        "Se leggi questo, la trascrizione sta andando avanti.",
        "Trasformando le onde sonore in parole.",
        "Caffè? Ci vuole ancora un po'.",
    )

    /**
     * The message for a given moment. [seed] picks where the sequence starts (so each run opens with
     * a different line), [elapsedMs] advances it every [ROTATION_MS].
     */
    fun at(seed: Long, elapsedMs: Long): String {
        val step = (maxOf(elapsedMs, 0L) / ROTATION_MS)
        val start = Math.floorMod(seed, ALL.size.toLong())
        return ALL[((start + step) % ALL.size).toInt()]
    }
}
