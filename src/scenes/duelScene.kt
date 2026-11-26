package scenes

import korlibs.event.*
import korlibs.image.bitmap.*
import korlibs.image.color.*
import korlibs.image.font.*
import korlibs.image.format.*
import korlibs.io.file.std.*
import korlibs.korge.input.*
import korlibs.korge.scene.*
import korlibs.korge.tween.*
import korlibs.korge.view.*
import korlibs.korge.service.storage.*
import korlibs.korge.view.animation.*
import korlibs.math.geom.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import model.*
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

private const val CARD_W = 63
private const val CARD_H = 90
private const val CARD_STEP = 69

internal const val ART_SCALE = 3.0

private const val SIDE_X = 27

private val SIDE_BUTTONS = mapOf("Descartar" to 88, "Duelo" to 114, "Nueva Partida" to 140)
private const val BUTTON_ART_X = 8
private const val BUTTON_ART_W = 43
private const val BUTTON_ART_H = 24
private const val BUTTON_CAP = 6
private const val SIDE_BUTTON_X = BUTTON_ART_X * 3
private const val DEATH_BUTTON_ART_W = 66
private const val PANEL_CAP = 13
private const val RESULT_PANEL_ART_W = 160
private const val RESULT_PANEL_ART_H = 140
private const val MENU_BUTTON_ART_W = 136
private const val CONFIRM_PANEL_ART_W = 160
private const val CONFIRM_PANEL_ART_H = 80

private const val TABLE_X = 59 * 3
private const val TABLE_W = 261 * 3
private const val CARDS_X = TABLE_X + (TABLE_W - (6 * CARD_STEP + CARD_W)) / 2
private const val ENEMY_Y = 240

private const val ENEMY_CARD_W = 50
private const val ENEMY_CARD_H = 72
private const val ENEMY_STEP = 55
private const val ENEMY_X = TABLE_X + (TABLE_W - (6 * ENEMY_STEP + ENEMY_CARD_W)) / 2
private const val PLAYER_Y = 400

// ---------- menú del duelo ----------
private const val GAME_MENU_PANEL_ART_W = 90        // 270 px
private const val GAME_MENU_PANEL_ART_H = 135       // 405 px
private const val DECKS_PANEL_ART_W = 160           // 480 px

// ---------- comodines ----------
private const val INVENTORY_BUTTON_X = 770          // botón "Comodines" (arriba a la derecha)
private const val INVENTORY_BUTTON_Y = 12
private const val INVENTORY_PANEL_X = 180
private const val INVENTORY_PANEL_Y = 40
private const val INVENTORY_PANEL_ART_W = 200      // 600 px de ancho
private const val INVENTORY_PANEL_ART_H = 142      // 426 px de alto
private const val JOKER_SLOT_W = 180
private const val JOKER_SLOT_H = 170
private const val JOKER_SLOT_GAP = 30
private const val JOKER_SLOT_Y = 112
private const val JOKER_HINT_Y = 352               // "Cambiapalos: toca una carta · Cancelar"
private const val JOKER_HOLD_SECONDS = 0.45         // mantener presionado para ver la descripción
private const val INVENTORY_HINT = "Toca para usar - mantén para ver qué hace"
private const val JOKER_CONFIRM_ART_W = 200         // cuadro "¿Usar ...?"

private val RIVALS = listOf("Mosquetero 1", "Cobrador de impuestos", "Princesa incognita")

private val BOSS_RIVALS = listOf("Princesa incognita")

private class Scenery(val file: String, val offsetY: Int = 0)

private val SCENERY = listOf(
    Scenery("escena_taberna.ase"),
    Scenery("escena_muelle.ase"),
    Scenery("escena_louvre.ase", offsetY = -3)
)

private fun sceneryFor(cycle: Int) = SCENERY[(cycle - 1).mod(SCENERY.size)]
private val NPC_RIVALS = RIVALS.filter { it !in BOSS_RIVALS }

private const val BOSS_FOCUS_W = 640
private const val BOSS_FOCUS_H = 400
private const val BOSS_FOCUS_RX = 58.0
private const val BOSS_FOCUS_RY = 50.0
private const val BOSS_FOCUS_FADE = 0.8
private const val BOSS_FOCUS_MAX_ALPHA = 200

private val DEATH_MOTTOS = listOf(
    "Todos para uno y nada para ti",
    "Sin honor, sin cartas, sin sombrero",
    "Adieu, mosquetero",
    "La partie est finie"
)

private lateinit var pixelFont: TtfFont

internal suspend fun loadPixelFont() {
    if (!::pixelFont.isInitialized) pixelFont = resourcesVfs["PixeloidSans.ttf"].readTtfFont()
}


private class FightResult(
    val outcome: DuelOutcome,
    val event: RunEvent,
    val title: String,
    val scores: String,
    val note: String,
    val runLine: String,
    val playerTotal: Int,
    val enemyTotal: Int
) {

    val isDefeat: Boolean
        get() = event == RunEvent.RUN_OVER
}

