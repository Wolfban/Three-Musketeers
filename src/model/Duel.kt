package model

enum class DuelOutcome { WIN, LOSE, DRAW }

class Duel(
    private val deck: Deck = Deck(),
    private val enemyMinCategory: HandCategory = HandCategory.ONE_PAIR,
    val maxRounds: Int = MAX_ROUNDS,
    val debuff: Debuff = Debuff.NONE,
    private val enemyMaxCategory: HandCategory = HandCategory.ROYAL_FLUSH,
    val targetBonusPercent: Int = 0
) {
    companion object {
        const val HAND_SIZE = 7
        const val MAX_DISCARD_PER_ROUND = 5
        const val MAX_ROUNDS = 3
    }

    init {
        require(enemyMinCategory <= enemyMaxCategory) { "El rango de mano del rival está al revés" }
        require(targetBonusPercent >= 0) { "El bono del objetivo no puede ser negativo" }
    }


    val enemyHand: List<Card> = dealEnemyHand()
    val enemyScore: ScoreBreakdown = Scoring.score(enemyHand)
    val targetScore: Int
        get() = enemyScore.total * (100 + targetBonusPercent) / 100

    val playerHand: MutableList<Card> = deck.draw(HAND_SIZE).toMutableList()
    val discardPile: MutableList<Card> = mutableListOf()

    private val _selected = mutableSetOf<Int>()
    val selected: Set<Int> get() = _selected

    var roundsUsed = 0
        private set
    var isFinished = false
        private set

    val roundsLeft: Int get() = maxRounds - roundsUsed

    val canDiscard: Boolean get() = !isFinished && roundsLeft > 0 && _selected.isNotEmpty()

    private fun dealEnemyHand(): List<Card> {
        while (true) {
            deck.reset()
            val hand = deck.draw(HAND_SIZE)
            val category = PokerEvaluator.evaluateBest(hand).category
            if (category >= enemyMinCategory && category <= enemyMaxCategory) return hand
        }
    }

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
        val target = targetScore
        return when {
            player > target -> DuelOutcome.WIN
            player < target -> DuelOutcome.LOSE
            else -> DuelOutcome.DRAW
        }
    }

    fun playerScore(): ScoreBreakdown = Scoring.score(playerHand, debuff)
    fun playerResult() = PokerEvaluator.evaluateBest(playerHand)
    fun enemyResult() = PokerEvaluator.evaluateBest(enemyHand)
}
