package model


data class SavedRun(
    val cycle: Int,
    val phase: Int,
    val seed: Long,
    val bossSeed: Long = 0L
) {

    fun encode(): String = "$cycle;$phase;$seed;$bossSeed"

    companion object {

        fun decode(text: String?): SavedRun? {
            if (text.isNullOrBlank()) return null
            val parts = text.split(";")
            if (parts.size != 3 && parts.size != 4) return null
            val cycle = parts[0].toIntOrNull() ?: return null
            val phase = parts[1].toIntOrNull() ?: return null
            val seed = parts[2].toLongOrNull() ?: return null
            val bossSeed = if (parts.size == 4) parts[3].toLongOrNull() ?: return null else 0L
            if (cycle < 1 || phase !in 1..Difficulty.PHASES_PER_CYCLE) return null
            return SavedRun(cycle, phase, seed, bossSeed)
        }
    }
}


object RunStats {

    fun encountersWon(cycle: Int, phase: Int, wonCurrent: Boolean): Int =
        (cycle - 1) * Difficulty.PHASES_PER_CYCLE + (phase - 1) + if (wonCurrent) 1 else 0


    fun cycleReached(wins: Int): Int = wins / Difficulty.PHASES_PER_CYCLE + 1
    fun phaseReached(wins: Int): Int = wins % Difficulty.PHASES_PER_CYCLE + 1
    fun bossesBeaten(wins: Int): Int = wins / Difficulty.PHASES_PER_CYCLE

    fun decode(text: String?): Int = (text?.trim()?.toIntOrNull() ?: 0).coerceAtLeast(0)
}

object PlayerName {
    const val MAX_LENGTH = 12


    fun clean(raw: String?): String? {
        val name = raw?.trim()?.take(MAX_LENGTH)?.trim()
        return if (name.isNullOrEmpty()) null else name
    }
}
