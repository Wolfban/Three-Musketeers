package model

enum class DuelOutcome { WIN, LOSE, DRAW }

class Duel(
    private val deck: Deck = Deck(),
    private val enemyMinCategory: HandCategory = HandCategory.ONE_PAIR
) {

    companion object {
        const val HAND_SIZE = 5
        const val MAX_DISCARD_PER_ROUND = 3
        const val MAX_ROUNDS = 3
    }

    // El rival se reparte primero y no cambia durante el duelo
    val enemyHand: List<Card> = dealEnemyHand()
    val playerHand: MutableList<Card> = deck.draw(HAND_SIZE).toMutableList()
    val discardPile: MutableList<Card> = mutableListOf()

    private val _selected = mutableSetOf<Int>()
    val selected: Set<Int> get() = _selected

    var roundsUsed = 0
        private set

    var isFinished = false
        private set

    val roundsLeft: Int get() = MAX_ROUNDS - roundsUsed


    val canDiscard: Boolean get() = !isFinished && roundsLeft > 0 && _selected.isNotEmpty()

    // Re-baraja y reparte hasta que el rival tenga al menos la categoría mínima.
    // Las cartas rechazadas se devuelven porque el mazo se reinicia completo cada intento.
    private fun dealEnemyHand(): List<Card> {
        while (true) {
            deck.reset()
            val hand = deck.draw(HAND_SIZE)
            if (PokerEvaluator.evaluate(hand).category >= enemyMinCategory) return hand
        }
    }

    /** Marca o desmarca una carta. Devuelve false si no se pudo (duelo cerrado o ya hay 3 marcadas). */
    fun toggleSelect(index: Int): Boolean {
        require(index in playerHand.indices) { "Índice de carta inválido" }
        if (isFinished) return false
        if (index in _selected) { _selected.remove(index); return true }
        if (_selected.size >= MAX_DISCARD_PER_ROUND) return false
        _selected.add(index)
        return true
    }


    fun discardSelected() = playerDiscard(_selected.toSet())

    fun playerDiscard(indices: Set<Int>) {
        require(!isFinished) { "El duelo ya terminó" }
        require(roundsLeft > 0) { "No quedan rondas de descarte" }
        require(indices.isNotEmpty() && indices.size <= MAX_DISCARD_PER_ROUND) {
            "Se deben descartar entre 1 y $MAX_DISCARD_PER_ROUND cartas"
        }
        require(indices.all { it in playerHand.indices }) { "Índice de carta inválido" }

        for (i in indices.sortedDescending()) discardPile.add(playerHand.removeAt(i))
        playerHand.addAll(deck.draw(indices.size))
        _selected.clear()
        roundsUsed++
    }


    fun fight(): DuelOutcome {
        require(!isFinished) { "El duelo ya terminó" }
        isFinished = true
        _selected.clear()
        val player = playerScore().total
        val enemy = enemyScore.total
        return if (player > enemy) DuelOutcome.WIN else DuelOutcome.LOSE



        }


    fun playerResult() = PokerEvaluator.evaluate(playerHand)
    fun enemyResult() = PokerEvaluator.evaluate(enemyHand)
    val enemyScore: ScoreBreakdown = Scoring.score(enemyHand)
    fun playerScore(): ScoreBreakdown = Scoring.score(playerHand)
    val targetScore: Int get() = enemyScore.total
}
