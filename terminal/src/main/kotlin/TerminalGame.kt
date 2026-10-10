import model.*

private fun categoryName(c: HandCategory) = when (c) {
    HandCategory.HIGH_CARD -> "Carta alta"
    HandCategory.ONE_PAIR -> "Par"
    HandCategory.TWO_PAIR -> "Doble par"
    HandCategory.THREE_OF_A_KIND -> "Trío"
    HandCategory.STRAIGHT -> "Escalera"
    HandCategory.FLUSH -> "Color (flush)"
    HandCategory.FULL_HOUSE -> "Full house"
    HandCategory.FOUR_OF_A_KIND -> "Poker (four of a kind)"
    HandCategory.STRAIGHT_FLUSH -> "Escalera de color"
}

private fun cardText(card: Card): String {
    val rank = when (card.rank) {
        Rank.JACK -> "J"
        Rank.QUEEN -> "Q"
        Rank.KING -> "K"
        Rank.ACE -> "A"
        else -> card.rank.value.toString()
    }
    val suit = when (card.suit) {
        Suit.CLUBS -> "C"
        Suit.DIAMONDS -> "D"
        Suit.HEARTS -> "H"
        Suit.SPADES -> "S"
    }
    return rank + suit
}

// Las cartas que puntúan se marcan con *
private fun handText(cards: List<Card>, score: ScoreBreakdown, numbered: Boolean): String =
    cards.mapIndexed { i, card ->
        val mark = if (card in score.scoringCards) "*" else " "
        val label = (cardText(card) + mark).padEnd(4)
        if (numbered) "[${i + 1}] $label" else label
    }.joinToString("  ")

private fun describe(s: ScoreBreakdown) =
    "${categoryName(s.category)}: (${s.baseChips} + ${s.cardChips}) x ${s.multiplier} = ${s.total}"

private fun showFight(duel: Duel) {
    val outcome = duel.fight()
    val player = duel.playerScore()
    val enemy = duel.enemyScore
    println()
    println("========== DUELO ==========")
    println("RIVAL: ${handText(duel.enemyHand, enemy, false)}")
    println("       ${describe(enemy)}")
    println("TU:    ${handText(duel.playerHand, player, false)}")
    println("       ${describe(player)}")
    println()
    when (outcome) {
        DuelOutcome.WIN -> println("¡GANASTE! ${player.total} supera a ${enemy.total}.")
        DuelOutcome.LOSE -> println("PERDISTE. ${player.total} no supera a ${enemy.total}.")
        DuelOutcome.DRAW -> println("EMPATE: ${player.total} contra ${enemy.total}.")
    }
    if (outcome == DuelOutcome.WIN && player.category < enemy.category) {
        println("Ojo: tu mano es de categoría menor, pero tus cartas altas sumaron mas fichas.")
    }
    if (outcome == DuelOutcome.LOSE && player.category > enemy.category) {
        println("Ojo: tu mano es de categoria mayor, pero las cartas del rival sumaron mas fichas.")
    }
}

private fun tryDiscard(duel: Duel, input: String) {
    val numbers = input.split(" ", ",").filter { it.isNotBlank() }.map { it.toIntOrNull() }
    if (numbers.isEmpty() || numbers.any { it == null }) {
        println("No entendi. Escribe números de carta separados por espacio, ej: 1 3 5")
        return
    }
    val indices = numbers.filterNotNull().map { it - 1 }
    if (indices.any { it !in duel.playerHand.indices }) {
        println("Los numeros deben estar entre 1 y ${duel.playerHand.size}.")
        return
    }
    if (indices.toSet().size != indices.size) {
        println("Repetiste una carta.")
        return
    }
    if (indices.size > Duel.MAX_DISCARD_PER_ROUND) {
        println("Maximo ${Duel.MAX_DISCARD_PER_ROUND} cartas por descarte.")
        return
    }
    val dropped = indices.map { duel.playerHand[it] }
    indices.forEach { duel.toggleSelect(it) }
    duel.discardSelected()
    println("Descartaste: ${dropped.joinToString(" ") { cardText(it) }}")
}

// Devuelve true si el duelo terminó, false si el jugador salió
private fun playDuel(duel: Duel): Boolean {
    println()
    println("RIVAL:   ${handText(duel.enemyHand, duel.enemyScore, false)}")
    println("         ${describe(duel.enemyScore)}")
    println("(* = carta que suma puntos)")
    println(">>> Debes superar ${duel.targetScore} puntos <<<")

    while (!duel.isFinished) {
        val score = duel.playerScore()
        println()
        println("TU MANO: ${handText(duel.playerHand, score, true)}")
        println("         ${describe(score)}   [objetivo: ${duel.targetScore}]")
        println("Descartes restantes: ${duel.roundsLeft}")
        if (duel.roundsLeft > 0) {
            print("Cartas a descartar (1 a 3 números, ej. 1 3 5), 'd' = duelo, 'q' = salir: ")
        } else {
            print("Sin descartes. 'd' = duelo, 'q' = salir: ")
        }
        val input = readlnOrNull()?.trim()?.lowercase() ?: return false
        when {
            input == "q" -> return false
            input == "d" -> showFight(duel)
            duel.roundsLeft == 0 -> println("Ya no quedan descartes. Escribe 'd' para el duelo.")
            else -> tryDiscard(duel, input)
        }
    }
    return true
}

fun main() {
    println("=== THE THREE MUSKETEERS: duelo de poker ===")
    while (true) {
        if (!playDuel(Duel())) break
        print("\nOtra partida? (s/n): ")
        if (readlnOrNull()?.trim()?.lowercase() != "s") break
    }
    println("Uno para todos y todos para uno!")
}
