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
import model.*
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

internal const val JOKER_ART_SCALE = 1.25
private const val REPLACE_ART_H = 100

class ShopScene : Scene() {

    override suspend fun SContainer.sceneInit() {
        loadPixelFont()
        GameAudio.load(views)
        GameAudio.playMusic(MusicTrack.SHOP)

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

        // La oferta nunca repite un comodín que el jugador ya tiene
        var inventory = views.loadInventory()
        val offer = inventory.shopOffer(count = SHOP_CHOICES)
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

        val ownedText = if (inventory.jokers.isEmpty()) "Inventario vacío" else
            "Tienes: ${inventory.jokers.joinToString(", ") { it.title }}"
        pixelText("$ownedText  (${inventory.jokers.size}/${JokerInventory.MAX_SIZE})", 16, Colors["#d9b26a"]) {
            position(SHOP_BOX_X + (boxW - width.toInt()) / 2, SHOP_BOX_Y + boxH - 34)
        }

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
        var confirmAction: (() -> Unit)? = null

        fun select(index: Int) {
            selected = index
            val joker = offer[index]
            info.removeChildren()
            info.pixelText(joker.title, 22) { position(SHOP_BOX_X + 36, SHOP_INFO_Y + 26) }
            info.pixelText(joker.description, 16) { position(SHOP_BOX_X + 36, SHOP_INFO_Y + 68) }
            val x = cellsX + index * cellW
            val c = Colors["#ffd54f"]
            glow.removeChildren()
            glow.solidRect(cellW, 3, c).position(x, cellsY)
            glow.solidRect(cellW, 3, c).position(x, cellsY + cellH - 3)
            glow.solidRect(3, cellH, c).position(x, cellsY)
            glow.solidRect(3, cellH, c).position(x + cellW - 3, cellsY)
        }

        fun closeDialog() {
            dialog.removeChildren()
            confirmAction = null
        }

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


        fun take(newInventory: JokerInventory) {
            inventory = newInventory
            views.saveInventory(newInventory)
            GameAudio.play(Sfx.CARD_SELECT)
            backToDuel()
        }

        fun choose() {
            if (dialog.numChildren > 0) return
            val joker = offer[selected]
            val screen = views.actualVirtualBounds
            val buttonW = SHOP_BUTTON_ART_W * ART_SCALE.toInt()
            dialog.solidRect(screen.width, screen.height, RGBA(0, 0, 0, 170)).position(screen.x, screen.y).onClick { }

            if (!inventory.isFull) {
                dialog.framePanel(panelArt, CONFIRM_ART_W, CONFIRM_ART_H).position(240, 165)
                dialog.pixelText("¿Elegir ${joker.title}?", 22) { position((960 - width.toInt()) / 2, 205) }
                val accept = { take(inventory.add(joker)) }
                confirmAction = accept
                dialog.artButton(
                    "Elegir", buttonArt, 480 - buttonW - 12, 270, true,
                    artWidth = SHOP_BUTTON_ART_W, labelSize = 22, onTap = accept
                )
                dialog.artButton(
                    "Cancelar", buttonArt, 492, 270, true,
                    artWidth = SHOP_BUTTON_ART_W, labelSize = 22, onTap = ::closeDialog
                )
                return
            }

            // Inventario lleno: el jugador elige cuál de sus comodines suelta
            dialog.framePanel(panelArt, CONFIRM_ART_W, REPLACE_ART_H).position(240, 120)
            dialog.pixelText("Inventario lleno", 22, Colors["#ffe08a"]) { position((960 - width.toInt()) / 2, 150) }
            dialog.pixelText("¿Cuál cambias por ${joker.title}?", 16) { position((960 - width.toInt()) / 2, 190) }
            inventory.jokers.forEachIndexed { i, owned ->
                dialog.artButton(
                    owned.title, buttonArt, if (i == 0) 480 - buttonW - 12 else 492, 230, true,
                    artWidth = SHOP_BUTTON_ART_W, labelSize = 18
                ) { take(inventory.replace(owned, joker)) }
            }
            dialog.artButton(
                "Cancelar", buttonArt, 480 - buttonW / 2, 318, true,
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
            cell.pixelText(joker.title, 16) { position(x + (cellW - width.toInt()) / 2, cellsY + 122) }
            cell.onOver { if (dialog.numChildren == 0) select(i) }
            cell.onClick {
                if (dialog.numChildren > 0) return@onClick
                GameAudio.play(Sfx.CLICK)
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
            down(Key.LEFT) {
                if (dialog.numChildren == 0) {
                    GameAudio.play(Sfx.CLICK)
                    select((selected + offer.size - 1) % offer.size)
                }
            }
            down(Key.RIGHT) {
                if (dialog.numChildren == 0) {
                    GameAudio.play(Sfx.CLICK)
                    select((selected + 1) % offer.size)
                }
            }
            down(Key.ENTER, Key.RETURN, Key.SPACE) {
                if (dialog.numChildren > 0) {
                    confirmAction?.invoke()
                } else {
                    GameAudio.play(Sfx.CLICK)
                    choose()
                }
            }
        }

        select(0)
    }
}
