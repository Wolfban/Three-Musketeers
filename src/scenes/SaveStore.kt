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

/** Termina la run guardada: borra la partida y los comodines que tenía. */
internal fun Views.clearSavedRun() {
    storage[SAVED_RUN_KEY] = ""
    storage[INVENTORY_KEY] = ""
}

// ---------- comodines ----------

internal const val INVENTORY_KEY = "jokerInventory"
internal const val CONFIRM_JOKERS_KEY = "confirmJokers"

internal fun Views.loadInventory(): JokerInventory = JokerInventory.decode(storage.getOrNull(INVENTORY_KEY))

internal fun Views.saveInventory(inventory: JokerInventory) {
    storage[INVENTORY_KEY] = inventory.encode()
}

/** true (por defecto): pedir confirmación antes de usar un comodín. */
internal fun Views.loadConfirmJokers(): Boolean = storage.getOrNull(CONFIRM_JOKERS_KEY) != "0"

internal fun Views.saveConfirmJokers(confirm: Boolean) {
    storage[CONFIRM_JOKERS_KEY] = if (confirm) "1" else "0"
}

// ---------- colección ----------

internal const val UNLOCKED_JOKERS_KEY = "unlockedJokers"

internal fun Views.loadUnlockedJokers(): Set<JokerType> = JokerUnlocks.decode(storage.getOrNull(UNLOCKED_JOKERS_KEY))

/** Desbloquea un comodín en la colección. Devuelve true si es la primera vez. */
internal fun Views.unlockJoker(type: JokerType): Boolean {
    val unlocked = loadUnlockedJokers()
    if (type in unlocked) return false
    storage[UNLOCKED_JOKERS_KEY] = JokerUnlocks.encode(unlocked + type)
    return true
}

// ---------- estadísticas ----------

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

/** Borra el récord, las runs ganadas y la colección (todos los comodines vuelven a estar ocultos). El nombre se mantiene. */
internal fun Views.clearStats() {
    storage[BEST_RUN_KEY] = ""
    storage[RUNS_WON_KEY] = ""
    storage[UNLOCKED_JOKERS_KEY] = ""
}

internal fun Views.loadPlayerName(): String? = PlayerName.clean(storage.getOrNull(PLAYER_NAME_KEY))

internal fun Views.savePlayerName(name: String) {
    storage[PLAYER_NAME_KEY] = name
}
