package scenes

import korlibs.datastructure.*
import korlibs.event.*
import korlibs.image.color.*
import korlibs.image.format.*
import korlibs.io.file.std.*
import korlibs.korge.input.*
import korlibs.korge.scene.*
import korlibs.korge.tween.*
import korlibs.korge.view.*
import korlibs.korge.view.animation.*
import kotlinx.coroutines.launch
import model.JokerType
import kotlin.math.sin
import kotlin.time.Duration.Companion.seconds

private const val COLLECTION_COLS = 3
private const val COLLECTION_Y = 76
private const val COLLECTION_ART_H = 136
private const val COLLECTION_GRID_X = 36
private const val COLLECTION_GRID_ART_W = 172
private const val COLLECTION_INFO_X = 570
private const val COLLECTION_INFO_ART_W = 118
private const val COLLECTION_PAD = 30
private const val COLLECTION_CARD_SCALE = 1.5
private const val COLLECTION_PREVIEW_SCALE = 2.0
private const val COLLECTION_DIM_ALPHA = 150
private val LOCKED_TINT = RGBA(25, 20, 30, 255)
private const val LOCKED_NAME = "???"
private const val LOCKED_HINT = "Encuéntralo en la tienda y úsalo para desbloquearlo."


private val COLLECTION_JOKERS = JokerType.entries

class CollectionScene : Scene() {

