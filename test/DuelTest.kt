package tests

import model.*
import kotlin.random.Random
import kotlin.test.*

class DuelTest {

    @Test fun handsNeverShareCards() {
        repeat(100) { seed ->
            val duel = Duel(Deck(Random(seed)))
            assertEquals(Duel.HAND_SIZE * 2, (duel.playerHand + duel.enemyHand).toSet().size)
        }
    }

    @Test fun enemyAlwaysHasAtLeastAPair() {
        repeat(200) { seed ->
            val duel = Duel(Deck(Random(seed)))
            assertTrue(duel.enemyResult().category >= HandCategory.ONE_PAIR)
        }
    }

    @Test fun enemyHandDoesNotChangeAfterDiscards() {
        val duel = Duel(Deck(Random(5)))
        val before = duel.enemyHand.toList()
        duel.toggleSelect(0)
        duel.discardSelected()
        assertEquals(before, duel.enemyHand)
    }

    @Test fun discardedCardsNeverReturn() {
        val duel = Duel(Deck(Random(2)))
        repeat(3) { duel.playerDiscard(setOf(0, 1, 2)) }
        val seen = duel.playerHand + duel.enemyHand + duel.discardPile
        assertEquals(seen.size, seen.toSet().size)
        assertEquals(9, duel.discardPile.size)
    }

    @Test fun cannotSelectMoreThanTheMaximum() {
        val duel = Duel()
        repeat(Duel.MAX_DISCARD_PER_ROUND) { assertTrue(duel.toggleSelect(it)) }
        assertFalse(duel.toggleSelect(Duel.MAX_DISCARD_PER_ROUND))
    }

    @Test fun discardNeedsSelection() {
        val duel = Duel()
        assertFalse(duel.canDiscard)
        assertFailsWith<IllegalArgumentException> { duel.discardSelected() }
    }

    @Test fun cannotDiscardMoreThanTheMaximum() {
        val duel = Duel()
        assertFailsWith<IllegalArgumentException> { duel.playerDiscard((0..Duel.MAX_DISCARD_PER_ROUND).toSet()) }
    }

    @Test fun onlyThreeRounds() {
        val duel = Duel()
        repeat(3) { duel.playerDiscard(setOf(0)) }
        assertFalse(duel.canDiscard)
        assertFailsWith<IllegalArgumentException> { duel.playerDiscard(setOf(0)) }
    }

    @Test fun fightWorksWithoutDiscarding() {
        val duel = Duel(Deck(Random(7)))
        duel.fight()
        assertTrue(duel.isFinished)
    }

    @Test fun nothingAllowedAfterFight() {
        val duel = Duel()
        duel.toggleSelect(0)
        duel.fight()
        assertFalse(duel.toggleSelect(1))
        assertFalse(duel.canDiscard)
        assertFailsWith<IllegalArgumentException> { duel.playerDiscard(setOf(0)) }
        assertFailsWith<IllegalArgumentException> { duel.fight() }
    }
    // ---------- comodines sin límites de cartas repetidas ----------

    @Test fun convertToAceCanRepeatAnAce() {
        val duel = Duel(Deck(Random(9L)))
        val target = duel.playerHand.indexOfFirst { it.rank != Rank.ACE }
        val existingAce = Card(Rank.ACE, duel.playerHand[target].suit)
        duel.playerHand[(target + 1) % duel.playerHand.size] = existingAce   // ya hay un as de ese palo
        assertTrue(duel.convertToAce(target))
        assertEquals(2, duel.playerHand.count { it == existingAce })
    }

    @Test fun changeSuitCanRepeatACard() {
        val duel = Duel(Deck(Random(10L)))
        val card = duel.playerHand[0]
        val otherSuit = Suit.entries.first { it != card.suit }
        duel.playerHand[1] = card.copy(suit = otherSuit)          // la carta destino ya está en la mano
        assertTrue(duel.changeSuit(0, otherSuit))
        assertEquals(duel.playerHand[0], duel.playerHand[1])
    }

    @Test fun stealAndSwapAlwaysWorks() {
        val duel = Duel(Deck(Random(11L)))
        duel.playerHand[0] = duel.enemyHand[0]                    // aunque quede repetida
        assertTrue(duel.stealAndSwap(0))
    }
}