class DuelScene(
    private val tutorialMode: Boolean = false,
    private val resume: Boolean = false
) : Scene() {

    override suspend fun SContainer.sceneInit() {

        val saved = if (resume && !tutorialMode) views.loadSavedRun() else null


        var nextSeed: Long = saved?.seed ?: if (tutorialMode) TUTORIAL_SEED.toLong() else Random.nextLong()

        var bossSeed: Long = saved?.bossSeed ?: Random.nextLong()
        val gameRun = Run(saved?.cycle ?: 1, saved?.phase ?: 1, bossSeed) { Deck(Random(nextSeed)) }
        var duel = gameRun.duel
        var revealedCard: Card? = null
        var suitChangeMode = false
        var suitChangeTarget: Int? = null
        var aceChangeMode = false
        aceChangeMode = false
        var recoverDiscardMode = false
        var stealSwapMode = false


        if (saved == null) views.saveInventory(JokerInventory())
        var inventory = views.loadInventory()
        var confirmJokers = views.loadConfirmJokers()
        var inventoryOpen = false


        fun anyJokerMode() = suitChangeMode || aceChangeMode || recoverDiscardMode || stealSwapMode

        fun cancelJokerModes() {
            suitChangeMode = false
            suitChangeTarget = null
            aceChangeMode = false
            recoverDiscardMode = false
            stealSwapMode = false
        }


        fun resetJokerState() {
            cancelJokerModes()
            revealedCard = null
            inventoryOpen = false
        }


        fun consumeJoker(type: JokerType) {
            if (type !in inventory) return
            inventory = inventory.remove(type)

            if (!tutorialMode && views.unlockJoker(type)) {
                val toast = container { alpha = 0.0 }
                val plate = toast.plate("¡Nuevo en la colección: ${type.title}!", 0, 60, Colors["#ffe08a"])
                plate.x = (960 - plate.width) / 2
                this@DuelScene.launch {
                    toast.tween(toast::alpha[1.0], time = 0.3.seconds)
                    delay(2200)
                    toast.tween(toast::alpha[0.0], time = 0.6.seconds)
                    toast.removeFromParent()
                }
            }
        }


        fun activateJoker(type: JokerType) {
            cancelJokerModes()
            when (type) {
                JokerType.CHANGE_SUIT -> suitChangeMode = true
                JokerType.TO_ACE -> aceChangeMode = true
                JokerType.STEAL_SWAP -> stealSwapMode = true
                JokerType.RECOVER -> recoverDiscardMode = true
                JokerType.KINGS_AS_ACES -> if (duel.activateKingsAsAces()) consumeJoker(type)
                JokerType.VISION -> {
                    revealedCard = duel.useVision()
                    if (revealedCard != null) consumeJoker(type)
                }
            }
            GameAudio.play(Sfx.CARD_SELECT)
        }

        views.saveRun(gameRun.cycle, gameRun.phase, nextSeed, bossSeed)

        val playerLabel = views.loadPlayerName() ?: "Tú"
        var result: FightResult? = null
        var showOverlay = false

        println("DEBUG: Entrando a DuelScene")

        loadPixelFont()
        GameAudio.load(views)

        val cardImages = mutableMapOf<Card, Bitmap>()

        for (suit in Suit.entries) {
            for (rank in Rank.entries) {

                val card = Card(rank, suit)
                val path = card.imagePath()

                val bitmap = try {
                    resourcesVfs[path].readBitmap()
                } catch (e: Exception) {
                    null
                }

                if (bitmap != null) {
                    cardImages[card] = bitmap
                }
            }
        }

        println("Cartas cargadas: ${cardImages.size}/52")


        println("Cartas PNG cargadas: ${cardImages.size}")

        val sceneArt = resourcesVfs["escena_mosquetero.ase"]
            .readImageData(ASE.toProps().also { it.onlyReadVisibleLayers = false })
        val sceneryArt = SCENERY.associateWith { resourcesVfs[it.file].readImageData(ASE.toProps()) }
        val stageLayer = sceneArt.frames.first().layerData.first { it.layer.name == "Ecenario 1" }
        val tableLayer = sceneArt.frames.first().layerData.first { it.layer.name == "Mesa 1" }

        solidRect(4960, 4540, Colors.BLACK).position(-2000, -2000)
        val scenery = container()
        val background = imageDataView(sceneArt, playing = true, smoothing = false) { scale(ART_SCALE) }
        listOf("Background", "Ecenario 1", "Mesa 1", "Barra Lateral").forEach { background.getLayer(it)?.visible = false }
        val tableFront = container()


        var sceneryShown: Scenery? = null
        fun showScenery() {
            val place = sceneryFor(gameRun.cycle)
            if (place == sceneryShown) return
            sceneryShown = place
            val art = sceneryArt.getValue(place)
            val bitmap = art.frames.first().layerData.first().bitmap
            val artX = stageLayer.targetX - (bitmap.width - stageLayer.width) / 2
            val artY = stageLayer.targetY - (bitmap.height - stageLayer.height) / 2 + place.offsetY
            val tableTop = tableLayer.targetY - artY
            scenery.removeChildren()
            tableFront.removeChildren()
            scenery.imageDataView(art, playing = true, smoothing = false) {
                scale(ART_SCALE)
                position(artX * ART_SCALE, artY * ART_SCALE)
            }
            tableFront.image(bitmap.sliceWithSize(0, tableTop, bitmap.width, bitmap.height - tableTop)) {
                smoothing = false
                scale(ART_SCALE)
                position(artX * ART_SCALE, (artY + tableTop) * ART_SCALE)
            }
        }

        val bossFocus = image(bossFocusBitmap()) {
            smoothing = true
            scale(ART_SCALE)
        }

        var rivalName = ""
        fun pickRival() {
            showScenery()
            val pool = if (gameRun.encounter.isBoss) BOSS_RIVALS else NPC_RIVALS
            val rival = pool.random()
            rivalName = rival
            RIVALS.forEach { background.getLayer(it)?.visible = it == rival }
            val rivalLayer = sceneArt.frames.first().layerData.first { it.layer.name == rival }
            bossFocus.visible = gameRun.encounter.isBoss
            bossFocus.position(
                (rivalLayer.targetX + rivalLayer.width / 2.0 - BOSS_FOCUS_W / 2) * ART_SCALE,
                (rivalLayer.targetY + rivalLayer.height / 2.0 - BOSS_FOCUS_H / 2) * ART_SCALE
            )
        }
        pickRival()
        fun startEncounterAudio() {
            if (gameRun.encounter.isBoss) {
                GameAudio.playMusic(MusicTrack.BOSS)
                GameAudio.play(Sfx.BOSS_APPEARS)
            } else {
                GameAudio.playMusic(MusicTrack.DUEL)
            }
            GameAudio.play(Sfx.DRAW)
        }

        val buttonArt = SIDE_BUTTONS.mapValues { (name, artY) ->
            background.getLayer(name)?.visible = false
            val layer = sceneArt.frames.first().layerData.first { it.layer.name == name }
            layer.bitmap.sliceWithSize(BUTTON_ART_X - layer.targetX, artY - layer.targetY, BUTTON_ART_W, BUTTON_ART_H).extract()
        }

        val panelArt = sceneArt.frames.first().layerData.first { it.layer.name == "Barra Lateral" }.bitmap
        val sidebar = container()

        val deathArt =resourcesVfs["escena_muerte.ase"].readImageData(ASE.toProps())
        val cardBack = resourcesVfs["carta_reverso.png"].readBitmap()
        val jokerArt = JokerType.entries.associateWith {
            resourcesVfs["comodines/${it.file}.ase"].readImageData(ASE.toProps()).mainBitmap
        }

        val enemyLayer = container()
        val playerLayer = container()
        val sideLayer = container()
        val overlayLayer = container()
        val tutorialLayer = container()
        val handsLayer = container()
        val inventoryLayer = container()
        val gameMenuLayer = container()
        val confirmLayer = container()
        var tut: DuelTutorial? = null
        val tutorialTargets = if (!tutorialMode) emptySet() else {
            val scoring = duel.playerScore().scoringCards
            duel.playerHand.indices.filter { duel.playerHand[it] !in scoring }
                .sortedBy { duel.playerHand[it].rank.value }.take(3).toSet()
        }

        fun toggleHands() {
            GameAudio.play(Sfx.CLICK)
            if (handsLayer.numChildren > 0) handsLayer.removeChildren()
            else handsLayer.handsTable(panelArt, cardImages, views.actualVirtualBounds) { handsLayer.removeChildren() }
        }
        val fadeLayer = container()

        var sidebarHeight = -1
        addUpdater {
            val screen = views.actualVirtualBounds
            val artHeight = kotlin.math.ceil(screen.height / ART_SCALE).toInt()
            if (artHeight != sidebarHeight) {
                sidebarHeight = artHeight
                sidebar.removeChildren()
                if (artHeight > panelArt.height) sidebar.framePanel(panelArt, panelArt.width, artHeight)
                else sidebar.image(panelArt) { smoothing = false; scale(ART_SCALE) }
            }
            sidebar.position(screen.left, if (artHeight > panelArt.height) screen.top else 0.0)
            sideLayer.x = screen.left
        }

        lateinit var fadeToDeath: suspend () -> Unit
        lateinit var fadeToVictory: suspend (FightResult) -> Unit
        lateinit var renderInventory: () -> Unit

        fun closeInventory() {
            inventoryOpen = false
            inventoryLayer.removeChildren()
        }

        fun toggleInventory() {
            if (tut?.active == true) return
            GameAudio.play(Sfx.CLICK)
            if (inventoryOpen) {
                closeInventory()
            } else {
                inventoryOpen = true
                renderInventory()
            }
        }

        var leaving = false
        fun goToMenu(toShop: Boolean = false) {
            if (leaving) return
            leaving = true
            views.launch {
                val screen = views.actualVirtualBounds
                val black = views.stage.solidRect(screen.width, screen.height, Colors.BLACK) {
                    position(screen.x, screen.y)
                    alpha = 0.0
                }
                black.onClick { }
                black.tween(black::alpha[1.0], time = 1.0.seconds)
                if (toShop) sceneContainer.changeTo { ShopScene() } else sceneContainer.changeTo { MenuScene() }
                black.tween(black::alpha[0.0], time = 1.0.seconds)
                black.removeFromParent()
            }
        }

        fun closeConfirm() = confirmLayer.removeChildren()

        fun openConfirm() {
            if (confirmLayer.numChildren > 0) return
            val screen = views.actualVirtualBounds
            confirmLayer.solidRect(screen.width, screen.height, RGBA(0, 0, 0, 170)).position(screen.x, screen.y).onClick { }
            confirmLayer.framePanel(panelArt, CONFIRM_PANEL_ART_W, CONFIRM_PANEL_ART_H).position(240, 150)
            confirmLayer.pixelText("¿Salir al menú?", 33) { position((960 - width.toInt()) / 2, 180) }
            confirmLayer.pixelText(
                if (duel.isFinished) "Tu avance queda guardado." else "Al volver, la fase empieza de nuevo.", 16
            ) { position((960 - width.toInt()) / 2, 230) }
            confirmLayer.artButton(
                "Salir", buttonArt.getValue("Descartar"), 276, 282, true,
                artWidth = DEATH_BUTTON_ART_W, labelSize = 22
            ) {
                closeConfirm()
                goToMenu()
            }
            confirmLayer.artButton(
                "Cancelar", buttonArt.getValue("Nueva Partida"), 486, 282, true,
                artWidth = DEATH_BUTTON_ART_W, labelSize = 22, onTap = ::closeConfirm
            )
        }

        lateinit var restartRun: () -> Unit

        fun closeGameMenu() = gameMenuLayer.removeChildren()


        fun openAbandonConfirm() {
            if (confirmLayer.numChildren > 0) return
            val screen = views.actualVirtualBounds
            confirmLayer.solidRect(screen.width, screen.height, RGBA(0, 0, 0, 170)).position(screen.x, screen.y).onClick { }
            confirmLayer.framePanel(panelArt, CONFIRM_PANEL_ART_W, CONFIRM_PANEL_ART_H).position(240, 150)
            confirmLayer.pixelText("¿Abandonar la run?", 33) { position((960 - width.toInt()) / 2, 180) }
            confirmLayer.pixelText("Se pierden el avance y los comodines.", 16) { position((960 - width.toInt()) / 2, 230) }
            confirmLayer.artButton(
                "Abandonar", buttonArt.getValue("Descartar"), 276, 282, true,
                artWidth = DEATH_BUTTON_ART_W, labelSize = 22
            ) {
                closeConfirm()
                closeGameMenu()
                restartRun()
            }
            confirmLayer.artButton(
                "Cancelar", buttonArt.getValue("Nueva Partida"), 486, 282, true,
                artWidth = DEATH_BUTTON_ART_W, labelSize = 22, onTap = ::closeConfirm
            )
        }


        fun openDecks(onBack: () -> Unit) {
            gameMenuLayer.removeChildren()
            val screen = views.actualVirtualBounds
            gameMenuLayer.solidRect(screen.width, screen.height, RGBA(0, 0, 0, 170)).position(screen.x, screen.y).onClick { closeGameMenu() }
            val panelW = DECKS_PANEL_ART_W * ART_SCALE.toInt()
            gameMenuLayer.framePanel(panelArt, DECKS_PANEL_ART_W, GAME_MENU_PANEL_ART_H).position((960 - panelW) / 2, 60).onClick { }
            gameMenuLayer.pixelText("Mazos", 33) { position((960 - width.toInt()) / 2, 82) }
            val backScale = 120.0 / cardBack.height
            gameMenuLayer.image(cardBack) {
                smoothing = false
                scale(backScale)
                position((960 - cardBack.width * backScale) / 2, 135.0)
            }
            gameMenuLayer.pixelText("Mazo clásico (52 cartas)", 22) { position((960 - width.toInt()) / 2, 268) }
            gameMenuLayer.pixelText("En uso", 16, Colors["#81c784"]) { position((960 - width.toInt()) / 2, 302) }
            gameMenuLayer.pixelText("Pronto habrá más mazos.", 16, Colors["#aaaaaa"]) { position((960 - width.toInt()) / 2, 328) }
            val back = gameMenuLayer.plate("Volver", 0, 372)
            back.x = (960 - back.width) / 2
            back.onClick {
                GameAudio.play(Sfx.CLICK)
                onBack()
            }
        }


        fun openGameMenu() {
            if (tut?.active == true) return
            gameMenuLayer.removeChildren()
            val screen = views.actualVirtualBounds
            gameMenuLayer.solidRect(screen.width, screen.height, RGBA(0, 0, 0, 170)).position(screen.x, screen.y).onClick { closeGameMenu() }
            val panelW = GAME_MENU_PANEL_ART_W * ART_SCALE.toInt()
            val panelX = (960 - panelW) / 2
            gameMenuLayer.framePanel(panelArt, GAME_MENU_PANEL_ART_W, GAME_MENU_PANEL_ART_H).position(panelX, 60).onClick { }
            gameMenuLayer.pixelText("Menú", 33) { position((960 - width.toInt()) / 2, 82) }
            val buttonX = (960 - DEATH_BUTTON_ART_W * ART_SCALE.toInt()) / 2
            gameMenuLayer.artButton(
                "Nueva run", buttonArt.getValue("Nueva Partida"), buttonX, 125, true,
                artWidth = DEATH_BUTTON_ART_W, labelSize = 22, onTap = ::openAbandonConfirm
            )
            gameMenuLayer.artButton(
                "Mazos", buttonArt.getValue("Duelo"), buttonX, 205, true,
                artWidth = DEATH_BUTTON_ART_W, labelSize = 22
            ) { openDecks(onBack = ::openGameMenu) }
            gameMenuLayer.artButton(
                "Salir al menú", buttonArt.getValue("Descartar"), buttonX, 285, true,
                artWidth = DEATH_BUTTON_ART_W, labelSize = 18
            ) {
                closeGameMenu()
                openConfirm()
            }
            val back = gameMenuLayer.plate("Volver", 0, 372)
            back.x = (960 - back.width) / 2
            back.onClick {
                GameAudio.play(Sfx.CLICK)
                closeGameMenu()
            }
        }

        keys {
            down(Key.ESCAPE, Key.BACK) {
                when {
                    confirmLayer.numChildren > 0 -> closeConfirm()
                    gameMenuLayer.numChildren > 0 -> closeGameMenu()
                    inventoryOpen -> closeInventory()
                    handsLayer.numChildren > 0 -> handsLayer.removeChildren()
                    duel.isFinished -> goToMenu()
                    else -> openConfirm()
                }
            }
            down(Key.H) { toggleHands() }
            down(Key.C) { toggleInventory() }
            down(Key.M) { GameAudio.toggleMute() }
        }


        fun redraw() {
            enemyLayer.removeChildren()
            playerLayer.removeChildren()
            sideLayer.removeChildren()
            overlayLayer.removeChildren()


            val continueRun: () -> Unit = continueRun@{
                val finished = result
                if (finished == null || finished.event == RunEvent.RUN_OVER) {
                    restartRun()
                    return@continueRun
                }
                // Después de cada jefe (cada 3 duelos) viene la tienda
                if (tut?.active != true && finished.event != RunEvent.NEXT_PHASE) {
                    goToMenu(toShop = true)
                    return@continueRun
                }
                gameRun.advance()
                duel = gameRun.duel
                resetJokerState()
                startEncounterAudio()
                result = null
                showOverlay = false
                pickRival()
                redraw()
            }

            // Ciclo, fase y rival
            val enc = gameRun.encounter
            val info = buildString {
                append("Ciclo ${gameRun.cycle} - Fase ${gameRun.phase}/${Difficulty.PHASES_PER_CYCLE}")
                if (enc.isBoss) append(" - JEFE")
                append("\n$rivalName")
                enc.bossRule?.let { append("\n${bossRuleText(it, duel)}") }
                if (gameRun.isEndless) append("\nSin fin: objetivo +${enc.targetBonusPercent}%")
            }
            enemyLayer.plate(info, TABLE_X + 12, 12, color = if (enc.isBoss) Colors["#ff8a80"] else Colors.WHITE)


            val enemy = duel.enemyScore
            duel.enemyHand.forEachIndexed { i, card ->
                enemyLayer.cardView(
                    card,
                    ENEMY_X + i * ENEMY_STEP,
                    ENEMY_Y,
                    card in enemy.scoringCards,
                    false,
                    cardImages,
                    w = ENEMY_CARD_W,
                    h = ENEMY_CARD_H,
                    textSize = 22
                )
            }

            val rivalText = if (duel.targetBonusPercent > 0) {
                "Rival - ${scoreText(enemy)} +${duel.targetBonusPercent}% = ${duel.targetScore}"
            } else {
                "Rival - ${scoreText(enemy)}"
            }
            enemyLayer.plate(rivalText, ENEMY_X, ENEMY_Y + ENEMY_CARD_H + 8)


            val player = duel.playerScore()
            val beating = player.total > duel.targetScore
            duel.playerHand.forEachIndexed { i, card ->
                val selected = if (suitChangeMode) {
                    suitChangeTarget == i
                } else {
                    i in duel.selected
                }
                val debuffed = duel.debuff.affects(card)

                playerLayer.cardView(
                    card,
                    CARDS_X + i * CARD_STEP,
                    if (selected) PLAYER_Y - 20 else PLAYER_Y,
                    card in player.scoringCards && !debuffed,
                    selected,
                    cardImages,
                    debuffed = debuffed
                ) {
                    if (stealSwapMode) {

                        if (duel.stealAndSwap(i)) {
                            stealSwapMode = false
                            consumeJoker(JokerType.STEAL_SWAP)
                            GameAudio.play(Sfx.DRAW)
                            redraw()
                        } else {
                            GameAudio.play(Sfx.ERROR)
                        }

                    } else if (recoverDiscardMode) {

                        if (duel.recoverLastDiscard(i)) {
                            recoverDiscardMode = false
                            consumeJoker(JokerType.RECOVER)
                            GameAudio.play(Sfx.DRAW)
                            redraw()
                        } else {
                            GameAudio.play(Sfx.ERROR)
                        }

                    } else if (aceChangeMode) {

                        if (duel.convertToAce(i)) {
                            aceChangeMode = false
                            consumeJoker(JokerType.TO_ACE)
                            GameAudio.play(Sfx.CARD_SELECT)
                            redraw()
                        } else {
                            GameAudio.play(Sfx.ERROR)
                        }

                    } else if (suitChangeMode) {


                        suitChangeTarget = i
                        GameAudio.play(Sfx.CARD_SELECT)
                        redraw()

                    } else {

                        if (tut?.allows(TutorialWait.SELECT) != false) {
                            val wasSelected = i in duel.selected
                            if (duel.toggleSelect(i)) {
                                GameAudio.play(if (wasSelected) Sfx.CARD_DESELECT else Sfx.CARD_SELECT)
                            } else {
                                GameAudio.play(Sfx.ERROR)
                            }
                            redraw()

                            if (tut?.waiting(TutorialWait.SELECT) == true &&
                                duel.selected == tutorialTargets
                            ) {
                                tut?.advance()
                            }
                        }
                    }
                }

            }
            playerLayer.plate(
                "$playerLabel - ${scoreText(player)}", CARDS_X, PLAYER_Y + CARD_H + 8,
                color = if (beating) Colors["#81c784"] else Colors.WHITE
            )

            val targetIndex = suitChangeTarget

            if (suitChangeMode && targetIndex != null &&
                targetIndex in duel.playerHand.indices
            ) {
                val targetCard = duel.playerHand[targetIndex]

                playerLayer.pixelText("Elegir nuevo palo:", 16) {
                    position(CARDS_X, 340)
                }

                val suitOptions = listOf(
                    Suit.CLUBS to "Treboles",
                    Suit.DIAMONDS to "Diamantes",
                    Suit.HEARTS to "Corazones",
                    Suit.SPADES to "Picas"
                )

                suitOptions.forEachIndexed { i, (suit, label) ->


                    val validOption = suit != targetCard.suit

                    val optionText = playerLayer.pixelText(
                        label,
                        16,
                        if (validOption) Colors["#ffe082"]
                        else Colors["#777777"]
                    ) {
                        position(CARDS_X + i * 130, 363)
                    }

                    if (validOption) {
                        optionText.onClick {
                            if (duel.changeSuit(targetIndex, suit)) {
                                suitChangeMode = false
                                suitChangeTarget = null
                                consumeJoker(JokerType.CHANGE_SUIT)
                                GameAudio.play(Sfx.CARD_SELECT)
                                redraw()
                            }
                        }
                    }
                }
            }


            if (anyJokerMode()) {
                val hint = when {
                    suitChangeMode -> if (suitChangeTarget == null) "Cambiapalos: toca una carta" else null
                    aceChangeMode -> "Todos As: toca la carta que será as"
                    stealSwapMode -> "Intercambio: toca la carta que entregas"
                    else -> duel.discardPile.lastOrNull()?.let { "Recuperar ${cardText(it)}: toca la carta a cambiar" }
                }
                var cancelX = CARDS_X + 200          // al lado de "Elegir nuevo palo:"
                var cancelY = 340
                if (hint != null) {
                    val hintPlate = playerLayer.plate(hint, CARDS_X, JOKER_HINT_Y, color = Colors["#ffe082"])
                    cancelX = CARDS_X + hintPlate.width.toInt() + 16
                    cancelY = JOKER_HINT_Y
                }
                playerLayer.plate("Cancelar", cancelX, cancelY, color = Colors["#ff9e8a"]).onClick {
                    GameAudio.play(Sfx.CLICK)
                    cancelJokerModes()
                    redraw()
                }
            }

            revealedCard?.let { card ->

                playerLayer.pixelText("Proxima carta", 16) {
                    position(835, 365)
                }

                playerLayer.cardView(
                    card = card,
                    x = 854,
                    y = 395,
                    scoring = false,
                    selected = false,
                    cardImages = cardImages
                )
            }


            sideLayer.pixelText("Objetivo", 16) { position(SIDE_X, 31) }
            sideLayer.pixelText("${duel.targetScore}", 33) { position(SIDE_X, 50) }
            sideLayer.pixelText("Descartes: ${duel.roundsLeft}", 16) { position(SIDE_X, 96) }
            sideLayer.pixelText("Marcadas: ${duel.selected.size}/${Duel.MAX_DISCARD_PER_ROUND}", 16) { position(SIDE_X, 115) }


            val r = result
            if (r != null) {
                sideLayer.pixelText(r.title, if (r.title.length > 10) 16 else 22) { position(SIDE_X, 145) }
                sideLayer.pixelText("Tú: ${r.playerTotal}\nRival: ${r.enemyTotal}", 16) { position(SIDE_X, 177) }
            } else {
                sideLayer.pixelText("Verde:\nsuman", 16) { position(SIDE_X, 145) }
                sideLayer.pixelText("Amarillo:\nmarcada", 16) { position(SIDE_X, 195) }
            }


            if (tut?.active != true) {
                enemyLayer.plate(
                    "Comodines ${inventory.jokers.size}/${JokerInventory.MAX_SIZE} (C)",
                    INVENTORY_BUTTON_X, INVENTORY_BUTTON_Y, color = Colors["#ffe082"]
                ).onClick { toggleInventory() }
            }

            sideLayer.artButton("Descartar", buttonArt.getValue("Descartar"), SIDE_BUTTON_X, SIDE_BUTTONS.getValue("Descartar") * 3, duel.canDiscard && !anyJokerMode(), sound = null) {
                if (tut?.allows(TutorialWait.DISCARD) != false) {
                    duel.discardSelected()
                    GameAudio.play(Sfx.DISCARD)
                    revealedCard = null
                    redraw()
                    if (tut?.waiting(TutorialWait.DISCARD) == true) tut?.advance()
                }
            }
            sideLayer.artButton("¡Duelo!", buttonArt.getValue("Duelo"), SIDE_BUTTON_X, SIDE_BUTTONS.getValue("Duelo") * 3, !duel.isFinished && !anyJokerMode(), sound = null) {
                if (tut?.allows(TutorialWait.FIGHT) != false) {
                    val fought = fight(duel, gameRun)
                    views.saveInventory(inventory)

                    GameAudio.play(Sfx.FIGHT)
                    if (fought.event == RunEvent.RUN_OVER || fought.event == RunEvent.RUN_WON) GameAudio.stopMusic()
                    val resultSfx = when (fought.event) {
                        RunEvent.NEXT_PHASE -> Sfx.WIN
                        RunEvent.CYCLE_CLEARED -> Sfx.BOSS_DEFEATED
                        RunEvent.RUN_WON -> Sfx.VICTORY
                        RunEvent.RUN_OVER -> Sfx.LOSE
                    }
                    this@DuelScene.launch {
                        delay(350)
                        GameAudio.play(resultSfx)
                    }

                    views.recordBestRun(
                        RunStats.encountersWon(gameRun.cycle, gameRun.phase, fought.event != RunEvent.RUN_OVER)
                    )

                    if (fought.event == RunEvent.RUN_OVER) {
                        views.clearSavedRun()
                    } else {
                        if (fought.event == RunEvent.RUN_WON) views.recordRunWon()
                        val bossCleared = fought.event == RunEvent.CYCLE_CLEARED || fought.event == RunEvent.RUN_WON
                        nextSeed = Random.nextLong()
                        views.saveRun(
                            if (bossCleared) gameRun.cycle + 1 else gameRun.cycle,
                            if (bossCleared) 1 else gameRun.phase + 1,
                            nextSeed,
                            bossSeed
                        )
                    }

                    result = fought
                    if (tut?.waiting(TutorialWait.FIGHT) == true) {
                        redraw()
                        tut?.advance()
                    } else if (fought.isDefeat) {
                        redraw()
                        this@DuelScene.launch { fadeToDeath() }
                    } else if (tut?.active == true) {
                        showOverlay = true
                        redraw()
                    } else {
                        redraw()
                        this@DuelScene.launch { fadeToVictory(fought) }
                    }
                }
            }
            enemyLayer.plate("Manos (H)", 830, 504).onClick { toggleHands() }
            val sideLabel = when (r?.event) {
                RunEvent.NEXT_PHASE -> "Siguiente\nfase"
                RunEvent.CYCLE_CLEARED -> "Siguiente\nciclo"
                RunEvent.RUN_WON -> "Modo\nsin fin"
                RunEvent.RUN_OVER -> "Nueva\nrun"
                null -> "Menú"
            }
            sideLayer.artButton(sideLabel, buttonArt.getValue("Nueva Partida"), SIDE_BUTTON_X, SIDE_BUTTONS.getValue("Nueva Partida") * 3, true) {
                if (tut?.active == true) return@artButton
                if (r == null) openGameMenu() else continueRun()
            }


            if (r != null && showOverlay) {
                val screen = views.actualVirtualBounds
                val dim = overlayLayer.solidRect(screen.width, screen.height, RGBA(0, 0, 0, 170)).position(screen.x, screen.y)
                dim.onClick { }

                val showHands = {
                    showOverlay = false
                    redraw()
                }

                if (r.isDefeat) {
                    overlayLayer.imageDataView(deathArt, playing = true, smoothing = false) {
                        scale(ART_SCALE)
                        position(
                            (views.virtualWidth - deathArt.width * ART_SCALE) / 2,
                            (views.virtualHeight - deathArt.height * ART_SCALE) / 2
                        )
                    }
                    overlayLayer.pixelText(r.title, 44) { position(501, 40) }
                    overlayLayer.plate(r.scores, 507, 104, size = 22)

                    if (r.runLine.isNotEmpty()) overlayLayer.plate(r.runLine, 507, 234)
                    if (r.note.isNotEmpty()) overlayLayer.plate(r.note, 507, 321)
                    overlayLayer.artButton(
                        "Menú", buttonArt.getValue("Descartar"), 711, 231, true,
                        artWidth = DEATH_BUTTON_ART_W, labelSize = 22, onTap = { goToMenu() }
                    )
                    overlayLayer.artButton(
                        "Nueva run",
                        buttonArt.getValue("Duelo"), 501, 147, true,
                        artWidth = DEATH_BUTTON_ART_W, labelSize = 22, onTap = continueRun
                    )
                    overlayLayer.artButton(
                        "Ver manos", buttonArt.getValue("Nueva Partida"), 711, 147, true,
                        artWidth = DEATH_BUTTON_ART_W, labelSize = 22, onTap = showHands
                    )
                } else {
                    overlayLayer.framePanel(panelArt, RESULT_PANEL_ART_W, RESULT_PANEL_ART_H).position(240, 60)


                    overlayLayer.pixelText(r.title, 44) { position((960 - width.toInt()) / 2, 84) }
                    overlayLayer.pixelText(r.scores, 22) { position((960 - width.toInt()) / 2, 150) }
                    if (r.runLine.isNotEmpty()) {
                        overlayLayer.pixelText(r.runLine, 16) { position((960 - width.toInt()) / 2, 186) }
                    }
                    if (r.note.isNotEmpty()) {
                        overlayLayer.pixelText(r.note, 16) {
                            position((960 - width.toInt()) / 2, if (r.runLine.isNotEmpty()) 212 else 186)
                        }
                    }

                    val primaryLabel = when (r.event) {
                        RunEvent.NEXT_PHASE -> "Siguiente fase"
                        RunEvent.CYCLE_CLEARED -> "Siguiente ciclo"
                        RunEvent.RUN_WON -> "Modo sin fin"
                        else -> "Continuar"
                    }
                    overlayLayer.artButton(
                        primaryLabel, buttonArt.getValue("Duelo"), 276, 270, true,
                        artWidth = DEATH_BUTTON_ART_W, labelSize = if (primaryLabel.length > 10) 18 else 22,
                        onTap = continueRun
                    )
                    overlayLayer.artButton(
                        "Ver manos", buttonArt.getValue("Nueva Partida"), 486, 270, true,
                        artWidth = DEATH_BUTTON_ART_W, labelSize = 22, onTap = showHands
                    )
                    if (r.event == RunEvent.RUN_WON) {
                        // Ganó la run: puede seguir en modo sin fin o terminarla aquí
                        overlayLayer.artButton(
                            "Terminar run", buttonArt.getValue("Descartar"), 276, 354, true,
                            artWidth = MENU_BUTTON_ART_W, labelSize = 22
                        ) {
                            views.clearSavedRun()
                            goToMenu()
                        }
                    } else {
                        overlayLayer.artButton(
                            "Menú", buttonArt.getValue("Descartar"), 276, 354, true,
                            artWidth = MENU_BUTTON_ART_W, labelSize = 22, onTap = { goToMenu() }
                        )
                    }
                }
            }


            if (inventoryOpen) renderInventory() else inventoryLayer.removeChildren()
        }


        restartRun = {
            nextSeed = Random.nextLong()
            bossSeed = Random.nextLong()
            gameRun.restart(bossSeed)
            views.saveRun(gameRun.cycle, gameRun.phase, nextSeed, bossSeed)
            inventory = JokerInventory()
            views.saveInventory(inventory)
            duel = gameRun.duel
            resetJokerState()
            startEncounterAudio()
            result = null
            showOverlay = false
            pickRival()
            redraw()
        }

        fadeToDeath = {
            val screen = views.actualVirtualBounds
            val black = fadeLayer.solidRect(screen.width, screen.height, Colors.BLACK) {
                position(screen.x, screen.y)
                alpha = 0.0
            }
            black.onClick { }
            val motto = fadeLayer.pixelText(DEATH_MOTTOS.random(), 33) {
                position((960 - width.toInt()) / 2, (540 - height.toInt()) / 2)
                alpha = 0.0
            }
            black.tween(black::alpha[1.0], time = 1.0.seconds)
            motto.tween(motto::alpha[1.0], time = 0.8.seconds)
            GameAudio.playMusic(MusicTrack.GAME_OVER)
            delay(2200)
            motto.tween(motto::alpha[0.0], time = 0.8.seconds)
            showOverlay = true
            redraw()
            black.tween(black::alpha[0.0], time = 1.0.seconds)
            fadeLayer.removeChildren()
        }

        fadeToVictory = { r ->
            val screen = views.actualVirtualBounds
            val black = fadeLayer.solidRect(screen.width, screen.height, Colors.BLACK) {
                position(screen.x, screen.y)
                alpha = 0.0
            }
            black.onClick { }
            val text = fadeLayer.container { alpha = 0.0 }
            text.pixelText(if (r.event == RunEvent.NEXT_PHASE) "Touché" else r.title, 44) { position((960 - width.toInt()) / 2, 180) }
            text.pixelText(r.scores, 22) { position((960 - width.toInt()) / 2, 254) }
            text.pixelText(r.runLine, 22, Colors["#ffe08a"]) { position((960 - width.toInt()) / 2, 300) }
            black.tween(black::alpha[1.0], time = 1.0.seconds)
            text.tween(text::alpha[1.0], time = 0.8.seconds)
            delay(2200)
            if (r.event == RunEvent.RUN_WON) {

                showOverlay = true
                redraw()
                text.tween(text::alpha[0.0], time = 0.8.seconds)
                black.tween(black::alpha[0.0], time = 1.0.seconds)
                fadeLayer.removeChildren()
            } else if (r.event != RunEvent.NEXT_PHASE) {
                goToMenu(toShop = true)
            } else {
                gameRun.advance()
                duel = gameRun.duel
                resetJokerState()
                result = null
                pickRival()
                startEncounterAudio()
                redraw()
                text.tween(text::alpha[0.0], time = 0.8.seconds)
                black.tween(black::alpha[0.0], time = 1.0.seconds)
                fadeLayer.removeChildren()
            }
        }

        // ---------- inventario de comodines ----------
        renderInventory = {
            inventoryLayer.removeChildren()
            val screen = views.actualVirtualBounds
            inventoryLayer.solidRect(screen.width, screen.height, RGBA(0, 0, 0, 170))
                .position(screen.x, screen.y)
                .onClick { closeInventory() }
            inventoryLayer.framePanel(panelArt, INVENTORY_PANEL_ART_W, INVENTORY_PANEL_ART_H)
                .position(INVENTORY_PANEL_X, INVENTORY_PANEL_Y)
                .onClick { }
            inventoryLayer.pixelText("Comodines", 33) { position((960 - width.toInt()) / 2, INVENTORY_PANEL_Y + 22) }


            val infoLayer = inventoryLayer.container()
            fun showInfo(text: String, color: RGBA) {
                infoLayer.removeChildren()
                infoLayer.pixelText(text, 16, color) {
                    position((960 - width.toInt()) / 2, JOKER_SLOT_Y + JOKER_SLOT_H + 14)
                }
            }
            showInfo(INVENTORY_HINT, Colors["#d9b26a"])

            fun useJoker(type: JokerType) {
                closeInventory()
                activateJoker(type)
                redraw()
            }


            fun askToUse(type: JokerType) {
                val dialog = inventoryLayer.container()
                dialog.solidRect(screen.width, screen.height, RGBA(0, 0, 0, 150)).position(screen.x, screen.y).onClick { }
                dialog.framePanel(panelArt, JOKER_CONFIRM_ART_W, 92).position(480 - JOKER_CONFIRM_ART_W * ART_SCALE.toInt() / 2, 130)
                dialog.pixelText("¿Usar ${type.title}?", 33) { position((960 - width.toInt()) / 2, 152) }
                dialog.pixelText(type.description, 16) { position((960 - width.toInt()) / 2, 204) }
                dialog.pixelText("Se gasta al usarlo.", 16, Colors["#d9b26a"]) { position((960 - width.toInt()) / 2, 230) }

                dialog.artButton(
                    "Usar", buttonArt.getValue("Duelo"), 276, 276, true,
                    artWidth = DEATH_BUTTON_ART_W, labelSize = 22, sound = null
                ) { useJoker(type) }
                dialog.artButton(
                    "Cancelar", buttonArt.getValue("Nueva Partida"), 486, 276, true,
                    artWidth = DEATH_BUTTON_ART_W, labelSize = 22
                ) { dialog.removeFromParent() }
            }


            val slotsX = INVENTORY_PANEL_X + (INVENTORY_PANEL_ART_W * ART_SCALE.toInt() - (2 * JOKER_SLOT_W + JOKER_SLOT_GAP)) / 2
            for (slot in 0 until JokerInventory.MAX_SIZE) {
                val cell = inventoryLayer.container { position(slotsX + slot * (JOKER_SLOT_W + JOKER_SLOT_GAP), JOKER_SLOT_Y) }
                cell.solidRect(JOKER_SLOT_W, JOKER_SLOT_H, RGBA(0, 0, 0, 90))
                val type = inventory.jokers.getOrNull(slot)
                if (type == null) {
                    cell.pixelText("Vacío", 16, Colors["#888888"]) {
                        position((JOKER_SLOT_W - width.toInt()) / 2, (JOKER_SLOT_H - height.toInt()) / 2)
                    }
                    continue
                }

                val blocked = duel.jokerBlockedReason(type)
                val art = jokerArt.getValue(type)
                cell.image(art) {
                    smoothing = false
                    scale(JOKER_ART_SCALE)
                    position((JOKER_SLOT_W - art.width * JOKER_ART_SCALE) / 2, 6.0)
                    if (blocked != null) colorMul = Colors["#707070"]
                }
                cell.pixelText(type.title, 16) { position((JOKER_SLOT_W - width.toInt()) / 2, JOKER_SLOT_H - 44) }
                val status = when {
                    blocked == null -> "Disponible"
                    duel.jokerUsed(type) -> "Usado"
                    else -> "No disponible"
                }
                cell.pixelText(status, 16, if (blocked == null) Colors["#81c784"] else Colors["#aaaaaa"]) {
                    position((JOKER_SLOT_W - width.toInt()) / 2, JOKER_SLOT_H - 22)
                }

                cell.onTapOrHold(
                    holdSeconds = JOKER_HOLD_SECONDS,
                    onHold = {
                        GameAudio.play(Sfx.CLICK)
                        showInfo(type.description, Colors.WHITE)
                    },
                    onTap = {
                        when {
                            blocked != null -> {
                                GameAudio.play(Sfx.ERROR)
                                showInfo(blocked, Colors["#ff9e8a"])
                            }
                            confirmJokers -> {
                                GameAudio.play(Sfx.CLICK)
                                askToUse(type)
                            }
                            else -> useJoker(type)
                        }
                    }
                )
            }


            val confirmLabel = if (confirmJokers) "[X] Pedir confirmación antes de usar" else "[  ] Pedir confirmación antes de usar"
            val confirmCheck = inventoryLayer.plate(confirmLabel, 0, JOKER_SLOT_Y + JOKER_SLOT_H + 52, color = Colors["#d9b26a"])
            confirmCheck.x = (960 - confirmCheck.width) / 2
            confirmCheck.onClick {
                GameAudio.play(Sfx.CLICK)
                confirmJokers = !confirmJokers
                views.saveConfirmJokers(confirmJokers)
                renderInventory()
            }

            val close = inventoryLayer.plate("Cerrar (C)", 0, JOKER_SLOT_Y + JOKER_SLOT_H + 96)
            close.x = (960 - close.width) / 2
            close.onClick {
                GameAudio.play(Sfx.CLICK)
                closeInventory()
            }
        }

        if (tutorialMode) {
            val enemyRect = Rectangle(ENEMY_X - 8, ENEMY_Y - 8, 6 * ENEMY_STEP + ENEMY_CARD_W + 16, ENEMY_CARD_H + 46)
            val handRect = Rectangle(CARDS_X - 8, PLAYER_Y - 28, 6 * CARD_STEP + CARD_W + 16, CARD_H + 36)
            val scoreRect = Rectangle(CARDS_X - 14, PLAYER_Y + CARD_H + 2, 440, 30)
            val handsRect = Rectangle(820, 496, 130, 34)
            fun objectiveRect() = Rectangle(views.actualVirtualBounds.left + SIDE_X - 10, 24, 150, 74)
            fun cardRect(i: Int) = Rectangle(CARDS_X + i * CARD_STEP - 6, PLAYER_Y - 26, CARD_W + 12, CARD_H + 32)
            fun buttonRect(name: String) = Rectangle(
                views.actualVirtualBounds.left + SIDE_BUTTON_X - 4, SIDE_BUTTONS.getValue(name) * 3 - 4,
                BUTTON_ART_W * 3 + 8, BUTTON_ART_H * 3 + 8
            )

            tut = DuelTutorial(tutorialLayer, panelArt, { views.actualVirtualBounds }, { i ->
                when (i) {
                    0 -> TutorialStep(
                        "Este es tu rival. Su mejor mano vale ${duel.targetScore}\nfichas: ese es el objetivo que debes superar.",
                        TutorialWait.NEXT, false, listOf(enemyRect, objectiveRect())
                    )
                    1 -> TutorialStep(
                        "Estas son tus 7 cartas. Solo cuentan las 5\nque forman tu mejor mano de póker.\nLas cartas verdes son las que suman.",
                        TutorialWait.NEXT, true, listOf(handRect)
                    )
                    2 -> TutorialStep(
                        "Puntaje = (fichas de la mano + fichas de las\ncartas) x multiplicador. Con ${duel.playerScore().total} no le ganas\nal rival: hay que mejorar la mano.",
                        TutorialWait.NEXT, true, listOf(scoreRect)
                    )
                    3 -> TutorialStep(
                        "Toca las 3 cartas señaladas para marcarlas.\nLas cartas marcadas se cambian por nuevas.",
                        TutorialWait.SELECT, true, tutorialTargets.map { cardRect(it) }
                    )
                    4 -> TutorialStep(
                        "Pulsa Descartar. Tienes ${Duel.MAX_ROUNDS} descartes de\nhasta ${Duel.MAX_DISCARD_PER_ROUND} cartas cada uno.",
                        TutorialWait.DISCARD, true, listOf(buttonRect("Descartar"))
                    )
                    5 -> TutorialStep(
                        "¡Formaste un ${categoryName(duel.playerScore().category).lowercase()}! Tu mano vale\n${duel.playerScore().total} fichas y supera al rival.",
                        TutorialWait.NEXT, true, listOf(handRect, scoreRect)
                    )
                    6 -> TutorialStep(
                        "Pulsa ¡Duelo! para enfrentarte al rival.\nGana quien tenga más puntos.",
                        TutorialWait.FIGHT, true, listOf(buttonRect("Duelo"))
                    )
                    7 -> TutorialStep(
                        "¡Bien hecho, mosquetero! Pulsa H o toca\n\"Manos\" para ver la tabla de manos cuando\nquieras.",
                        TutorialWait.NEXT, true, listOf(handsRect)
                    )
                    else -> null
                }
            }) {
                views.storage[TUTORIAL_DONE_KEY] = "1"
                if (duel.isFinished && result != null) showOverlay = true
                redraw()
            }
        }

        startEncounterAudio()
        redraw()
        tut?.render()
    }
}



