package model

import kotlin.random.Random

enum class RunEvent {
    NEXT_PHASE,      // ganó a un NPC: sigue la siguiente fase
    CYCLE_CLEARED,   // venció a un jefe: sigue el siguiente ciclo
    RUN_WON,         // venció al último jefe: victoria (puede seguir en modo sin fin)
    RUN_OVER         // perdió o empató: la run termina
}


enum class BossRule(val debuff: Debuff = Debuff.NONE, val fewerDiscards: Boolean = false) {
    FEWER_DISCARDS(fewerDiscards = true),                                   // un descarte menos
    NO_DIAMONDS(Debuff(suit = Suit.DIAMONDS)),                              // los diamantes no suman fichas
    NO_HEARTS(Debuff(suit = Suit.HEARTS)),                                  // los corazones no suman fichas
    NO_CLUBS(Debuff(suit = Suit.CLUBS)),                                    // los tréboles no suman fichas
    NO_SPADES(Debuff(suit = Suit.SPADES)),                                  // las picas no suman fichas
    NO_FACES(Debuff(ranks = setOf(Rank.JACK, Rank.QUEEN, Rank.KING)))       // K, Q y J no suman fichas
}


data class EnemyTier(val min: HandCategory, val max: HandCategory)


data class Encounter(
    val cycle: Int,
    val phase: Int,
    val name: String,
    val isBoss: Boolean,
    val enemyMinCategory: HandCategory,
    val playerRounds: Int,
    val bossRule: BossRule? = null,
    val enemyMaxCategory: HandCategory = HandCategory.ROYAL_FLUSH,
    val targetBonusPercent: Int = 0
) {

    val debuff: Debuff
        get() = bossRule?.debuff ?: Debuff.NONE
}

//Toda la configuración de dificultad vive aquí.
object Difficulty {
    const val PHASES_PER_CYCLE = 3
    const val BOSS_ROUNDS = 2


    const val CYCLES_TO_WIN = 6


    const val ENDLESS_BONUS_PERCENT = 15

    private val tiers = listOf(
        EnemyTier(HandCategory.ONE_PAIR, HandCategory.ONE_PAIR),                    // 0
        EnemyTier(HandCategory.ONE_PAIR, HandCategory.TWO_PAIR),                    // 1
        EnemyTier(HandCategory.TWO_PAIR, HandCategory.TWO_PAIR),                    // 2
        EnemyTier(HandCategory.TWO_PAIR, HandCategory.THREE_OF_A_KIND),             // 3
        EnemyTier(HandCategory.THREE_OF_A_KIND, HandCategory.THREE_OF_A_KIND),      // 4
        EnemyTier(HandCategory.THREE_OF_A_KIND, HandCategory.STRAIGHT),             // 5
        EnemyTier(HandCategory.STRAIGHT, HandCategory.STRAIGHT),                    // 6
        EnemyTier(HandCategory.STRAIGHT, HandCategory.FLUSH),                       // 7
        EnemyTier(HandCategory.FLUSH, HandCategory.FULL_HOUSE),                     // 8
        EnemyTier(HandCategory.FULL_HOUSE, HandCategory.ROYAL_FLUSH)                // 9
    )


    private val curve = listOf(
        listOf(0, 0, 1),    // ciclo 1
        listOf(0, 1, 1),    // ciclo 2
        listOf(1, 1, 2),    // ciclo 3
        listOf(1, 2, 3),    // ciclo 4
        listOf(2, 3, 4),    // ciclo 5
        listOf(3, 4, 5)     // ciclo 6
    )

    private val npcNames = listOf(
        "Guardia del Cardenal", "Jussac", "Bernajoux", "Cahusac", "Biscarat", "Espía de Milady"
    )
    private val bossNames = listOf("Rochefort", "Milady de Winter", "Cardenal Richelieu")


    val bossCycle = listOf(
        BossRule.FEWER_DISCARDS,
        BossRule.NO_DIAMONDS,
        BossRule.NO_HEARTS,
        BossRule.NO_CLUBS,
        BossRule.NO_SPADES,
        BossRule.NO_FACES
    )