    override suspend fun SContainer.sceneInit() {
        loadPixelFont()
        GameAudio.load(views)
        GameAudio.playMusic(MusicTrack.MENU)

        val menuArt = resourcesVfs["escena_menu.ase"].readImageData(ASE.toProps())
        val uiArt = resourcesVfs["escena_mosquetero.ase"].readImageData(ASE.toProps().also {
            it.onlyReadVisibleLayers = false
            it.setExtra("layers", "Barra Lateral")
        })
        val panelArt = uiArt.frames.first().layerData.first { it.layer.name == "Barra Lateral" }.bitmap
        val unlocked = views.loadUnlockedJokers()
        fun isUnlocked(joker: JokerType) = joker in unlocked
        val jokerArt = COLLECTION_JOKERS.associateWith { resourcesVfs["comodines/${it.file}.ase"].readImageData(ASE.toProps()).mainBitmap }

        solidRect(4960, 4540, Colors.BLACK).position(-2000, -2000)
        val background = imageDataView(menuArt, playing = true, smoothing = false) {
            scale(ART_SCALE)
            position((views.virtualWidth - menuArt.width * ART_SCALE) / 2, (views.virtualHeight - menuArt.height * ART_SCALE) / 2)
        }
        listOf("Titulo", "BtnSeleccionado", "Btn").forEach { background.getLayer(it)?.visible = false }
        val screen = views.actualVirtualBounds
        solidRect(screen.width, screen.height, RGBA(0, 0, 0, COLLECTION_DIM_ALPHA)).position(screen.x, screen.y)

        pixelText("Colección", 33, Colors["#ffe08a"]) { position((960 - width.toInt()) / 2, 18) }

        val gridW = COLLECTION_GRID_ART_W * ART_SCALE.toInt()
        val panelH = COLLECTION_ART_H * ART_SCALE.toInt()
        val infoW = COLLECTION_INFO_ART_W * ART_SCALE.toInt()
        framePanel(panelArt, COLLECTION_GRID_ART_W, COLLECTION_ART_H).position(COLLECTION_GRID_X, COLLECTION_Y)
        framePanel(panelArt, COLLECTION_INFO_ART_W, COLLECTION_ART_H).position(COLLECTION_INFO_X, COLLECTION_Y)

        val rows = (COLLECTION_JOKERS.size + COLLECTION_COLS - 1) / COLLECTION_COLS
        val cellW = (gridW - 2 * COLLECTION_PAD) / COLLECTION_COLS
        val cellH = (panelH - 2 * COLLECTION_PAD) / rows
        val cellsX = COLLECTION_GRID_X + COLLECTION_PAD
        val cellsY = COLLECTION_Y + COLLECTION_PAD


        fun cellX(i: Int): Int {
            val row = i / COLLECTION_COLS
            val inRow = minOf(COLLECTION_COLS, COLLECTION_JOKERS.size - row * COLLECTION_COLS)
            return cellsX + (COLLECTION_COLS - inRow) * cellW / 2 + i % COLLECTION_COLS * cellW
        }
        fun cellY(i: Int): Int = cellsY + i / COLLECTION_COLS * cellH

        val found = COLLECTION_JOKERS.count { isUnlocked(it) }
        val counter = plate("Desbloqueados: $found/${COLLECTION_JOKERS.size}", 0, 500, Colors["#ffe08a"])
        counter.x = COLLECTION_INFO_X + infoW - counter.width

        val info = container()
        val glow = container()
        var t = 0.0
        glow.addUpdater { dt ->
            t += dt.inWholeMilliseconds / 1000.0
            alpha = 0.55 + 0.45 * sin(t * 6)
        }

        fun wrap(text: String, size: Int, maxW: Int): String {
            val probe = Container()
            val lines = mutableListOf<String>()
            var line = ""
            text.replace("\n", " ").split(" ").forEach { word ->
                val next = if (line.isEmpty()) word else "$line $word"
                if (line.isNotEmpty() && probe.pixelText(next, size).width > maxW) {
                    lines += line
                    line = word
                } else {
                    line = next
                }
            }
            if (line.isNotEmpty()) lines += line
            return lines.joinToString("\n")
        }

        var selected = 0
        var leaving = false

        fun select(index: Int, sound: Boolean = false) {
            if (sound && index != selected) GameAudio.play(Sfx.CLICK)
            selected = index
            val joker = COLLECTION_JOKERS[index]
            val art = jokerArt.getValue(joker)
            val centerX = COLLECTION_INFO_X + infoW / 2
            info.removeChildren()
            val open = isUnlocked(joker)
            info.image(art) {
                smoothing = false
                scale(COLLECTION_PREVIEW_SCALE)
                position(centerX - art.width * COLLECTION_PREVIEW_SCALE / 2, COLLECTION_Y + 40.0)
                if (!open) colorMul = LOCKED_TINT
            }
            if (!open) {
                info.pixelText("?", 44, Colors["#d9b26a"]) {
                    position(centerX - width.toInt() / 2, COLLECTION_Y + 40 + (art.height * COLLECTION_PREVIEW_SCALE - height).toInt() / 2)
                }
            }
            info.pixelText(if (open) joker.title else LOCKED_NAME, 22, Colors["#ffe08a"]) {
                position(centerX - width.toInt() / 2, COLLECTION_Y + 232)
            }
            // Descripción centrada línea por línea
            val text = if (open) joker.description else LOCKED_HINT
            wrap(text, 16, infoW - 2 * COLLECTION_PAD).split("\n").forEachIndexed { line, part ->
                info.pixelText(part, 16, if (open) Colors.WHITE else Colors["#bbbbbb"]) {
                    position(centerX - width.toInt() / 2, COLLECTION_Y + 276 + line * 22)
                }
            }
            val x = cellX(index)
            val y = cellY(index)
            val c = Colors["#ffd54f"]
            glow.removeChildren()
            glow.solidRect(cellW, 3, c).position(x, y)
            glow.solidRect(cellW, 3, c).position(x, y + cellH - 3)
            glow.solidRect(3, cellH, c).position(x, y)
            glow.solidRect(3, cellH, c).position(x + cellW - 3, y)
        }

        fun backToMenu() {
            if (leaving) return
            leaving = true
            views.launch {
                val bounds = views.actualVirtualBounds
                val black = views.stage.solidRect(bounds.width, bounds.height, Colors.BLACK) {
                    position(bounds.x, bounds.y)
                    alpha = 0.0
                }
                black.onClick { }
                black.tween(black::alpha[1.0], time = 0.6.seconds)
                sceneContainer.changeTo { MenuScene() }
                black.tween(black::alpha[0.0], time = 0.6.seconds)
                black.removeFromParent()
            }
        }

        COLLECTION_JOKERS.forEachIndexed { i, joker ->
            val x = cellX(i)
            val y = cellY(i)
            val art = jokerArt.getValue(joker)
            val open = isUnlocked(joker)
            val cell = container()
            cell.solidRect(cellW, cellH, Colors.TRANSPARENT).position(x, y)
            cell.image(art) {
                smoothing = false
                scale(COLLECTION_CARD_SCALE)
                position(x + (cellW - art.width * COLLECTION_CARD_SCALE) / 2, y + 8.0)
                if (!open) colorMul = LOCKED_TINT
            }
            if (!open) {
                cell.pixelText("?", 33, Colors["#d9b26a"]) {
                    position(x + (cellW - width.toInt()) / 2, y + 8 + (art.height * COLLECTION_CARD_SCALE - height).toInt() / 2)
                }
            }
            cell.pixelText(if (open) joker.title else LOCKED_NAME, 16, if (open) Colors.WHITE else Colors["#aaaaaa"]) { position(x + (cellW - width.toInt()) / 2, y + 146) }
            cell.onOver { select(i, sound = true) }
            cell.onClick { select(i, sound = true) }
        }
        addChild(glow)

        plate("Volver (Esc)", 20, 500).onClick {
            GameAudio.play(Sfx.CLICK)
            backToMenu()
        }

        keys {
            down(Key.ESCAPE, Key.BACK) { backToMenu() }
            down(Key.LEFT) { select((selected + COLLECTION_JOKERS.size - 1) % COLLECTION_JOKERS.size, sound = true) }
            down(Key.RIGHT) { select((selected + 1) % COLLECTION_JOKERS.size, sound = true) }
            down(Key.UP) { select((selected + COLLECTION_JOKERS.size - COLLECTION_COLS) % COLLECTION_JOKERS.size, sound = true) }
            down(Key.DOWN) { select((selected + COLLECTION_COLS) % COLLECTION_JOKERS.size, sound = true) }
        }

        select(0)
    }
}