private fun Container.cardView(
    card: Card,
    x: Int,
    y: Int,
    scoring: Boolean,
    selected: Boolean,
    cardImages: Map<Card, Bitmap>,
    w: Int = CARD_W,
    h: Int = CARD_H,
    textSize: Int = 33,
    debuffed: Boolean = false,
    onTap: (() -> Unit)? = null
): Container = container {

    position(x, y)


    val bg = when {
        selected -> Colors["#ffe082"]
        scoring -> Colors["#c8e6c9"]
        else -> Colors.WHITE
    }


    solidRect(w + 4, h + 4, Colors["#2b1608"])
        .position(-2, -2)

    solidRect(w, h, bg)

    // Buscar el PNG correspondiente
    val bitmap = cardImages[card]

    if (bitmap != null) {

        // Mostrar la imagen PNG
        image(bitmap) {
            smoothing = false

            scaleX = w.toDouble() / bitmap.width
            scaleY = h.toDouble() / bitmap.height
        }

    } else {


        val ink = if (
            card.suit == Suit.HEARTS ||
            card.suit == Suit.DIAMONDS
        ) Colors["#c62828"] else Colors.BLACK

        pixelText(cardText(card), textSize, color = ink) {
            position(
                (w - width.toInt()) / 2,
                (h - height.toInt()) / 2
            )
        }
    }


    if (debuffed) solidRect(w, h, RGBA(0, 0, 0, 130))

    if (selected || scoring) {
        val borderColor = if (selected) {
            Colors["#ffe082"]
        } else {
            Colors["#81c784"]
        }

        solidRect(w, 3, borderColor)
        solidRect(w, 3, borderColor).position(0, h - 3)
        solidRect(3, h, borderColor)
        solidRect(3, h, borderColor).position(w - 3, 0)
    }


    if (onTap != null) {
        onClick { onTap() }
    }
}


