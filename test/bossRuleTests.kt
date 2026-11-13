package tests

import model.*
import kotlin.random.Random
import kotlin.test.*

class BossRuleTest {

    private fun hand(vararg s: Pair<Rank, Suit>) = s.map { Card(it.first, it.second) }

    @Test fun npcsHaveNoBossRule() {
        assertNull(Difficulty.encounterFor(1, 1).bossRule)
        assertNull(Difficulty.encounterFor(2, 2).bossRule)
        assertNull(Difficulty.encounterFor(1, 1).debuff)
    }

    @Test fun bossRulesAlternateByCycle() {
        assertEquals(BossRule.FEWER_DISCARDS, Difficulty.encounterFor(1, 3).bossRule)
        assertEquals(BossRule.NO_DIAMONDS, Difficulty.encounterFor(2, 3).bossRule)
        assertEquals(BossRule.FEWER_DISCARDS, Difficulty.encounterFor(3, 3).bossRule)
        assertEquals(BossRule.NO_DIAMONDS, Difficulty.encounterFor(4, 3).bossRule)
    }

    @Test fun fewerDiscardsBossOnlyRemovesADiscard() {
        val boss = Difficulty.encounterFor(1, 3)
        assertEquals(Difficulty.BOSS_ROUNDS, boss.playerRounds)
        assertNull(boss.debuff)
    }

}
