import model.*
import kotlin.random.Random
import kotlin.test.*

class RandomBossTest {

    private fun order(seed: Long, cycles: IntRange = 1..6) = cycles.map { Difficulty.bossRuleFor(it, seed) }

    @Test fun sameSeedGivesTheSameBosses() {
        assertEquals(order(42L), order(42L))
        assertEquals(order(42L, 7..12), order(42L, 7..12))
    }

    @Test fun everyRunSeesTheSixBossesOnceBeforeRepeating() {
        for (seed in 0L until 50L) {
            assertEquals(Difficulty.bossCycle.toSet(), order(seed).toSet(), "semilla $seed")
            assertEquals(Difficulty.bossCycle.toSet(), order(seed, 7..12).toSet(), "semilla $seed, modo sin fin")
        }
    }

    @Test fun differentRunsGetDifferentOrders() {
        val orders = (0L until 30L).map { order(it) }.toSet()
        assertTrue(orders.size > 10, "Solo salieron ${orders.size} órdenes distintos en 30 runs")
    }

    @Test fun anyBossCanComeFirst() {
        val firsts = (0L until 200L).map { Difficulty.bossRuleFor(1, it) }.toSet()
        assertEquals(Difficulty.bossCycle.toSet(), firsts)
    }

    @Test fun withoutSeedTheOrderIsTheFixedList() {
        assertEquals(Difficulty.bossCycle, (1..6).map { Difficulty.bossRuleFor(it) })
    }

    @Test fun runUsesItsSeedForEveryBoss() {
        val seed = 77L
        for (cycle in 1..8) {
            val run = Run(cycle, 3, seed) { Deck(Random(1L)) }
            assertEquals(Difficulty.bossRuleFor(cycle, seed), run.encounter.bossRule)
        }
    }

    @Test fun aResumedRunKeepsTheSameBosses() {
        val saved = SavedRun(3, 3, 5L, 123456789L)
        val restored = SavedRun.decode(saved.encode())!!
        val original = Run(saved.cycle, saved.phase, saved.bossSeed) { Deck(Random(saved.seed)) }
        val resumed = Run(restored.cycle, restored.phase, restored.bossSeed) { Deck(Random(restored.seed)) }
        assertEquals(original.encounter.bossRule, resumed.encounter.bossRule)
        assertEquals(original.duel.playerHand, resumed.duel.playerHand)
    }

    @Test fun restartingWithANewSeedChangesTheRun() {
        val run = Run(4, 2, 1L) { Deck(Random(1L)) }
        run.restart(2L)
        assertEquals(2L, run.bossSeed)
        assertEquals(1, run.cycle)
        assertEquals(1, run.phase)
    }

    @Test fun oldSavesWithoutBossSeedStillLoad() {
        val old = SavedRun.decode("2;1;999")
        assertEquals(SavedRun(2, 1, 999L, 0L), old)
    }
}