internal fun Container.artButton(
    label: String, art: Bitmap, x: Int, y: Int, enabled: Boolean,
    artWidth: Int = art.width, labelSize: Int = 16,
    sound: Sfx? = Sfx.CLICK,
    onTap: () -> Unit
): Container = container {
    position(x, y)
    container {
        val mid = art.width / 2
        image(art.sliceWithSize(0, 0, BUTTON_CAP, art.height)) { smoothing = false }
        image(art.sliceWithSize(mid, 0, 1, art.height)) {
            smoothing = false
            position(BUTTON_CAP, 0)
            scaleX = (artWidth - 2 * BUTTON_CAP).toDouble()
        }
        image(art.sliceWithSize(art.width - BUTTON_CAP, 0, BUTTON_CAP, art.height)) {
            smoothing = false
            position(artWidth - BUTTON_CAP, 0)
        }
        scale(ART_SCALE)
        if (!enabled) colorMul = Colors["#808080"]
    }
    val w = (artWidth * ART_SCALE).toInt()
    val h = (art.height * ART_SCALE).toInt()
    val lines = label.lines().map { pixelText(it, labelSize, if (enabled) Colors.WHITE else Colors["#bbbbbb"]) }
    val lineH = lines.first().height.toInt()
    val top = (h - lineH * lines.size) / 2
    lines.forEachIndexed { i, line -> line.position((w - line.width.toInt()) / 2, top + i * lineH) }
    if (enabled) {
        onClick {
            sound?.let { GameAudio.play(it) }
            onTap()
        }
    } else {
        onClick { GameAudio.play(Sfx.ERROR) }
    }
}

