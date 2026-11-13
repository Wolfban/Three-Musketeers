package scenes

import korlibs.korge.service.storage.*
import korlibs.korge.view.*
import model.*


internal const val SAVED_RUN_KEY = "savedRun"
internal const val PLAYER_NAME_KEY = "playerName"

internal fun Views.loadSavedRun(): SavedRun? = SavedRun.decode(storage.getOrNull(SAVED_RUN_KEY))

internal fun Views.saveRun(cycle: Int, phase: Int, seed: Long, bossSeed: Long) {
    storage[SAVED_RUN_KEY] = SavedRun(cycle, phase, seed, bossSeed).encode()
}

internal fun Views.clearSavedRun() {
    storage[SAVED_RUN_KEY] = ""
}

internal const val BEST_RUN_KEY = "bestRunWins"

internal fun Views.loadBestRun(): Int = RunStats.decode(storage.getOrNull(BEST_RUN_KEY))

internal fun Views.recordBestRun(wins: Int) {
    if (wins > loadBestRun()) storage[BEST_RUN_KEY] = wins.toString()
}

internal const val RUNS_WON_KEY = "runsWon"

internal fun Views.loadRunsWon(): Int = RunStats.decode(storage.getOrNull(RUNS_WON_KEY))

internal fun Views.recordRunWon() {
    storage[RUNS_WON_KEY] = (loadRunsWon() + 1).toString()
}

internal fun Views.loadPlayerName(): String? = PlayerName.clean(storage.getOrNull(PLAYER_NAME_KEY))

internal fun Views.savePlayerName(name: String) {
    storage[PLAYER_NAME_KEY] = name
}

