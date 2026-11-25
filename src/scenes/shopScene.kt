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

private const val SHOP_CHOICES = 3
private const val SHOP_BOX_X = 48
private const val SHOP_BOX_Y = 120
private const val SHOP_BOX_ART_W = 176
private const val SHOP_BOX_ART_H = 78
private const val SHOP_INFO_Y = 372
private const val SHOP_INFO_ART_H = 50
private const val SHOP_KEEPER = "Vendedor"
private const val SHOP_KEEPER_ART_X = 300
private const val SHOP_KEEPER_ART_BOTTOM = 160
private const val SHOP_KEEPER_SCALE = 4.0
private const val SHOP_BUTTON_ART_W = 66
private const val SHOP_BUTTON_X = 633
private const val SHOP_BUTTON_Y = 444
private const val CONFIRM_ART_W = 160
private const val CONFIRM_ART_H = 70

private const val JOKER_ART_SCALE = 1.25

internal class Joker(val name: String, val file: String, val description: String)

internal val JOKERS = listOf(
    Joker("Cambiapalos", "cambiapalos", "Cambia el palo de cualquier carta."),
    Joker("Todos As", "todos_as", "Convierte cualquier carta en un as."),
    Joker("Intercambio", "intercambio", "Roba una carta del rival y dale una tuya."),
    Joker("Recuperar", "recuperar", "Recupera una carta de tu pila de descarte."),
    Joker("Reyes Ases", "reyes_ases", "Los reyes cuentan como ases."),
    Joker("Apuesta", "apuesta", "Una carta al azar de tu mano se convierte\nen un comodín al azar."),
    Joker("Espejo", "espejo", "Vuelve a usar un comodín que ya usaste\nen esta partida."),
    Joker("Futuro", "futuro", "Mira la próxima carta del mazo antes\nde decidir tu descarte.")
)

class ShopScene : Scene() {

    override suspend fun SContainer.sceneInit() {
        loadPixelFont()

        val shopArt = resourcesVfs["escena_tienda.ase"].readImageData(ASE.toProps())
        val uiArt = resourcesVfs["escena_mosquetero.ase"].readImageData(ASE.toProps().also {
            it.onlyReadVisibleLayers = false
            it.setExtra("layers", "Barra Lateral,Nueva Partida")
        })
        val panelArt = uiArt.frames.first().layerData.first { it.layer.name == "Barra Lateral" }.bitmap
        val buttonArt = uiArt.frames.first().layerData.first { it.layer.name == "Nueva Partida" }.bitmap
        val keeperArt = resourcesVfs["vendedor.ase"].readImageData(ASE.toProps().also {
            it.onlyReadVisibleLayers = false
            it.setExtra("layers", SHOP_KEEPER)
        })
        val keeper = keeperArt.frames.first().layerData.first { it.layer.name == SHOP_KEEPER }
        val offer = JOKERS.shuffled().take(SHOP_CHOICES)
        val jokerArt = offer.associateWith { resourcesVfs["comodines/${it.file}.ase"].readImageData(ASE.toProps()).mainBitmap }

        val artX = (views.virtualWidth - shopArt.width * ART_SCALE) / 2
        val artY = (views.virtualHeight - shopArt.height * ART_SCALE) / 2

        solidRect(4960, 4540, Colors.BLACK).position(-2000, -2000)
        imageDataView(shopArt, playing = true, smoothing = false) {
            scale(ART_SCALE)
            position(artX, artY)
        }
        imageDataView(keeperArt, playing = true, smoothing = false) {
            scale(SHOP_KEEPER_SCALE)
            position(
                artX + SHOP_KEEPER_ART_X * ART_SCALE - (keeper.targetX + keeper.width / 2.0) * SHOP_KEEPER_SCALE,
                artY + SHOP_KEEPER_ART_BOTTOM * ART_SCALE - (keeper.targetY + keeper.height) * SHOP_KEEPER_SCALE
            )
        }

        val boxW = SHOP_BOX_ART_W * ART_SCALE.toInt()
        val boxH = SHOP_BOX_ART_H * ART_SCALE.toInt()
        framePanel(panelArt, SHOP_BOX_ART_W, SHOP_BOX_ART_H).position(SHOP_BOX_X, SHOP_BOX_Y)
        pixelText("Elige un comodín", 22, Colors["#ffe08a"]) { position(SHOP_BOX_X + (boxW - width.toInt()) / 2, SHOP_BOX_Y + 22) }

        val cellW = (boxW - 60) / SHOP_CHOICES
        val cellH = boxH - 90
        val cellsX = SHOP_BOX_X + 30
        val cellsY = SHOP_BOX_Y + 54

        framePanel(panelArt, SHOP_BOX_ART_W, SHOP_INFO_ART_H).position(SHOP_BOX_X, SHOP_INFO_Y)
        val info = container()

        val glow = container()
        var t = 0.0
        glow.addUpdater { dt ->
            t += dt.inWholeMilliseconds / 1000.0
            alpha = 0.55 + 0.45 * sin(t * 6)
        }

        val dialog = container()
        var selected = 0
        var leaving = false

        fun select(index: Int) {
            selected = index
            val joker = offer[index]
            info.removeChildren()
            info.pixelText(joker.name, 22) { position(SHOP_BOX_X + 36, SHOP_INFO_Y + 26) }
            info.pixelText(joker.description, 16) { position(SHOP_BOX_X + 36, SHOP_INFO_Y + 68) }
            val x = cellsX + index * cellW
            val c = Colors["#ffd54f"]
            glow.removeChildren()
            glow.solidRect(cellW, 3, c).position(x, cellsY)
            glow.solidRect(cellW, 3, c).position(x, cellsY + cellH - 3)
            glow.solidRect(3, cellH, c).position(x, cellsY)
            glow.solidRect(3, cellH, c).position(x + cellW - 3, cellsY)
        }

        fun closeDialog() = dialog.removeChildren()

        fun backToDuel() {
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
                sceneContainer.changeTo { DuelScene(resume = true) }
                black.tween(black::alpha[0.0], time = 1.0.seconds)
                black.removeFromParent()
            }
        }

