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

private val RIVALS = listOf("Mosquetero 1", "Cobrador de impuestos", "Princesa incognita")

private val DEATH_MOTTOS = listOf(
    "Todos para uno y nada para ti",
    "Touché",
    "Sin honor, sin cartas, sin sombrero",
    "Adieu, mosquetero",
    "La partie est finie"
)

private lateinit var pixelFont: TtfFont

internal suspend fun loadPixelFont() {
    if (!::pixelFont.isInitialized) pixelFont = resourcesVfs["PixelifySans.ttf"].readTtfFont()
}


private class FightResult(
    val outcome: DuelOutcome,
    val title: String,
    val scores: String,
    val note: String,
    val playerTotal: Int,
    val enemyTotal: Int
)

class DuelScene(private val tutorialMode: Boolean = false) : Scene() {

    override suspend fun SContainer.sceneInit() {
        var duel = if (tutorialMode) Duel(Deck(Random(TUTORIAL_SEED))) else Duel()
        var result: FightResult? = null
        var showOverlay = false

        println("DEBUG: Entrando a DuelScene")

        loadPixelFont()


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
        val tavernArt = resourcesVfs["escena_taberna.ase"].readImageData(ASE.toProps())
        val tavern = tavernArt.frames.first().layerData.first().bitmap
        val stageLayer = sceneArt.frames.first().layerData.first { it.layer.name == "Ecenario 1" }
        val tableLayer = sceneArt.frames.first().layerData.first { it.layer.name == "Mesa 1" }
        val tavernX = stageLayer.targetX - (tavern.width - stageLayer.width) / 2
        val tavernY = stageLayer.targetY - (tavern.height - stageLayer.height) / 2
        val tableTop = tableLayer.targetY - tavernY

        solidRect(4960, 4540, Colors.BLACK).position(-2000, -2000)
        imageDataView(tavernArt, playing = true, smoothing = false) {
            scale(ART_SCALE)
            position(tavernX * ART_SCALE, tavernY * ART_SCALE)
        }
        val background = imageDataView(sceneArt, playing = true, smoothing = false) { scale(ART_SCALE) }
        listOf("Background", "Ecenario 1", "Mesa 1", "Barra Lateral").forEach { background.getLayer(it)?.visible = false }
        image(tavern.sliceWithSize(0, tableTop, tavern.width, tavern.height - tableTop)) {
            smoothing = false
            scale(ART_SCALE)
            position(tavernX * ART_SCALE, (tavernY + tableTop) * ART_SCALE)
        }

        fun pickRival() {
            val rival = RIVALS.random()
            RIVALS.forEach { background.getLayer(it)?.visible = it == rival }
        }
        pickRival()

        val buttonArt = SIDE_BUTTONS.mapValues { (name, artY) ->
            background.getLayer(name)?.visible = false
            val layer = sceneArt.frames.first().layerData.first { it.layer.name == name }
            layer.bitmap.sliceWithSize(BUTTON_ART_X - layer.targetX, artY - layer.targetY, BUTTON_ART_W, BUTTON_ART_H).extract()
        }
        git add src/scenes/duelTutorial.kt src/scenes/duelScene.kt src/scenes/menuScene.kt src/model/Scoring.kt
        val panelArt = sceneArt.frames.first().layerData.first { it.layer.name == "Barra Lateral" }.bitmap
        val sidebar = container()

        val deathArt =resourcesVfs["escena_muerte.ase"].readImageData(ASE.toProps())

        val enemyLayer = container()
        val playerLayer = container()
        val sideLayer = container()
        val overlayLayer = container()
        val tutorialLayer = container()
        val handsLayer = container()
        val confirmLayer = container()
        var tut: DuelTutorial? = null
        val tutorialTargets = if (!tutorialMode) emptySet() else {
            val scoring = duel.playerScore().scoringCards
            duel.playerHand.indices.filter { duel.playerHand[it] !in scoring }
                .sortedBy { duel.playerHand[it].rank.value }.take(3).toSet()
        }

        fun toggleHands() {
            if (handsLayer.numChildren > 0) handsLayer.removeChildren()
            else handsLayer.handsTable(panelArt, views.actualVirtualBounds) { handsLayer.removeChildren() }
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

        var leaving = false
        fun goToMenu() {
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
                sceneContainer.changeTo { MenuScene() }
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
            confirmLayer.pixelText("Se perderá la partida actual.", 16) { position((960 - width.toInt()) / 2, 230) }
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

        keys {
            down(Key.ESCAPE, Key.BACK) {
                when {
                    handsLayer.numChildren > 0 -> handsLayer.removeChildren()
                    confirmLayer.numChildren > 0 -> closeConfirm()
                    duel.isFinished -> goToMenu()
                    else -> openConfirm()
                }
            }
            down(Key.H) { toggleHands() }
        }


        fun redraw() {
            enemyLayer.removeChildren()
            playerLayer.removeChildren()
            sideLayer.removeChildren()
            overlayLayer.removeChildren()


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
            enemyLayer.plate("Rival - ${scoreText(enemy)}", ENEMY_X, ENEMY_Y + ENEMY_CARD_H + 8)


            val player = duel.playerScore()
            val beating = player.total > duel.targetScore
            duel.playerHand.forEachIndexed { i, card ->
                val selected = i in duel.selected

                playerLayer.cardView(
                    card,
                    CARDS_X + i * CARD_STEP,
                    if (selected) PLAYER_Y - 20 else PLAYER_Y,
                    card in player.scoringCards,
                    selected,
                    cardImages
                ) {
                    if (tut?.allows(TutorialWait.SELECT) != false) {
                        duel.toggleSelect(i)
                        redraw()
                        if (tut?.waiting(TutorialWait.SELECT) == true && duel.selected == tutorialTargets) tut?.advance()
                    }
                }

            }
            playerLayer.plate(
                "Tú - ${scoreText(player)}", CARDS_X, PLAYER_Y + CARD_H + 8,
                color = if (beating) Colors["#81c784"] else Colors.WHITE
            )


            sideLayer.pixelText("Objetivo", 16) { position(SIDE_X, 31) }
            sideLayer.pixelText("${duel.targetScore}", 33) { position(SIDE_X, 50) }
            sideLayer.pixelText("Descartes: ${duel.roundsLeft}", 16) { position(SIDE_X, 96) }
            sideLayer.pixelText("Marcadas: ${duel.selected.size}/${Duel.MAX_DISCARD_PER_ROUND}", 16) { position(SIDE_X, 115) }


            val r = result
            if (r != null) {
                sideLayer.pixelText(r.title, 22) { position(SIDE_X, 145) }
                sideLayer.pixelText("Tú: ${r.playerTotal}\nRival: ${r.enemyTotal}", 16) { position(SIDE_X, 177) }
            } else {
                sideLayer.pixelText("Verde:\nsuman", 16) { position(SIDE_X, 145) }
                sideLayer.pixelText("Amarillo:\nmarcada", 16) { position(SIDE_X, 195) }
            }

            sideLayer.artButton("Descartar", buttonArt.getValue("Descartar"), SIDE_BUTTON_X, SIDE_BUTTONS.getValue("Descartar") * 3, duel.canDiscard) {
                if (tut?.allows(TutorialWait.DISCARD) != false) {
                    duel.discardSelected()
                    redraw()
                    if (tut?.waiting(TutorialWait.DISCARD) == true) tut?.advance()
                }
            }
            sideLayer.artButton("¡Duelo!", buttonArt.getValue("Duelo"), SIDE_BUTTON_X, SIDE_BUTTONS.getValue("Duelo") * 3, !duel.isFinished) {
                if (tut?.allows(TutorialWait.FIGHT) != false) {
                    val fought = fight(duel)
                    result = fought
                    if (tut?.waiting(TutorialWait.FIGHT) == true) {
                        redraw()
                        tut?.advance()
                    } else if (fought.outcome == DuelOutcome.LOSE) {
                        redraw()
                        this@DuelScene.launch { fadeToDeath() }
                    } else {
                        showOverlay = true
                        redraw()
                    }
                }
            }
            enemyLayer.plate("Manos (H)", 830, 504).onClick { toggleHands() }
            sideLayer.artButton("Nueva\npartida", buttonArt.getValue("Nueva Partida"), SIDE_BUTTON_X, SIDE_BUTTONS.getValue("Nueva Partida") * 3, true) {
                if (tut?.active == true) return@artButton
                duel = Duel()
                result = null
                showOverlay = false
                pickRival()
                redraw()
            }


            if (r != null && showOverlay) {
                val screen = views.actualVirtualBounds
                val dim = overlayLayer.solidRect(screen.width, screen.height, RGBA(0, 0, 0, 170)).position(screen.x, screen.y)
                dim.onClick { }   // captura los toques para que no lleguen a lo que está debajo

                val playAgain = {
                    duel = Duel()
                    result = null
                    showOverlay = false
                    pickRival()
                    redraw()
                }
                val showHands = {
                    showOverlay = false
                    redraw()
                }

                if (r.outcome == DuelOutcome.LOSE) {
                    overlayLayer.imageDataView(deathArt, playing = true, smoothing = false) {
                        scale(ART_SCALE)
                        position(
                            (views.virtualWidth - deathArt.width * ART_SCALE) / 2,
                            (views.virtualHeight - deathArt.height * ART_SCALE) / 2
                        )
                    }
                    overlayLayer.pixelText(r.title, 44) { position(501, 40) }
                    overlayLayer.plate(r.scores, 507, 104, size = 22)
                    if (r.note.isNotEmpty()) overlayLayer.plate(r.note, 507, 321)
                    overlayLayer.artButton(
                        "Menú", buttonArt.getValue("Descartar"), 711, 231, true,
                        artWidth = DEATH_BUTTON_ART_W, labelSize = 22, onTap = ::goToMenu
                    )
                    overlayLayer.artButton(
                        "Jugar de nuevo", buttonArt.getValue("Duelo"), 501, 147, true,
                        artWidth = DEATH_BUTTON_ART_W, labelSize = 22, onTap = playAgain
                    )
                    overlayLayer.artButton(
                        "Ver manos", buttonArt.getValue("Nueva Partida"), 711, 147, true,
                        artWidth = DEATH_BUTTON_ART_W, labelSize = 22, onTap = showHands
                    )
                } else {
                    overlayLayer.framePanel(panelArt, RESULT_PANEL_ART_W, RESULT_PANEL_ART_H).position(240, 60)


                    overlayLayer.pixelText(r.title, 44) { position((960 - width.toInt()) / 2, 84) }
                    overlayLayer.pixelText(r.scores, 22) { position((960 - width.toInt()) / 2, 150) }
                    if (r.note.isNotEmpty()) {
                        overlayLayer.pixelText(r.note, 22) { position((960 - width.toInt()) / 2, 186) }
                    }

                    overlayLayer.artButton(
                        "Continuar", buttonArt.getValue("Duelo"), 276, 270, true,
                        artWidth = DEATH_BUTTON_ART_W, labelSize = 22, onTap = playAgain
                    )
                    overlayLayer.artButton(
                        "Ver manos", buttonArt.getValue("Nueva Partida"), 486, 270, true,
                        artWidth = DEATH_BUTTON_ART_W, labelSize = 22, onTap = showHands
                    )
                    overlayLayer.artButton(
                        "Menú", buttonArt.getValue("Descartar"), 276, 354, true,
                        artWidth = MENU_BUTTON_ART_W, labelSize = 22, onTap = ::goToMenu
                    )
                }
            }
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
            delay(2200)
            motto.tween(motto::alpha[0.0], time = 0.8.seconds)
            showOverlay = true
            redraw()
            black.tween(black::alpha[0.0], time = 1.0.seconds)
            fadeLayer.removeChildren()
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
    onTap: (() -> Unit)? = null
): Container = container {

    position(x, y)

    // Color de fondo según el estado de la carta
    val bg = when {
        selected -> Colors["#ffe082"]
        scoring -> Colors["#c8e6c9"]
        else -> Colors.WHITE
    }

    // Borde exterior de la carta
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

        // Si todavía no existe PNG, mostrar texto
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

    // Indicador visual de selección o puntuación
    if (selected || scoring) {
        val borderColor = if (selected) {
            Colors["#ffe082"]
        } else {
            Colors["#81c784"]
        }

        // Bordes indicadores
        solidRect(w, 3, borderColor)
        solidRect(w, 3, borderColor).position(0, h - 3)
        solidRect(3, h, borderColor)
        solidRect(3, h, borderColor).position(w - 3, 0)
    }

    // Mantener la interacción
    if (onTap != null) {
        onClick { onTap() }
    }
}


private fun Container.artButton(
    label: String, art: Bitmap, x: Int, y: Int, enabled: Boolean,
    artWidth: Int = art.width, labelSize: Int = 16, onTap: () -> Unit
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
    if (enabled) onClick { onTap() }
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

private val pixelFonts =mutableMapOf<Int, BitmapFont>()

private fun pixelFontOf(size: Int): BitmapFont = pixelFonts.getOrPut(size) {
    pixelFont.toBitmapFont(size, CharacterSet.LATIN_ALL, mipmaps = false).also { font ->
        (font.baseBmp as Bitmap32).updateColors { if (it.a >= 128) Colors.WHITE else Colors.TRANSPARENT }
    }
}

internal fun Container.pixelText(
    label: String, size: Int = 16, color: RGBA = Colors.WHITE, block: Text.() -> Unit = {}
): Text = text(label, size, color, pixelFontOf(size)) {
    smoothing = false
    block()
}

internal fun Container.plate(label: String, x: Int, y: Int, color: RGBA = Colors.WHITE, size: Int = 16): Container = container {
    position(x, y)
    val t = Text(label, size, color, pixelFontOf(size)).apply { smoothing = false }
    solidRect(t.width + 12, t.height + 6, RGBA(0, 0, 0, 160)).position(-6, -3)
    addChild(t)
}

private fun fight(duel: Duel): FightResult {
    val outcome = duel.fight()
    val p = duel.playerScore()
    val e = duel.enemyScore
    val title = when (outcome) {
        DuelOutcome.WIN -> "¡GANASTE!"
        DuelOutcome.LOSE -> "PERDISTE"
        DuelOutcome.DRAW -> "EMPATE"
    }
    val note = when {
        outcome == DuelOutcome.WIN && p.category < e.category ->
            "Tu mano es de categoría menor,\npero tus cartas sumaron más fichas."
        outcome == DuelOutcome.LOSE && p.category > e.category ->
            "Tu mano es de mayor categoría,\npero las cartas del rival\nsumaron más fichas."
        else -> ""
    }
    return FightResult(outcome, title, "Tú: ${p.total}   vs   Rival: ${e.total}", note, p.total, e.total)
}