    fun bossRuleFor(cycle: Int, bossSeed: Long? = null): BossRule {
        val index = (cycle - 1) % bossCycle.size
        if (bossSeed == null) return bossCycle[index]
        val lap = (cycle - 1) / bossCycle.size
        return bossCycle.shuffled(Random(bossSeed + lap))[index]
    }

    fun isEndless(cycle: Int): Boolean = cycle > CYCLES_TO_WIN


    fun tierFor(cycle: Int, phase: Int): EnemyTier {
        val step = if (cycle <= curve.size) {
            curve[cycle - 1][phase - 1]
        } else {
            curve.last()[phase - 1] + (cycle - curve.size)
        }
        return tiers[step.coerceAtMost(tiers.lastIndex)]
    }

    fun targetBonusFor(cycle: Int): Int =
        if (isEndless(cycle)) ENDLESS_BONUS_PERCENT * (cycle - CYCLES_TO_WIN) else 0

    fun encounterFor(cycle: Int, phase: Int, bossSeed: Long? = null): Encounter {
        require(cycle >= 1) { "El ciclo empieza en 1" }
        require(phase in 1..PHASES_PER_CYCLE) { "Fase inválida" }

        val isBoss = phase == PHASES_PER_CYCLE
        val tier = tierFor(cycle, phase)
        val name = if (isBoss) {
            bossNames[(cycle - 1).coerceAtMost(bossNames.lastIndex)]
        } else {
            npcNames[((cycle - 1) * 2 + (phase - 1)) % npcNames.size]
        }
        val bossRule = if (isBoss) bossRuleFor(cycle, bossSeed) else null
        return Encounter(
            cycle, phase, name, isBoss,
            enemyMinCategory = tier.min,
            playerRounds = if (bossRule?.fewerDiscards == true) BOSS_ROUNDS else Duel.MAX_ROUNDS,
            bossRule = bossRule,
            enemyMaxCategory = tier.max,
            targetBonusPercent = targetBonusFor(cycle)
        )
    }
}


class Run(
    startCycle: Int = 1,
    startPhase: Int = 1,
    bossSeed: Long? = null,
    private val deckFactory: () -> Deck = { Deck() }
) {

    var bossSeed: Long? = bossSeed
        private set
    var cycle = startCycle
        private set
    var phase = startPhase
        private set
    var isOver = false
        private set
    var encounter: Encounter = Difficulty.encounterFor(startCycle, startPhase, bossSeed)
        private set
    var duel: Duel = createDuel()
        private set


    val isEndless: Boolean
        get() = Difficulty.isEndless(cycle)

    private var pendingEvent: RunEvent? = null

    private fun createDuel() = Duel(
        deck = deckFactory(),
        enemyMinCategory = encounter.enemyMinCategory,
        maxRounds = encounter.playerRounds,
        debuff = encounter.debuff,
        enemyMaxCategory = encounter.enemyMaxCategory,
        targetBonusPercent = encounter.targetBonusPercent
    )


    fun report(outcome: DuelOutcome): RunEvent {
        require(duel.isFinished) { "El duelo todavía no termina" }
        require(pendingEvent == null) { "Este duelo ya fue reportado" }
        val event = when {
            outcome != DuelOutcome.WIN -> {
                isOver = true
                RunEvent.RUN_OVER
            }
            encounter.isBoss && cycle == Difficulty.CYCLES_TO_WIN -> RunEvent.RUN_WON
            encounter.isBoss -> RunEvent.CYCLE_CLEARED
            else -> RunEvent.NEXT_PHASE
        }
        pendingEvent = event
        return event
    }


    fun advance() {
        val event = pendingEvent
        require(event != null && event != RunEvent.RUN_OVER) { "No se puede avanzar" }
        if (event == RunEvent.NEXT_PHASE) {
            phase++
        } else {
            cycle++
            phase = 1
        }
        encounter = Difficulty.encounterFor(cycle, phase, bossSeed)
        duel = createDuel()
        pendingEvent = null
    }


    fun restart(newBossSeed: Long? = bossSeed) {
        bossSeed = newBossSeed
        cycle = 1
        phase = 1
        isOver = false
        pendingEvent = null
        encounter = Difficulty.encounterFor(1, 1, bossSeed)
        duel = createDuel()
    }
}