internal fun Container.framePanel(art: Bitmap, artW: Int, artH: Int): Container = container {
    val cols = listOf(Triple(0, PANEL_CAP, 0), Triple(art.width / 2, 1, PANEL_CAP), Triple(art.width - PANEL_CAP, PANEL_CAP, artW - PANEL_CAP))
    val rows = listOf(Triple(0, PANEL_CAP, 0), Triple(art.height / 2, 1, PANEL_CAP), Triple(art.height - PANEL_CAP, PANEL_CAP, artH - PANEL_CAP))
    for ((sx, sw, dx) in cols) for ((sy, sh, dy) in rows) {
        image(art.sliceWithSize(sx, sy, sw, sh)) {
            smoothing = false
            position(dx, dy)
            scaleX = if (sw == 1) (artW - 2 * PANEL_CAP).toDouble() else 1.0
            scaleY = if (sh == 1) (artH - 2 * PANEL_CAP).toDouble() else 1.0
        }
    }
    scale(ART_SCALE)
}


private const val FONT_SCALE = 1
private const val FONT_GRID = 4
private const val FONT_RASTER = 28

internal fun fontSize(size: Int): Int =
    ((size * FONT_SCALE / FONT_GRID + 0.5).toInt() * FONT_GRID).coerceAtLeast(8)

