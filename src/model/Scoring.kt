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
        HandCategory.STRAIGHT_FLUSH to Rule(100, 8)
    )

    fun chipsOf(card: Card): Int = when (card.rank) {
        Rank.ACE -> 11
        Rank.JACK, Rank.QUEEN, Rank.KING, Rank.TEN -> 10
        else -> card.rank.value
    }

    fun score(cards: List<Card>): ScoreBreakdown {
        val category = PokerEvaluator.evaluate(cards).category
        val scoring = scoringCards(cards, category)
        val rule = rules.getValue(category)
        return ScoreBreakdown(
            category = category,
            baseChips = rule.baseChips,
            scoringCards = scoring,
            cardChips = scoring.sumOf { chipsOf(it) },
            multiplier = rule.multiplier
        )
    }

    // Solo puntúan las cartas que forman la combinación
    private fun scoringCards(cards: List<Card>, category: HandCategory): List<Card> {
        val groups = cards.groupBy { it.rank }.values
        return when (category) {
            HandCategory.HIGH_CARD -> listOf(cards.maxBy { it.rank.value })
            HandCategory.ONE_PAIR, HandCategory.TWO_PAIR -> groups.filter { it.size == 2 }.flatten()
            HandCategory.THREE_OF_A_KIND -> groups.filter { it.size == 3 }.flatten()
            HandCategory.FOUR_OF_A_KIND -> groups.filter { it.size == 4 }.flatten()
            else -> cards // escalera, flush, full house y escalera de color usan las 5
        }
    }
}
