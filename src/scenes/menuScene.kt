package scenes

import korlibs.event.*
import korlibs.image.color.*
import korlibs.image.format.*
import korlibs.io.file.std.*
import korlibs.korge.input.*
import korlibs.korge.scene.*
import korlibs.korge.service.storage.*
import korlibs.korge.tween.*
import korlibs.korge.view.*
import korlibs.korge.view.animation.*
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.time.Duration.Companion.seconds

private const val MENU_LABEL_SIZE = 33
private const val MENU_BUTTON_CENTER_Y = 8
private const val MENU_HOVER_SCALE = 1.1

private const val LOADING_CARD = "carta_reverso.png"
private const val LOADING_CARD_SCALE = 1.0
private const val LOADING_SPEED = 4.0

private class MenuRow(val box: View, val normal: View, val highlighted: View, val x: Double, val y: Double)

class MenuScene : Scene() {

    override suspend fun SContainer.sceneInit() {
        loadPixelFont()

        val menuArt = resourcesVfs["escena_menu.ase"].readImageData(ASE.toProps())
        val art = container {
            position(
                (views.virtualWidth - menuArt.width * ART_SCALE) / 2,
                (views.virtualHeight - menuArt.height * ART_SCALE) / 2
            )
        }
        val background = art.imageDataView(menuArt, playing = true, smoothing = false) { scale(ART_SCALE) }
        background.getLayer("BtnSeleccionado")?.visible = false
        background.getLayer("Btn")?.visible = false

        val selectedArt = menuArt.frames.first().layerData.first { it.layer.name == "BtnSeleccionado" }
        val normalArt = menuArt.frames.first().layerData.first { it.layer.name == "Btn" }
        val rowStep = normalArt.targetY - selectedArt.targetY

        var selected = 0
        var leaving = false

        val loadingCard = resourcesVfs[LOADING_CARD].readBitmap()

        fun play(tutorial: Boolean) {
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
                val loader = views.stage.container { position(views.virtualWidth / 2.0, views.virtualHeight / 2.0 - 20) }
                val card = loader.image(loadingCard) {
                    anchor(0.5, 0.5)
                    smoothing = false
                    scaleY = LOADING_CARD_SCALE
                }
                loader.pixelText("Revolviendo cartas", 22) { position(-width / 2, 74.0) }
                var elapsed = 0.0
                card.addUpdater { dt ->
                    elapsed += dt.inWholeMilliseconds / 1000.0
                    scaleX = abs(cos(elapsed * LOADING_SPEED)) * LOADING_CARD_SCALE
                }
                sceneContainer.changeTo { DuelScene(tutorial) }
                loader.removeFromParent()
                black.tween(black::alpha[0.0], time = 1.0.seconds)
                black.removeFromParent()
            }
        }

        val options = listOf<Pair<String, () -> Unit>>(
            "Jugar" to { play(views.storage.getOrNull(TUTORIAL_DONE_KEY) == null) },
            "Mazos" to {},
            "Salir" to { views.gameWindow.close() }
        )

        lateinit var rows: List<MenuRow>

        val buttonW = selectedArt.width * ART_SCALE
        val buttonH = selectedArt.height * ART_SCALE

        fun select(index: Int) {
            selected = index
            rows.forEachIndexed { i, row ->
                val active = i == index
                val s = if (active) MENU_HOVER_SCALE else 1.0
                row.highlighted.visible = active
                row.normal.visible = !active
                row.box.scale = s
                row.box.position(row.x - buttonW * (s - 1) / 2, row.y - buttonH * (s - 1) / 2)
            }
        }

        rows = options.mapIndexed { i, (label, action) ->
            val x = selectedArt.targetX * ART_SCALE
            val y = (selectedArt.targetY + i * rowStep) * ART_SCALE
            val box = art.container()
            val normal = box.image(normalArt.slice) {
                smoothing = false
                scale(ART_SCALE)
                position((normalArt.targetX - selectedArt.targetX) * ART_SCALE, 0.0)
            }
            val highlighted = box.image(selectedArt.slice) {
                smoothing = false
                scale(ART_SCALE)
            }
            box.pixelText(label, MENU_LABEL_SIZE) {
                position(
                    (buttonW / 2 - width / 2).toInt(),
                    (MENU_BUTTON_CENTER_Y * ART_SCALE - height / 2).toInt()
                )
            }
            box.onOver { select(i) }
            box.onClick { action() }
            MenuRow(box, normal, highlighted, x, y)
        }

        keys {
            down(Key.UP) { select((selected + rows.size - 1) % rows.size) }
            down(Key.DOWN) { select((selected + 1) % rows.size) }
            down(Key.ENTER, Key.RETURN, Key.SPACE) { options[selected].second() }
        }

        plate("Tutorial", 836, 500).onClick { play(true) }

        select(0)
    }
}