        fun choose() {
            if (dialog.numChildren > 0) return
            val joker = offer[selected]
            val screen = views.actualVirtualBounds
            val buttonW = SHOP_BUTTON_ART_W * ART_SCALE.toInt()
            dialog.solidRect(screen.width, screen.height, RGBA(0, 0, 0, 170)).position(screen.x, screen.y).onClick { }
            dialog.framePanel(panelArt, CONFIRM_ART_W, CONFIRM_ART_H).position(240, 165)
            dialog.pixelText("¿Elegir ${joker.name}?", 22) { position((960 - width.toInt()) / 2, 205) }
            dialog.artButton(
                "Elegir", buttonArt, 480 - buttonW - 12, 270, true,
                artWidth = SHOP_BUTTON_ART_W, labelSize = 22, onTap = ::backToDuel
            )
            dialog.artButton(
                "Cancelar", buttonArt, 492, 270, true,
                artWidth = SHOP_BUTTON_ART_W, labelSize = 22, onTap = ::closeDialog
            )
        }

        offer.forEachIndexed { i, joker ->
            val x = cellsX + i * cellW
            val cell = container()
            cell.solidRect(cellW, cellH, Colors.TRANSPARENT).position(x, cellsY)
            val art = jokerArt.getValue(joker)
            cell.image(art) {
                smoothing = false
                scale(JOKER_ART_SCALE)
                position(x + (cellW - art.width * JOKER_ART_SCALE) / 2, cellsY + 4.0)
            }
            cell.pixelText(joker.name, 16) { position(x + (cellW - width.toInt()) / 2, cellsY + 122) }
            cell.onOver { if (dialog.numChildren == 0) select(i) }
            cell.onClick {
                select(i)
                choose()
            }
        }
        addChild(glow)

        artButton(
            "Saltar", buttonArt, SHOP_BUTTON_X, SHOP_BUTTON_Y, true,
            artWidth = SHOP_BUTTON_ART_W, labelSize = 22, onTap = ::backToDuel
        )
        addChild(dialog)

        keys {
            down(Key.ESCAPE, Key.BACK) { closeDialog() }
            down(Key.LEFT) { if (dialog.numChildren == 0) select((selected + SHOP_CHOICES - 1) % SHOP_CHOICES) }
            down(Key.RIGHT) { if (dialog.numChildren == 0) select((selected + 1) % SHOP_CHOICES) }
            down(Key.ENTER, Key.RETURN, Key.SPACE) { if (dialog.numChildren > 0) backToDuel() else choose() }
        }

        select(0)
    }
}
