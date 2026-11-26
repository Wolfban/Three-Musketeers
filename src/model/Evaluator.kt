package model


enum class HandCategory {
    HIGH_CARD, ONE_PAIR, TWO_PAIR, THREE_OF_A_KIND, STRAIGHT,
    FLUSH, FULL_HOUSE, FOUR_OF_A_KIND, STRAIGHT_FLUSH, ROYAL_FLUSH,

    FIVE_OF_A_KIND,
    FLUSH_HOUSE,
    FLUSH_FIVE
}

data class HandResult(
    val category: HandCategory,
    val tiebreakers: List<Int>
) : Comparable<HandResult> {
    override fun compareTo(other: HandResult): Int {
        val byCategory = category.compareTo(other.category)
        if (byCategory != 0) return byCategory
        for (i in tiebreakers.indices) {
            val diff = tiebreakers[i].compareTo(other.tiebreakers[i])
            if (diff != 0) return diff
        }
        return 0
    }
}

object PokerEvaluator {

    fun evaluate(cards: List<Card>): HandResult {

        require(cards.size == 5) { "Se necesitan exactamente 5 cartas" }

        val values = cards.map { it.rank.value }.sortedDescending()
        val isFlush = cards.map { it.suit }.toSet().size == 1
        val straightHigh = straightHigh(values)


        val groups = values.groupingBy { it }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenByDescending { it.key })
        val counts = groups.map { it.value }
        val ordered = groups.map { it.key }

        return when {
            counts == listOf(5) && isFlush -> HandResult(HandCategory.FLUSH_FIVE, ordered)
            counts == listOf(5) -> HandResult(HandCategory.FIVE_OF_A_KIND, ordered)
            counts == listOf(3, 2) && isFlush -> HandResult(HandCategory.FLUSH_HOUSE, ordered)
            isFlush && straightHigh == 14 -> HandResult(HandCategory.ROYAL_FLUSH, listOf(14))
            isFlush && straightHigh != null -> HandResult(HandCategory.STRAIGHT_FLUSH, listOf(straightHigh))
            counts == listOf(4, 1) -> HandResult(HandCategory.FOUR_OF_A_KIND, ordered)
            counts == listOf(3, 2) -> HandResult(HandCategory.FULL_HOUSE, ordered)
            isFlush -> HandResult(HandCategory.FLUSH, values)
            straightHigh != null -> HandResult(HandCategory.STRAIGHT, listOf(straightHigh))
            counts == listOf(3, 1, 1) -> HandResult(HandCategory.THREE_OF_A_KIND, ordered)
            counts == listOf(2, 2, 1) -> HandResult(HandCategory.TWO_PAIR, ordered)
            counts == listOf(2, 1, 1, 1) -> HandResult(HandCategory.ONE_PAIR, ordered)
            else -> HandResult(HandCategory.HIGH_CARD, values)
        }
    }
    fun evaluateBest(cards: List<Card>): HandResult {
        require(cards.size >= 5) { "Se necesitan al menos 5 cartas" }
        return Combinations.of(cards, 5).map { evaluate(it) }.max()
    }

    private fun straightHigh(sortedDesc: List<Int>): Int? {
        if (sortedDesc.toSet().size != 5) return null
        if (sortedDesc[0] - sortedDesc[4] == 4) return sortedDesc[0]
        if (sortedDesc == listOf(14, 5, 4, 3, 2)) return 5
        return null
    }
}
