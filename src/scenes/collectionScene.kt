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
import kotlin.math.sin
import kotlin.time.Duration.Companion.seconds

private const val COLLECTION_COLS = 4
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

class CollectionScene : Scene() {

    override suspend fun SContainer.sceneInit() {
        loadPixelFont()

        val menuArt = resourcesVfs["escena_menu.ase"].readImageData(ASE.toProps())
        val uiArt = resourcesVfs["escena_mosquetero.ase"].readImageData(ASE.toProps().also {
            it.onlyReadVisibleLayers = false
            it.setExtra("layers", "Barra Lateral")
        })
        val panelArt = uiArt.frames.first().layerData.first { it.layer.name == "Barra Lateral" }.bitmap
        val jokerArt = JOKERS.associateWith { resourcesVfs["comodines/${it.file}.ase"].readImageData(ASE.toProps()).mainBitmap }

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

        val rows = (JOKERS.size + COLLECTION_COLS - 1) / COLLECTION_COLS
        val cellW = (gridW - 2 * COLLECTION_PAD) / COLLECTION_COLS
        val cellH = (panelH - 2 * COLLECTION_PAD) / rows
        val cellsX = COLLECTION_GRID_X + COLLECTION_PAD
        val cellsY = COLLECTION_Y + COLLECTION_PAD

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

        fun select(index: Int) {
            selected = index
            val joker = JOKERS[index]
            val art = jokerArt.getValue(joker)
            val centerX = COLLECTION_INFO_X + infoW / 2
            info.removeChildren()
            info.image(art) {
                smoothing = false
                scale(COLLECTION_PREVIEW_SCALE)
                position(centerX - art.width * COLLECTION_PREVIEW_SCALE / 2, COLLECTION_Y + 40.0)
            }
            info.pixelText(joker.name, 22, Colors["#ffe08a"]) { position(centerX - width.toInt() / 2, COLLECTION_Y + 232) }
            info.pixelText(wrap(joker.description, 16, infoW - 2 * COLLECTION_PAD), 16) {
                position(COLLECTION_INFO_X + COLLECTION_PAD, COLLECTION_Y + 276)
            }
            val x = cellsX + index % COLLECTION_COLS * cellW
            val y = cellsY + index / COLLECTION_COLS * cellH
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

        JOKERS.forEachIndexed { i, joker ->
            val x = cellsX + i % COLLECTION_COLS * cellW
            val y = cellsY + i / COLLECTION_COLS * cellH
            val art = jokerArt.getValue(joker)
            val cell = container()
            cell.solidRect(cellW, cellH, Colors.TRANSPARENT).position(x, y)
            cell.image(art) {
                smoothing = false
                scale(COLLECTION_CARD_SCALE)
                position(x + (cellW - art.width * COLLECTION_CARD_SCALE) / 2, y + 8.0)
            }
            cell.pixelText(joker.name, 16) { position(x + (cellW - width.toInt()) / 2, y + 146) }
            cell.onOver { select(i) }
            cell.onClick { select(i) }
        }
        addChild(glow)

        plate("Volver (Esc)", 20, 500).onClick {
            GameAudio.play(Sfx.CLICK)
            backToMenu()
        }

        keys {
            down(Key.ESCAPE, Key.BACK) { backToMenu() }
            down(Key.LEFT) { select((selected + JOKERS.size - 1) % JOKERS.size) }
            down(Key.RIGHT) { select((selected + 1) % JOKERS.size) }
            down(Key.UP) { select((selected + JOKERS.size - COLLECTION_COLS) % JOKERS.size) }
            down(Key.DOWN) { select((selected + COLLECTION_COLS) % JOKERS.size) }
        }

        select(0)
    }
}
