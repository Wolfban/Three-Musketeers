package tests

import model.*
import kotlin.test.*

class ScoringTest {

    private fun hand(vararg s: Pair<Rank, Suit>) = s.map { Card(it.first, it.second) }

    private val fourTwos = hand(Rank.TWO to Suit.HEARTS, Rank.TWO to Suit.CLUBS, Rank.TWO to Suit.SPADES,
        Rank.TWO to Suit.DIAMONDS, Rank.NINE to Suit.HEARTS)
    private val aceHighFlush = hand(Rank.ACE to Suit.HEARTS, Rank.KING to Suit.HEARTS, Rank.QUEEN to Suit.HEARTS,
        Rank.JACK to Suit.HEARTS, Rank.NINE to Suit.HEARTS)
    private val lowFlush = hand(Rank.TWO to Suit.CLUBS, Rank.THREE to Suit.CLUBS, Rank.FOUR to Suit.CLUBS,
        Rank.FIVE to Suit.CLUBS, Rank.SEVEN to Suit.CLUBS)
    private val fourAces = hand(Rank.ACE to Suit.HEARTS, Rank.ACE to Suit.CLUBS, Rank.ACE to Suit.SPADES,
        Rank.ACE to Suit.DIAMONDS, Rank.TWO to Suit.HEARTS)

    @Test fun knownScores() {
        assertEquals(290, Scoring.score(fourTwos).total)
        assertEquals(340, Scoring.score(aceHighFlush).total)
        assertEquals(224, Scoring.score(lowFlush).total)
        assertEquals(470, Scoring.score(fourAces).total)
    }

    @Test fun highFlushCanBeatLowFourOfAKind() {
        assertTrue(Scoring.score(aceHighFlush).total > Scoring.score(fourTwos).total)
    }

    @Test fun lowFlushLosesToLowFourOfAKind() {
        assertTrue(Scoring.score(lowFlush).total < Scoring.score(fourTwos).total)
    }

    @Test fun highFourOfAKindBeatsAnyFlush() {
        assertTrue(Scoring.score(fourAces).total > Scoring.score(aceHighFlush).total)
    }

    @Test fun onlyCombinationCardsScore() {
        val pair = hand(Rank.KING to Suit.HEARTS, Rank.KING to Suit.CLUBS, Rank.NINE to Suit.SPADES,
            Rank.SEVEN to Suit.HEARTS, Rank.THREE to Suit.DIAMONDS)
        val s = Scoring.score(pair)
        assertEquals(2, s.scoringCards.size)
        assertEquals(60, s.total)   // (10 + 20) × 2
    }
}
