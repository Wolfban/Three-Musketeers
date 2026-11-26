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


    val enemyHand: MutableList<Card> = dealEnemyHand().toMutableList()

    val enemyScore: ScoreBreakdown
        get() = Scoring.score(enemyHand)
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

    var visionUsed = false
        private set

    var suitChangeUsed = false
        private set

    var aceChangeUsed = false
        private set

    var recoverDiscardUsed = false
        private set

    fun recoverLastDiscard(index: Int): Boolean {
        if (isFinished || recoverDiscardUsed) return false
        if (discardPile.isEmpty()) return false
        if (index !in playerHand.indices) return false

        // Obtener la última carta descartada (puede repetir una carta de la mano: los comodines no tienen límites)
        val recoveredCard = discardPile.last()

        // Guardar la carta que vamos a reemplazar
        val replacedCard = playerHand[index]

        // Recuperar la última carta descartada
        discardPile.removeAt(discardPile.lastIndex)

        // Intercambiar las cartas
        playerHand[index] = recoveredCard
        discardPile.add(replacedCard)

        // Limpiar selecciones anteriores
        _selected.clear()

        // Marcar el comodín como utilizado
        recoverDiscardUsed = true

        return true
    }

    var kingsAsAcesActive = false
        private set

    fun activateKingsAsAces(): Boolean {
        if (isFinished || kingsAsAcesActive) return false

        kingsAsAcesActive = true
        return true
    }

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

    fun peekNextCard(): Card? {
        if (isFinished || deck.remaining == 0) return null
        return deck.peek()
    }

    fun useVision(): Card? {
        if (visionUsed || isFinished) return null

        val nextCard = peekNextCard() ?: return null

        visionUsed = true
        return nextCard
    }

    fun changeSuit(index: Int, newSuit: Suit): Boolean {
        if (isFinished || suitChangeUsed) return false
        if (index !in playerHand.indices) return false

        val originalCard = playerHand[index]

        // No permitir cambiar al mismo palo
        if (originalCard.suit == newSuit) return false

        // Crear la carta con el nuevo palo (puede quedar repetida en la mano: está permitido)
        val newCard = originalCard.copy(suit = newSuit)

        // Reemplazar la carta original
        playerHand[index] = newCard

        // Consumir el comodín
        suitChangeUsed = true

        return true
    }

    fun convertToAce(index: Int): Boolean {
        if (isFinished || aceChangeUsed) return false
        if (index !in playerHand.indices) return false

        val originalCard = playerHand[index]

        if (originalCard.rank == Rank.ACE) return false


        val newCard = originalCard.copy(rank = Rank.ACE)

        playerHand[index] = newCard

        aceChangeUsed = true

        return true
    }

    fun playerScore(): ScoreBreakdown = Scoring.score(
        playerHand,
        debuff,
        kingsAsAces = kingsAsAcesActive
    )

    var stealSwapUsed = false
        private set

    fun stealAndSwap(playerIndex: Int): Boolean {

        if (isFinished || stealSwapUsed) return false
        if (playerIndex !in playerHand.indices) return false

        // Carta que el jugador entregará
        val offeredCard = playerHand[playerIndex]

        // Cualquier carta del rival vale, aunque quede repetida en alguna mano
        if (enemyHand.isEmpty()) return false

        // Elegir aleatoriamente una carta del rival
        val enemyIndex = enemyHand.indices.random()
        val stolenCard = enemyHand[enemyIndex]

        playerHand[playerIndex] = stolenCard
        enemyHand[enemyIndex] = offeredCard


        _selected.clear()


        stealSwapUsed = true

        return true
    }
    fun playerResult() = PokerEvaluator.evaluateBest(playerHand)
    fun enemyResult() = PokerEvaluator.evaluateBest(enemyHand)
}
