package tests

import model.*
import kotlin.random.Random
import kotlin.test.*

// ---------- utilidades para imprimir ----------

private fun Card.short(): String {
    val r = when (rank) {
        Rank.TEN -> "T"
        Rank.JACK -> "J"
        Rank.QUEEN -> "Q"
        Rank.KING -> "K"
        Rank.ACE -> "A"
        else -> rank.value.toString()
    }
    val s = when (suit) {
        Suit.CLUBS -> "C"
        Suit.DIAMONDS -> "D"
        Suit.HEARTS -> "H"
        Suit.SPADES -> "S"
    }
    return r + s
}

private fun List<Card>.show() = joinToString(" ") { it.short() }

// ---------- jugador automático ----------

// Descarta (hasta 3) las cartas sueltas más bajas, conservando pares, tríos, etc.
private fun chooseDiscards(hand: List<Card>): Set<Int> {
    val counts = hand.groupingBy { it.rank }.eachCount()
    return hand.indices
        .filter { counts.getValue(hand[it].rank) == 1 }
        .sortedBy { hand[it].rank.value }
        .take(Duel.MAX_DISCARD_PER_ROUND)
        .toSet()
}

// Mientras no supere el objetivo y le queden rondas, descarta. Luego pulsa "Duelo".
private fun autoPlay(duel: Duel, log: Boolean = false): DuelOutcome {
    if (log) {
        println("Rival:   ${duel.enemyHand.show()}  -> ${duel.enemyScore.describe()}")
        println("Jugador: ${duel.playerHand.show()}  -> ${duel.playerScore().describe()}  (objetivo: ${duel.targetScore})")
    }
    while (duel.roundsLeft > 0 && duel.playerScore().total <= duel.targetScore) {
        val toDiscard = chooseDiscards(duel.playerHand)
        if (toDiscard.isEmpty()) break
        val dropped = toDiscard.map { duel.playerHand[it] }
        toDiscard.forEach { duel.toggleSelect(it) }
        duel.discardSelected()
        if (log) {
            println("Descarta: ${dropped.show()}")
            println("Jugador:  ${duel.playerHand.show()}  -> ${duel.playerScore().describe()}")
        }
    }
    val outcome = duel.fight()
    if (log) println("Resultado: $outcome  (rondas usadas: ${duel.roundsUsed})\n")
    return outcome
}

// ---------- estadísticas ----------

private class Stats(val total: Int) {
    var wins = 0
    var losses = 0
    var draws = 0
    var rounds = 0
    var playerScoreSum = 0L
    var enemyScoreSum = 0L

    fun pct(n: Int) = n * 100 / total
    fun avg(sum: Long) = sum / total
    fun avgRounds() = (rounds * 100.0 / total).toInt() / 100.0

    fun print(label: String) {
        println(
            "$label | victorias ${pct(wins)}% | derrotas ${pct(losses)}% | empates ${pct(draws)}% | " +
                "puntaje jugador ${avg(playerScoreSum)} vs rival ${avg(enemyScoreSum)} | rondas prom. ${avgRounds()}"
        )
    }
}

private fun simulate(total: Int, enemyMin: HandCategory): Stats {
    val stats = Stats(total)
    repeat(total) { seed ->
        val duel = Duel(Deck(Random(seed)), enemyMin)
        when (autoPlay(duel)) {
            DuelOutcome.WIN -> stats.wins++
            DuelOutcome.LOSE -> stats.losses++
            DuelOutcome.DRAW -> stats.draws++
        }
        stats.rounds += duel.roundsUsed
        stats.playerScoreSum += duel.playerScore().total
        stats.enemyScoreSum += duel.enemyScore.total

        // Reglas que nunca deben romperse
        val allCards = duel.playerHand + duel.enemyHand + duel.discardPile
        assertEquals(allCards.size, allCards.toSet().size, "Carta repetida en la semilla $seed")
        assertTrue(duel.discardPile.size <= Duel.MAX_ROUNDS * Duel.MAX_DISCARD_PER_ROUND)
        assertTrue(duel.roundsUsed <= Duel.MAX_ROUNDS)
        assertTrue(duel.isFinished)
        assertTrue(duel.enemyResult().category >= enemyMin, "Rival por debajo del mínimo en la semilla $seed")
    }
    return stats
}

// ---------- tests ----------

class DuelSimulationTest {

    @Test fun narratedGames() {
        repeat(3) { seed ->
            println("=== Partida ${seed + 1} ===")
            autoPlay(Duel(Deck(Random(seed))), log = true)
        }
    }

    @Test fun manyGamesKeepRulesIntact() {
        val stats = simulate(2000, HandCategory.ONE_PAIR)
        stats.print("2000 partidas (rival mínimo ONE_PAIR)")
        assertEquals(2000, stats.wins + stats.losses + stats.draws)
    }

    // No tiene aserciones de balance: solo imprime para que el equipo decida
    @Test fun difficultyComparison() {
        println("Comparación de dificultad (1000 partidas cada una):")
        listOf(HandCategory.ONE_PAIR, HandCategory.TWO_PAIR, HandCategory.THREE_OF_A_KIND).forEach { min ->
            simulate(1000, min).print("Rival mínimo $min".padEnd(32))
        }
    }
}
