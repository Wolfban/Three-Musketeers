package tests

import model.*
import kotlin.random.Random
import kotlin.test.*

class RunVictoryTest {

    private fun runAt(cycle: Int, phase: Int) = Run(cycle, phase) { Deck(Random(11L)) }

    private fun finish(run: Run, outcome: DuelOutcome): RunEvent {
        run.duel.fight()
        return run.report(outcome)
    }

    // ---------- victoria y modo sin fin ----------

    @Test fun beatingTheSixthBossWinsTheRun() {
        val run = runAt(Difficulty.CYCLES_TO_WIN, 3)
        assertEquals(RunEvent.RUN_WON, finish(run, DuelOutcome.WIN))
        assertFalse(run.isOver)
        assertFalse(run.isEndless)
    }

    @Test fun earlierBossesOnlyClearTheCycle() {
        for (cycle in 1 until Difficulty.CYCLES_TO_WIN) {
            assertEquals(RunEvent.CYCLE_CLEARED, finish(runAt(cycle, 3), DuelOutcome.WIN))
        }
    }

    @Test fun losingToTheFinalBossEndsTheRun() {
        val run = runAt(Difficulty.CYCLES_TO_WIN, 3)
        assertEquals(RunEvent.RUN_OVER, finish(run, DuelOutcome.LOSE))
        assertTrue(run.isOver)
    }

    @Test fun afterTheVictoryTheRunCanContinueEndlessly() {
        val run = runAt(Difficulty.CYCLES_TO_WIN, 3)
        finish(run, DuelOutcome.WIN)
        run.advance()
        assertEquals(Difficulty.CYCLES_TO_WIN + 1, run.cycle)
        assertEquals(1, run.phase)
        assertTrue(run.isEndless)
        // En el modo sin fin los jefes ya no dan otra victoria: solo pasan de ciclo
        assertEquals(RunEvent.CYCLE_CLEARED, finish(runAt(Difficulty.CYCLES_TO_WIN + 1, 3), DuelOutcome.WIN))
    }

    @Test fun endlessBonusGrowsEveryCycle() {
        assertEquals(0, Difficulty.encounterFor(Difficulty.CYCLES_TO_WIN, 3).targetBonusPercent)
        assertEquals(Difficulty.ENDLESS_BONUS_PERCENT, Difficulty.encounterFor(Difficulty.CYCLES_TO_WIN + 1, 1).targetBonusPercent)
        assertEquals(2 * Difficulty.ENDLESS_BONUS_PERCENT, Difficulty.encounterFor(Difficulty.CYCLES_TO_WIN + 2, 2).targetBonusPercent)
    }

    // ---------- curva de dificultad ----------

    @Test fun firstRivalsHaveExactlyAPair() {
        val first = Difficulty.encounterFor(1, 1)
        assertEquals(HandCategory.ONE_PAIR, first.enemyMinCategory)
        assertEquals(HandCategory.ONE_PAIR, first.enemyMaxCategory)
    }

    @Test fun difficultyNeverGoesDownFromOneCycleToTheNext() {
        for (phase in 1..Difficulty.PHASES_PER_CYCLE) {
            for (cycle in 1..11) {
                val now = Difficulty.encounterFor(cycle, phase)
                val next = Difficulty.encounterFor(cycle + 1, phase)
                assertTrue(next.enemyMinCategory >= now.enemyMinCategory, "mínimo en ciclo ${cycle + 1}, fase $phase")
                assertTrue(next.enemyMaxCategory >= now.enemyMaxCategory, "máximo en ciclo ${cycle + 1}, fase $phase")
            }
        }
    }

    @Test fun theBossIsNeverEasierThanTheNpcsOfItsCycle() {
        for (cycle in 1..10) {
            val npc = Difficulty.encounterFor(cycle, 2)
            val boss = Difficulty.encounterFor(cycle, 3)
            assertTrue(boss.enemyMinCategory >= npc.enemyMinCategory)
            assertTrue(boss.enemyMaxCategory >= npc.enemyMaxCategory)
        }
    }

    @Test fun enemyHandStaysInsideItsRange() {
        for (cycle in 1..8) for (phase in 1..Difficulty.PHASES_PER_CYCLE) {
            val enc = Difficulty.encounterFor(cycle, phase)
            repeat(8) { seed ->
                val duel = Run(cycle, phase) { Deck(Random(seed.toLong())) }.duel
                val category = duel.enemyResult().category
                assertTrue(
                    category >= enc.enemyMinCategory && category <= enc.enemyMaxCategory,
                    "ciclo $cycle, fase $phase, semilla $seed: $category"
                )
            }
        }
    }

    // ---------- objetivo con bono ----------

    @Test fun targetScoreIncludesTheBonus() {
        val plain = Duel(Deck(Random(2L)))
        assertEquals(plain.enemyScore.total, plain.targetScore)

        val boosted = Duel(Deck(Random(2L)), targetBonusPercent = 50)
        assertEquals(boosted.enemyScore.total * 150 / 100, boosted.targetScore)
    }

    @Test fun fightComparesAgainstTheTargetNotTheRawEnemyScore() {
        // Con un bono enorme el objetivo queda fuera de alcance, sin importar las cartas
        val duel = Duel(Deck(Random(4L)), targetBonusPercent = 100_000)
        assertEquals(DuelOutcome.LOSE, duel.fight())
    }
}
