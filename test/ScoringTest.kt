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
    @Test fun sevenCardsFindHiddenStraight() {
        val h = hand(Rank.QUEEN to Suit.HEARTS, Rank.KING to Suit.SPADES, Rank.JACK to Suit.CLUBS,
            Rank.THREE to Suit.DIAMONDS, Rank.QUEEN to Suit.CLUBS, Rank.ACE to Suit.DIAMONDS,
            Rank.TEN to Suit.HEARTS)
        val s = Scoring.score(h)
        assertEquals(HandCategory.STRAIGHT, s.category)
        assertEquals(324, s.total)
    }
    @Test fun royalFlushScoresAndBeatsStraightFlush() {
        val royal = hand(Rank.ACE to Suit.SPADES, Rank.KING to Suit.SPADES, Rank.QUEEN to Suit.SPADES,
            Rank.JACK to Suit.SPADES, Rank.TEN to Suit.SPADES)
        val sf = hand(Rank.KING to Suit.HEARTS, Rank.QUEEN to Suit.HEARTS, Rank.JACK to Suit.HEARTS,
            Rank.TEN to Suit.HEARTS, Rank.NINE to Suit.HEARTS)
        assertEquals(2010, Scoring.score(royal).total)
        assertEquals(1192, Scoring.score(sf).total)
    }

    @Test fun royalFlushFoundAmongSevenCards() {
        val h = hand(Rank.ACE to Suit.SPADES, Rank.KING to Suit.SPADES, Rank.QUEEN to Suit.SPADES,
            Rank.JACK to Suit.SPADES, Rank.TEN to Suit.SPADES, Rank.TWO to Suit.HEARTS, Rank.NINE to Suit.CLUBS)
        assertEquals(HandCategory.ROYAL_FLUSH, Scoring.score(h).category)
    }
    // ---------- cartas repetidas creadas por comodines ----------

    @Test fun fiveAcesScoreAsFiveOfAKind() {
        val h = hand(Rank.ACE to Suit.SPADES, Rank.ACE to Suit.HEARTS, Rank.ACE to Suit.DIAMONDS,
            Rank.ACE to Suit.CLUBS, Rank.ACE to Suit.SPADES, Rank.TWO to Suit.HEARTS, Rank.NINE to Suit.CLUBS)
        val s = Scoring.score(h)
        assertEquals(HandCategory.FIVE_OF_A_KIND, s.category)
        assertEquals((160 + 55) * 12, s.total)
        assertEquals(5, s.scoringCards.size)
    }

    @Test fun duplicatedCardsBothScore() {
        // Par de K de corazones repetido: las dos copias suman fichas
        val h = hand(Rank.KING to Suit.HEARTS, Rank.KING to Suit.HEARTS, Rank.NINE to Suit.SPADES,
            Rank.SEVEN to Suit.HEARTS, Rank.THREE to Suit.DIAMONDS)
        assertEquals((10 + 20) * 2, Scoring.score(h).total)
    }

    @Test fun kingsAsAcesCanMakeFiveOfAKind() {
        val h = hand(Rank.ACE to Suit.SPADES, Rank.ACE to Suit.HEARTS, Rank.ACE to Suit.DIAMONDS,
            Rank.ACE to Suit.CLUBS, Rank.KING to Suit.SPADES, Rank.TWO to Suit.HEARTS, Rank.NINE to Suit.CLUBS)
        val s = Scoring.score(h, kingsAsAces = true)
        assertEquals(HandCategory.FIVE_OF_A_KIND, s.category)
        // El rey se muestra como rey, pero cuenta 11 fichas como as
        assertTrue(Card(Rank.KING, Suit.SPADES) in s.scoringCards)
        assertEquals((160 + 55) * 12, s.total)
    }

    @Test fun debuffStillAppliesToDuplicatedCards() {
        val h = hand(Rank.ACE to Suit.SPADES, Rank.ACE to Suit.HEARTS, Rank.ACE to Suit.DIAMONDS,
            Rank.ACE to Suit.CLUBS, Rank.ACE to Suit.DIAMONDS)
        // Jefe de diamantes: los dos ases de diamantes no suman
        assertEquals((160 + 33) * 12, Scoring.score(h, Debuff(suit = Suit.DIAMONDS)).total)
    }

    @Test fun flushHouseAndFlushFiveScore() {
        val flushHouse = hand(Rank.ACE to Suit.HEARTS, Rank.ACE to Suit.HEARTS, Rank.ACE to Suit.HEARTS,
            Rank.KING to Suit.HEARTS, Rank.KING to Suit.HEARTS)
        assertEquals(HandCategory.FLUSH_HOUSE, Scoring.score(flushHouse).category)
        assertEquals((175 + 53) * 14, Scoring.score(flushHouse).total)

        val flushFive = List(5) { Card(Rank.ACE, Suit.SPADES) }
        assertEquals(HandCategory.FLUSH_FIVE, Scoring.score(flushFive).category)
        assertEquals((200 + 55) * 16, Scoring.score(flushFive).total)
    }

    @Test fun eachJokerHandAlwaysBeatsTheOneBelow() {
        // La peor mano de cada categoría le gana a la mejor de la anterior
        val bestRoyal = Scoring.score(hand(Rank.ACE to Suit.SPADES, Rank.KING to Suit.SPADES, Rank.QUEEN to Suit.SPADES,
            Rank.JACK to Suit.SPADES, Rank.TEN to Suit.SPADES)).total
        val worstFive = Scoring.score(hand(Rank.TWO to Suit.SPADES, Rank.TWO to Suit.HEARTS, Rank.TWO to Suit.DIAMONDS,
            Rank.TWO to Suit.CLUBS, Rank.TWO to Suit.SPADES)).total
        val bestFive = Scoring.score(hand(Rank.ACE to Suit.SPADES, Rank.ACE to Suit.HEARTS, Rank.ACE to Suit.DIAMONDS,
            Rank.ACE to Suit.CLUBS, Rank.ACE to Suit.SPADES)).total
        val worstFlushHouse = Scoring.score(hand(Rank.TWO to Suit.HEARTS, Rank.TWO to Suit.HEARTS, Rank.TWO to Suit.HEARTS,
            Rank.THREE to Suit.HEARTS, Rank.THREE to Suit.HEARTS)).total
        val bestFlushHouse = Scoring.score(hand(Rank.ACE to Suit.HEARTS, Rank.ACE to Suit.HEARTS, Rank.ACE to Suit.HEARTS,
            Rank.KING to Suit.HEARTS, Rank.KING to Suit.HEARTS)).total
        val worstFlushFive = Scoring.score(List(5) { Card(Rank.TWO, Suit.CLUBS) }).total
        assertTrue(worstFive > bestRoyal)
        assertTrue(worstFlushHouse > bestFive)
        assertTrue(worstFlushFive > bestFlushHouse)
    }
}
