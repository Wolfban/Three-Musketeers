package tests

import model.*
import kotlin.random.Random
import kotlin.test.*

class SaveDataTest {

    @Test fun encodeDecodeRoundTrip() {
        val saved = SavedRun(2, 3, -12345L)
        assertEquals(saved, SavedRun.decode(saved.encode()))
    }

    @Test fun invalidDataIsIgnored() {
        listOf(null, "", "   ", "abc", "1;2", "1;2;3;4", "0;1;5", "1;0;5", "1;4;5", "1;x;5", "1;2;y").forEach {
            assertNull(SavedRun.decode(it), "Debería ignorar: $it")
        }
    }

    @Test fun runResumesAtSavedCheckpoint() {
        val run = Run(2, 3) { Deck(Random(7L)) }
        assertEquals(2, run.cycle)
        assertEquals(3, run.phase)
        assertTrue(run.encounter.isBoss)
        assertEquals(Difficulty.BOSS_ROUNDS, run.duel.maxRounds)
    }

    @Test fun sameSeedDealsTheSameDuel() {
        val a = Run(1, 2) { Deck(Random(99L)) }
        val b = Run(1, 2) { Deck(Random(99L)) }
        assertEquals(a.duel.playerHand, b.duel.playerHand)
        assertEquals(a.duel.enemyHand, b.duel.enemyHand)
    }

    @Test fun restartGoesBackToTheBeginning() {
        val run = Run(3, 2) { Deck(Random(1L)) }
        run.restart()
        assertEquals(1, run.cycle)
        assertEquals(1, run.phase)
    }
    @Test fun runStatsCountEncountersWon() {
        assertEquals(0, RunStats.encountersWon(1, 1, false))   // perdió el primer duelo
        assertEquals(1, RunStats.encountersWon(1, 1, true))
        assertEquals(3, RunStats.encountersWon(1, 3, true))    // venció al primer jefe
        assertEquals(5, RunStats.encountersWon(2, 3, false))   // cayó contra el segundo jefe
    }

    @Test fun runStatsDescribeHowFarTheRunGot() {
        assertEquals(2, RunStats.cycleReached(5))
        assertEquals(3, RunStats.phaseReached(5))
        assertEquals(1, RunStats.bossesBeaten(5))
    }

    @Test fun bestRunDecodeIgnoresBadData() {
        assertEquals(0, RunStats.decode(null))
        assertEquals(0, RunStats.decode("abc"))
        assertEquals(7, RunStats.decode("7"))
    }
    @Test fun playerNameIsCleaned() {
        assertEquals("ATHOS", PlayerName.clean("  ATHOS  "))
        assertEquals(PlayerName.MAX_LENGTH, PlayerName.clean("A".repeat(40))!!.length)
        assertNull(PlayerName.clean("   "))
        assertNull(PlayerName.clean(null))
    }
}
