package tests

import model.*
import kotlin.test.*

class PokerEvaluatorTest {

    private fun hand(vararg s: Pair<Rank, Suit>) = s.map { Card(it.first, it.second) }

    @Test fun detectsPair() {
        val h = hand(Rank.KING to Suit.HEARTS, Rank.KING to Suit.CLUBS, Rank.NINE to Suit.SPADES,
            Rank.SEVEN to Suit.HEARTS, Rank.THREE to Suit.DIAMONDS)
        assertEquals(HandCategory.ONE_PAIR, PokerEvaluator.evaluate(h).category)
    }

    @Test fun wheelStraightIsFiveHigh() {
        val h = hand(Rank.ACE to Suit.HEARTS, Rank.TWO to Suit.CLUBS, Rank.THREE to Suit.SPADES,
            Rank.FOUR to Suit.HEARTS, Rank.FIVE to Suit.DIAMONDS)
        val r = PokerEvaluator.evaluate(h)
        assertEquals(HandCategory.STRAIGHT, r.category)
        assertEquals(listOf(5), r.tiebreakers)
    }

    @Test fun fullHouseBeatsFlush() {
        val full = hand(Rank.TEN to Suit.HEARTS, Rank.TEN to Suit.CLUBS, Rank.TEN to Suit.SPADES,
            Rank.TWO to Suit.HEARTS, Rank.TWO to Suit.DIAMONDS)
        val flush = hand(Rank.ACE to Suit.HEARTS, Rank.NINE to Suit.HEARTS, Rank.SEVEN to Suit.HEARTS,
            Rank.FOUR to Suit.HEARTS, Rank.TWO to Suit.HEARTS)
        assertTrue(PokerEvaluator.evaluate(full) > PokerEvaluator.evaluate(flush))
    }

    @Test fun kickerBreaksTie() {
        val a = hand(Rank.KING to Suit.HEARTS, Rank.KING to Suit.CLUBS, Rank.ACE to Suit.SPADES,
            Rank.SEVEN to Suit.HEARTS, Rank.THREE to Suit.DIAMONDS)
        val b = hand(Rank.KING to Suit.SPADES, Rank.KING to Suit.DIAMONDS, Rank.QUEEN to Suit.SPADES,
            Rank.SEVEN to Suit.CLUBS, Rank.THREE to Suit.HEARTS)
        assertTrue(PokerEvaluator.evaluate(a) > PokerEvaluator.evaluate(b))
    }

    @Test fun rejectsDuplicateCards() {
        val h = hand(Rank.KING to Suit.HEARTS, Rank.KING to Suit.HEARTS, Rank.NINE to Suit.SPADES,
            Rank.SEVEN to Suit.HEARTS, Rank.THREE to Suit.DIAMONDS)
        assertFailsWith<IllegalArgumentException> { PokerEvaluator.evaluate(h) }
    }
}
