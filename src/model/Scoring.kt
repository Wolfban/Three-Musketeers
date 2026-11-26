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
        HandCategory.ROYAL_FLUSH to Rule (150, 10),
        HandCategory.FIVE_OF_A_KIND to Rule(160, 12),
        HandCategory.FLUSH_HOUSE to Rule(175, 14),
        HandCategory.FLUSH_FIVE to Rule(200, 16)
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
                    listOf(scoreFive(hand, hand, debuff))
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

        // Prueba cada rey como rey y como as. Puede quedar un as repetido: está permitido.
        return (0 until (1 shl kingIndexes.size)).map { mask ->

            val virtualHand = original.toMutableList()

            kingIndexes.forEachIndexed { bit, index ->
                if ((mask and (1 shl bit)) != 0) {
                    virtualHand[index] = virtualHand[index].copy(rank = Rank.ACE)
                }
            }

            // Se evalúa la mano virtual, pero se muestran y se penalizan las cartas originales
            scoreFive(virtualHand, original, debuff)
        }
    }

    /**
     * evaluated: las cartas tal como cuentan (con reyes convertidos en ases, si aplica).
     * original: las cartas reales en las mismas posiciones; son las que se muestran y las que penaliza el jefe.
     */
    private fun scoreFive(evaluated: List<Card>, original: List<Card>, debuff: Debuff): ScoreBreakdown {
        val category = PokerEvaluator.evaluate(evaluated).category
        val positions = scoringPositions(evaluated, category)
        val rule = rules.getValue(category)
        val chips = positions.filter { !debuff.affects(original[it]) }.sumOf { chipsOf(evaluated[it]) }
        return ScoreBreakdown(category, rule.baseChips, positions.map { original[it] }, chips, rule.multiplier)
    }

    // Posiciones de las cartas que forman la jugada. Se usan posiciones porque puede haber cartas repetidas.
    private fun scoringPositions(cards: List<Card>, category: HandCategory): List<Int> {
        val counts = cards.groupingBy { it.rank }.eachCount()
        fun withCount(n: Int) = cards.indices.filter { counts.getValue(cards[it].rank) == n }
        return when (category) {
            HandCategory.HIGH_CARD -> listOf(cards.indices.maxBy { cards[it].rank.value })
            HandCategory.ONE_PAIR, HandCategory.TWO_PAIR -> withCount(2)
            HandCategory.THREE_OF_A_KIND -> withCount(3)
            HandCategory.FOUR_OF_A_KIND -> withCount(4)
            else -> cards.indices.toList()
        }
    }
}