private var rasterFont: BitmapFont? = null

private fun pixelFontOf(): BitmapFont = rasterFont
    ?: pixelFont.toBitmapFont(FONT_RASTER, CharacterSet.LATIN_ALL, mipmaps = false).also { font ->
        (font.baseBmp as Bitmap32).updateColors { if (it.a >= 128) Colors.WHITE else Colors.TRANSPARENT }
        rasterFont = font
    }

internal fun Container.pixelText(
    label: String, size: Int = 16, color: RGBA = Colors.WHITE, block: Text.() -> Unit = {}
): Text = text(label, fontSize(size), color, pixelFontOf()) {
    smoothing = false
    block()
}

internal fun Container.plate(label: String, x: Int, y: Int, color: RGBA = Colors.WHITE, size: Int = 16): Container = container {
    position(x, y)
    val t = Text(label, fontSize(size), color, pixelFontOf()).apply { smoothing = false }
    solidRect(t.width + 12, t.height + 6, RGBA(0, 0, 0, 160)).position(-6, -3)
    addChild(t)
}

private fun bossRuleText(rule: BossRule, duel: Duel): String = when (rule) {
    BossRule.FEWER_DISCARDS -> "Solo ${duel.maxRounds} descartes"
    BossRule.NO_DIAMONDS -> "Los diamantes no suman fichas"
    BossRule.NO_HEARTS -> "Los corazones no suman fichas"
    BossRule.NO_CLUBS -> "Los tréboles no suman fichas"
    BossRule.NO_SPADES -> "Las picas no suman fichas"
    BossRule.NO_FACES -> "K, Q y J no suman fichas"
}

