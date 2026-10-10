package tests

import model.*
import kotlin.random.Random
import kotlin.test.*

class DuelTest {

    @Test fun handsNeverShareCards() {
        repeat(100) { seed ->
            val duel = Duel(Deck(Random(seed)))
            assertEquals(10, (duel.playerHand + duel.enemyHand).toSet().size)
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

    @Test fun cannotSelectMoreThanThree() {
        val duel = Duel()
        assertTrue(duel.toggleSelect(0)); assertTrue(duel.toggleSelect(1)); assertTrue(duel.toggleSelect(2))
        assertFalse(duel.toggleSelect(3))
    }

    @Test fun discardNeedsSelection() {
        val duel = Duel()
        assertFalse(duel.canDiscard)
        assertFailsWith<IllegalArgumentException> { duel.discardSelected() }
    }

    @Test fun cannotDiscardMoreThanThreeCards() {
        val duel = Duel()
        assertFailsWith<IllegalArgumentException> { duel.playerDiscard(setOf(0, 1, 2, 3)) }
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
}
