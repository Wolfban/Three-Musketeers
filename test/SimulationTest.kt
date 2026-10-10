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

// ---------- jugador automático (hace lo mismo que harían los botones) ----------

// Si ya tiene escalera o mejor, se planta. Si no, descarta (hasta 3) las cartas
// sueltas más bajas, conservando pares, tríos, etc.
private fun chooseDiscards(hand: List<Card>): Set<Int> {
    if (PokerEvaluator.evaluate(hand).category >= HandCategory.STRAIGHT) return emptySet()
    val counts = hand.groupingBy { it.rank }.eachCount()
    return hand.indices
        .filter { counts.getValue(hand[it].rank) == 1 }
        .sortedBy { hand[it].rank.value }
        .take(Duel.MAX_DISCARD_PER_ROUND)
        .toSet()
}

private fun autoPlay(duel: Duel, log: Boolean = false): DuelOutcome {
    if (log) {
        println("Rival:   ${duel.enemyHand.show()}  -> ${duel.enemyResult().category}")
        println("Jugador: ${duel.playerHand.show()}  -> ${duel.playerResult().category}")
    }
    while (duel.roundsLeft > 0) {
        val toDiscard = chooseDiscards(duel.playerHand)
        if (toDiscard.isEmpty()) break
        val dropped = toDiscard.map { duel.playerHand[it] }
        toDiscard.forEach { duel.toggleSelect(it) }
        duel.discardSelected()
        if (log) {
            println("Descarta: ${dropped.show()}")
            println("Jugador:  ${duel.playerHand.show()}  -> ${duel.playerResult().category}")
        }
    }
    val outcome = duel.fight()
    if (log) println("Resultado: $outcome  (rondas usadas: ${duel.roundsUsed})\n")
    return outcome
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
        val total = 2000
        val results = mutableMapOf(DuelOutcome.WIN to 0, DuelOutcome.LOSE to 0, DuelOutcome.DRAW to 0)
        val enemyCategories = mutableMapOf<HandCategory, Int>()

        repeat(total) { seed ->
            val duel = Duel(Deck(Random(seed)))
            val outcome = autoPlay(duel)
            results[outcome] = results.getValue(outcome) + 1
            enemyCategories[duel.enemyResult().category] =
                (enemyCategories[duel.enemyResult().category] ?: 0) + 1

            // Reglas que nunca deben romperse
            val allCards = duel.playerHand + duel.enemyHand + duel.discardPile
            assertEquals(allCards.size, allCards.toSet().size, "Carta repetida en la semilla $seed")
            assertTrue(duel.discardPile.size <= 9)
            assertTrue(duel.roundsUsed <= Duel.MAX_ROUNDS)
            assertTrue(duel.isFinished)
        }

        println("Resultados de $total partidas:")
        results.forEach { (k, v) -> println("  $k: $v (${v * 100 / total}%)") }
        println("Manos del rival:")
        enemyCategories.entries.sortedBy { it.key }.forEach { println("  ${it.key}: ${it.value}") }

        assertEquals(total, results.values.sum())
    }
}