private fun fight(duel: Duel, gameRun: Run): FightResult {
    val outcome = duel.fight()
    val event = gameRun.report(outcome)
    val p = duel.playerScore()
    val e = duel.enemyScore
    val title = when {
        event == RunEvent.RUN_WON -> "¡VICTORIA!"
        event == RunEvent.CYCLE_CLEARED -> "¡JEFE VENCIDO!"
        outcome == DuelOutcome.WIN -> "¡GANASTE!"
        outcome == DuelOutcome.LOSE -> "PERDISTE"
        else -> "EMPATE"
    }
    val note = when {
        outcome == DuelOutcome.WIN && p.category < e.category ->
            "Tu mano es de categoría menor,\npero tus cartas sumaron más fichas."
        outcome == DuelOutcome.LOSE && p.category > e.category ->
            "Tu mano es de mayor categoría,\npero las cartas del rival\nsumaron más fichas."
        else -> ""
    }

    val runLine = when (event) {
        RunEvent.NEXT_PHASE ->
            if (gameRun.phase + 1 == Difficulty.PHASES_PER_CYCLE) "Siguiente: ¡el JEFE!"
            else "Siguiente: fase ${gameRun.phase + 1}/${Difficulty.PHASES_PER_CYCLE}"
        RunEvent.CYCLE_CLEARED -> "¡Ciclo ${gameRun.cycle} completado! Sube la dificultad."
        RunEvent.RUN_WON -> "Venciste a los ${Difficulty.CYCLES_TO_WIN} jefes.\nPuedes seguir en modo sin fin."

        else -> (if (outcome == DuelOutcome.DRAW) "El empate cuenta\ncomo derrota.\n" else "") +
            "Run terminada en\nciclo ${gameRun.cycle}, fase ${gameRun.phase}."
    }

    val target = duel.targetScore
    val rivalLabel = if (duel.targetBonusPercent > 0) "Objetivo" else "Rival"
    return FightResult(outcome, event, title, "Tú: ${p.total}   vs   $rivalLabel: $target", note, runLine, p.total, target)
}

