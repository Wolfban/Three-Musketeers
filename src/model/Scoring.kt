package model

data class ScoreBreakdown(
    val category: HandCategory,
    val baseChips: Int,
    val scoringCards: List<Card>,
    val cardChips: Int,
    val multiplier: Int
) {
    val total: Int get() = (baseChips + cardChips) * multiplier


    fun describe(): String = "$category: ($baseChips + $cardChips) × $multiplier = $total"
}

object Scoring {

    private data class Rule(val baseChips: Int, val multiplier: Int)

    // Todos los valores de balance viven aquí
    private val rules = mapOf(
        HandCategory.HIGH_CARD to Rule(5, 1),
        HandCategory.ONE_PAIR to Rule(10, 2),
        HandCategory.TWO_PAIR to Rule(20, 2),
        HandCategory.THREE_OF_A_KIND to Rule(30, 3),
        HandCategory.STRAIGHT to Rule(30, 4),
        HandCategory.FLUSH to Rule(35, 4),
        HandCategory.FULL_HOUSE to Rule(40, 4),
        HandCategory.FOUR_OF_A_KIND to Rule(50, 5),
        HandCategory.STRAIGHT_FLUSH to Rule(100, 8),
        HandCategory.ROYAL_FLUSH to Rule (150, 10)
    )

    fun baseChipsOf(category: HandCategory): Int = rules.getValue(category).baseChips
    fun multiplierOf(category: HandCategory): Int = rules.getValue(category).multiplier

    fun chipsOf(card: Card): Int = when (card.rank) {
        Rank.ACE -> 11
        Rank.JACK, Rank.QUEEN, Rank.KING, Rank.TEN -> 10
        else -> card.rank.value
    }

    fun score(
        cards: List<Card>,
        debuff: Debuff = Debuff.NONE,
        kingsAsAces: Boolean = false
    ): ScoreBreakdown {

        require(cards.size >= 5)

        return Combinations.of(cards, 5)
            .flatMap { hand ->

                if (kingsAsAces) {
                    scoreFiveWithKingsAsAces(hand, debuff)
                } else {
                    listOf(scoreFive(hand, debuff))
                }

            }
            .maxWith(
                compareBy<ScoreBreakdown> { it.total }
                    .thenBy { it.category }
            )
    }


    private fun scoreFiveWithKingsAsAces(
        original: List<Card>,
        debuff: Debuff
    ): List<ScoreBreakdown> {

        val kingIndexes = original.indices.filter {
            original[it].rank == Rank.KING
        }

        return (0 until (1 shl kingIndexes.size)).mapNotNull { mask ->

            val virtualHand = original.toMutableList()

            kingIndexes.forEachIndexed { bit, index ->
                if ((mask and (1 shl bit)) != 0) {
                    virtualHand[index] = virtualHand[index].copy(rank = Rank.ACE)
                }
            }

            // Evitar cartas idénticas en la combinación evaluada
            if (virtualHand.toSet().size != virtualHand.size) {
                return@mapNotNull null
            }

            val virtualScore = scoreFive(virtualHand, Debuff.NONE)

            // Conservar las cartas originales para mostrarlas en pantalla
            val originalScoringCards = virtualScore.scoringCards.map { card ->
                original[virtualHand.indexOf(card)]
            }

            // Aplicar las penalizaciones del jefe a las cartas originales
            val actualChips = virtualScore.scoringCards
                .zip(originalScoringCards)
                .sumOf { (evaluatedCard, originalCard) ->
                    if (debuff.affects(originalCard)) 0
                    else chipsOf(evaluatedCard)
                }

            virtualScore.copy(
                scoringCards = originalScoringCards,
                cardChips = actualChips
            )
        }
    }

    private fun scoreFive(cards: List<Card>, debuff: Debuff): ScoreBreakdown {
        val category = PokerEvaluator.evaluate(cards).category
        val scoring = scoringCards(cards, category)
        val rule = rules.getValue(category)
        val chips = scoring.filter { !debuff.affects(it) }.sumOf { chipsOf(it) }
        return ScoreBreakdown(category, rule.baseChips, scoring, chips, rule.multiplier)
    }


    private fun scoringCards(cards: List<Card>, category: HandCategory): List<Card> {
        val groups = cards.groupBy { it.rank }.values
        return when (category) {
            HandCategory.HIGH_CARD -> listOf(cards.maxBy { it.rank.value })
            HandCategory.ONE_PAIR, HandCategory.TWO_PAIR -> groups.filter { it.size == 2 }.flatten()
            HandCategory.THREE_OF_A_KIND -> groups.filter { it.size == 3 }.flatten()
            HandCategory.FOUR_OF_A_KIND -> groups.filter { it.size == 4 }.flatten()
            else -> cards
        }
    }
}
