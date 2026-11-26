package tests

import model.*
import kotlin.random.Random
import kotlin.test.*

class BossRuleTest {

    private fun hand(vararg s: Pair<Rank, Suit>) = s.map { Card(it.first, it.second) }

    private val pairOfKings = hand(Rank.KING to Suit.HEARTS, Rank.KING to Suit.DIAMONDS, Rank.NINE to Suit.SPADES,
        Rank.SEVEN to Suit.HEARTS, Rank.THREE to Suit.CLUBS)

    // ---------- rotación de jefes ----------

    @Test fun npcsHaveNoBossRule() {
        assertNull(Difficulty.encounterFor(1, 1).bossRule)
        assertNull(Difficulty.encounterFor(2, 2).bossRule)
        assertEquals(Debuff.NONE, Difficulty.encounterFor(1, 1).debuff)
    }

    @Test fun sixDifferentBossesInARow() {
        val rules = (1..6).map { Difficulty.encounterFor(it, 3).bossRule }
        assertEquals(6, rules.filterNotNull().toSet().size)
        assertEquals(BossRule.FEWER_DISCARDS, rules.first())
    }

    @Test fun rotationStartsOverAfterTheSixthBoss() {
        for (cycle in 1..6) {
            assertEquals(Difficulty.encounterFor(cycle, 3).bossRule, Difficulty.encounterFor(cycle + 6, 3).bossRule)
        }
    }

    @Test fun fourBossesDebuffOneSuitEach() {
        val suits = Difficulty.bossCycle.mapNotNull { it.debuff.suit }
        assertEquals(4, suits.size)
        assertEquals(Suit.entries.toSet(), suits.toSet())
    }

    @Test fun onlyTheFewerDiscardsBossRemovesADiscard() {
        for (cycle in 1..6) {
            val boss = Difficulty.encounterFor(cycle, 3)
            val expected = if (boss.bossRule == BossRule.FEWER_DISCARDS) Difficulty.BOSS_ROUNDS else Duel.MAX_ROUNDS
            assertEquals(expected, boss.playerRounds)
        }
        assertEquals(Debuff.NONE, BossRule.FEWER_DISCARDS.debuff)
    }

    // ---------- qué cartas anula cada regla ----------

    @Test fun suitRulesOnlyAffectTheirSuit() {
        val rules = mapOf(
            BossRule.NO_DIAMONDS to Suit.DIAMONDS, BossRule.NO_HEARTS to Suit.HEARTS,
            BossRule.NO_CLUBS to Suit.CLUBS, BossRule.NO_SPADES to Suit.SPADES
        )
        for ((rule, suit) in rules) {
            for (other in Suit.entries) {
                assertEquals(other == suit, rule.debuff.affects(Card(Rank.ACE, other)), "$rule con $other")
            }
        }
    }

    @Test fun faceRuleAffectsOnlyJackQueenKing() {
        val debuff = BossRule.NO_FACES.debuff
        for (rank in Rank.entries) {
            val isFace = rank == Rank.JACK || rank == Rank.QUEEN || rank == Rank.KING
            assertEquals(isFace, debuff.affects(Card(rank, Suit.SPADES)), "valor $rank")
        }
    }

    // ---------- puntaje con cartas anuladas ----------

    @Test fun debuffedCardsFormTheHandButAddNoChips() {
        assertEquals(60, Scoring.score(pairOfKings).total)                                  // (10 + 20) x 2
        val noDiamonds = Scoring.score(pairOfKings, BossRule.NO_DIAMONDS.debuff)
        assertEquals(HandCategory.ONE_PAIR, noDiamonds.category)
        assertEquals(40, noDiamonds.total)                                                  // el rey de diamantes no suma
        assertEquals(40, Scoring.score(pairOfKings, BossRule.NO_HEARTS.debuff).total)       // el rey de corazones no suma
        assertEquals(60, Scoring.score(pairOfKings, BossRule.NO_CLUBS.debuff).total)        // ningún rey es de tréboles
        assertEquals(20, Scoring.score(pairOfKings, BossRule.NO_FACES.debuff).total)        // ningún rey suma
    }

    @Test fun diamondFlushOnlyScoresItsBaseChips() {
        val flush = hand(Rank.ACE to Suit.DIAMONDS, Rank.KING to Suit.DIAMONDS, Rank.QUEEN to Suit.DIAMONDS,
            Rank.JACK to Suit.DIAMONDS, Rank.NINE to Suit.DIAMONDS)
        assertEquals(340, Scoring.score(flush).total)                                   // (35 + 50) x 4
        assertEquals(140, Scoring.score(flush, BossRule.NO_DIAMONDS.debuff).total)      // (35 + 0) x 4
        assertEquals(220, Scoring.score(flush, BossRule.NO_FACES.debuff).total)         // (35 + 11 + 9) x 4
    }

    @Test fun faceRuleLowersAHighStraight() {
        val straight = hand(Rank.TEN to Suit.HEARTS, Rank.JACK to Suit.CLUBS, Rank.QUEEN to Suit.HEARTS,
            Rank.KING to Suit.SPADES, Rank.ACE to Suit.DIAMONDS)
        assertEquals(324, Scoring.score(straight).total)                                // (30 + 51) x 4
        assertEquals(204, Scoring.score(straight, BossRule.NO_FACES.debuff).total)      // (30 + 10 + 11) x 4
    }

    // ---------- en el duelo y en la run ----------

    @Test fun ruleAffectsThePlayerButNotTheBoss() {
        val debuff = BossRule.NO_FACES.debuff
        val duel = Duel(Deck(Random(3L)), HandCategory.ONE_PAIR, Duel.MAX_ROUNDS, debuff)
        assertEquals(Scoring.score(duel.playerHand, debuff).total, duel.playerScore().total)
        assertEquals(Scoring.score(duel.enemyHand).total, duel.enemyScore.total)
    }

    @Test fun runAppliesTheRuleOfEachBoss() {
        val first = Run(1, 3) { Deck(Random(5L)) }
        assertEquals(Difficulty.BOSS_ROUNDS, first.duel.maxRounds)
        assertEquals(Debuff.NONE, first.duel.debuff)

        val second = Run(2, 3) { Deck(Random(5L)) }
        assertEquals(Duel.MAX_ROUNDS, second.duel.maxRounds)
        assertEquals(BossRule.NO_DIAMONDS.debuff, second.duel.debuff)

        val sixth = Run(6, 3) { Deck(Random(5L)) }
        assertEquals(Duel.MAX_ROUNDS, sixth.duel.maxRounds)
        assertTrue(sixth.duel.debuff.affects(Card(Rank.KING, Suit.CLUBS)))
    }
}