private fun bossFocusBitmap(): Bitmap32 {
    val bitmap = Bitmap32(BOSS_FOCUS_W, BOSS_FOCUS_H, premultiplied = true)
    for (y in 0 until BOSS_FOCUS_H) for (x in 0 until BOSS_FOCUS_W) {
        val dx = (x + 0.5 - BOSS_FOCUS_W / 2) / BOSS_FOCUS_RX
        val dy = (y + 0.5 - BOSS_FOCUS_H / 2) / BOSS_FOCUS_RY
        val t = ((sqrt(dx * dx + dy * dy) - 1) / BOSS_FOCUS_FADE).coerceIn(0.0, 1.0)
        bitmap[x, y] = RGBA(0, 0, 0, (BOSS_FOCUS_MAX_ALPHA * t * t * (3 - 2 * t)).toInt())
    }
    return bitmap
}


private fun View.onTapOrHold(holdSeconds: Double, onHold: () -> Unit, onTap: () -> Unit) {
    var pressed = false
    var held = false
    var elapsed = 0.0
    onDown {
        pressed = true
        held = false
        elapsed = 0.0
    }
    onUp {
        if (pressed && !held) onTap()
        pressed = false
    }
    onUpOutside { pressed = false }
    addUpdater { dt ->
        if (pressed && !held) {
            elapsed += dt.inWholeMilliseconds / 1000.0
            if (elapsed >= holdSeconds) {
                held = true
                onHold()
            }
        }
    }
}
