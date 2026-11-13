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
import model.Difficulty
import model.PlayerName
import model.RunStats
import kotlin.math.abs
import kotlin.math.cos
import kotlin.time.Duration.Companion.seconds

private const val MENU_LABEL_SIZE = 33
private const val MENU_BUTTON_CENTER_Y = 8
private const val MENU_HOVER_SCALE = 1.1

private const val LOADING_CARD = "carta_reverso.png"
private const val LOADING_CARD_SCALE = 1.0
private const val LOADING_SPEED = 4.0

// Teclado en pantalla para el nombre (funciona igual en Android y en escritorio)
private const val NAME_KEYS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
private const val NAME_COLS = 9
private const val NAME_KEY_SIZE = 46
private const val NAME_KEY_STEP = 52
private const val NAME_GRID_X = (960 - ((NAME_COLS - 1) * NAME_KEY_STEP + NAME_KEY_SIZE)) / 2
private const val NAME_GRID_Y = 185

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

        fun play(tutorial: Boolean, resume: Boolean = false) {
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
                sceneContainer.changeTo { DuelScene(tutorial, resume) }
                loader.removeFromParent()
                black.tween(black::alpha[0.0], time = 1.0.seconds)
                black.removeFromParent()
            }
        }


        val nameLayer = Container()
        val dialogLayer = Container()
        var playerName: String? = views.loadPlayerName()

        fun closeDialog() = dialogLayer.removeChildren()

        fun dimBackground() {
            val screen = views.actualVirtualBounds
            dialogLayer.solidRect(screen.width, screen.height, RGBA(0, 0, 0, 170))
                .position(screen.x, screen.y)
                .onClick { }
        }


        fun openChoice(
            title: String, detail: String,
            primary: Pair<String, () -> Unit>, secondary: Pair<String, () -> Unit>
        ) {
            closeDialog()
            dimBackground()
            dialogLayer.dialogPanel(240, 150, 480, 230)
            dialogLayer.pixelText(title, 33) { position((960 - width.toInt()) / 2, 175) }
            dialogLayer.pixelText(detail, 16) { position((960 - width.toInt()) / 2, 232) }
            dialogLayer.menuKey(primary.first, 264, 290, 206, 56) {
                closeDialog()
                primary.second()
            }
            dialogLayer.menuKey(secondary.first, 490, 290, 206, 56, color = Colors["#37474f"]) {
                closeDialog()
                secondary.second()
            }
        }


        fun openStats() {
            closeDialog()
            dimBackground()
            val best = views.loadBestRun()
            val lines = mutableListOf("Jugador: ${playerName ?: "sin nombre"}", "Runs ganadas: ${views.loadRunsWon()}")
            if (best == 0) {
                lines += "Todavía no has ganado un duelo."
            } else {
                lines += "Run más larga: $best ${if (best == 1) "duelo ganado" else "duelos ganados"}"
                lines += "Llegaste a: Ciclo ${RunStats.cycleReached(best)} - Fase ${RunStats.phaseReached(best)}/${Difficulty.PHASES_PER_CYCLE}"
                lines += "Jefes vencidos: ${RunStats.bossesBeaten(best)}"
            }
            dialogLayer.dialogPanel(220, 120, 520, 290)
            dialogLayer.pixelText("Estadísticas", 33) { position((960 - width.toInt()) / 2, 142) }
            lines.forEachIndexed { i, line ->
                dialogLayer.pixelText(line, 22) { position((960 - width.toInt()) / 2, 196 + i * 30) }
            }
            dialogLayer.menuKey("Cerrar", 380, 340, 200, 50, color = Colors["#37474f"]) { closeDialog() }
        }


        fun openNameDialog(onDone: () -> Unit) {
            var typed = playerName ?: ""

            fun render() {
                closeDialog()
                dimBackground()
                dialogLayer.dialogPanel(200, 50, 560, 430)
                dialogLayer.pixelText("Tu nombre", 33) { position((960 - width.toInt()) / 2, 68) }
                val shown = if (typed.length < PlayerName.MAX_LENGTH) "${typed}_" else typed
                dialogLayer.pixelText(shown, 33, Colors["#ffe082"]) { position((960 - width.toInt()) / 2, 120) }

                NAME_KEYS.forEachIndexed { i, ch ->
                    dialogLayer.menuKey(
                        ch.toString(),
                        NAME_GRID_X + (i % NAME_COLS) * NAME_KEY_STEP,
                        NAME_GRID_Y + (i / NAME_COLS) * NAME_KEY_STEP,
                        NAME_KEY_SIZE, NAME_KEY_SIZE
                    ) {
                        if (typed.length < PlayerName.MAX_LENGTH) {
                            typed += ch
                            render()
                        }
                    }
                }

                val rowY = NAME_GRID_Y + 4 * NAME_KEY_STEP + 10
                dialogLayer.menuKey("Borrar", NAME_GRID_X, rowY, 140, 50, enabled = typed.isNotEmpty(), color = Colors["#8d2f2f"]) {
                    typed = typed.dropLast(1)
                    render()
                }

                if (playerName != null) {
                    dialogLayer.menuKey("Cancelar", NAME_GRID_X + 161, rowY, 140, 50, color = Colors["#37474f"]) { closeDialog() }
                }
                dialogLayer.menuKey("Listo", NAME_GRID_X + 322, rowY, 140, 50, enabled = typed.isNotBlank(), color = Colors["#2e7d32"]) {
                    val clean = PlayerName.clean(typed)
                    if (clean != null) {
                        playerName = clean
                        views.savePlayerName(clean)
                        closeDialog()
                        onDone()
                    }
                }
            }

            render()
        }


        fun drawName() {
            nameLayer.removeChildren()
            nameLayer.plate("Jugador: ${playerName ?: "sin nombre"}", 20, 500).onClick { openNameDialog { drawName() } }
        }

        fun firstTime() = views.storage.getOrNull(TUTORIAL_DONE_KEY) == null


        fun startGame() {
            val saved = views.loadSavedRun()
            if (saved == null) {
                play(firstTime())
                return
            }
            openChoice(
                "Partida guardada",
                "Ciclo ${saved.cycle} - Fase ${saved.phase}/${Difficulty.PHASES_PER_CYCLE}",
                "Continuar" to { play(tutorial = false, resume = true) },
                "Nueva partida" to {
                    views.clearSavedRun()
                    play(firstTime())
                }
            )
        }


        fun startTutorial() {
            if (views.loadSavedRun() == null) {
                play(true)
                return
            }
            openChoice(
                "Empezar tutorial",
                "Se borra tu partida guardada.",
                "Tutorial" to {
                    views.clearSavedRun()
                    play(true)
                },
                "Cancelar" to { }
            )
        }

        val options = listOf<Pair<String, () -> Unit>>(
            "Jugar" to { startGame() },
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
            box.onOver { if (dialogLayer.numChildren == 0) select(i) }
            box.onClick { action() }
            MenuRow(box, normal, highlighted, x, y)
        }

        // Con un cuadro abierto, el teclado no mueve ni activa el menú de fondo
        keys {
            down(Key.UP) { if (dialogLayer.numChildren == 0) select((selected + rows.size - 1) % rows.size) }
            down(Key.DOWN) { if (dialogLayer.numChildren == 0) select((selected + 1) % rows.size) }
            down(Key.ENTER, Key.RETURN, Key.SPACE) { if (dialogLayer.numChildren == 0) options[selected].second() }
        }

        plate("Tutorial", 836, 500).onClick { startTutorial() }
        plate("Estadísticas", 432, 500).onClick { openStats() }

        addChild(nameLayer)
        addChild(dialogLayer)
        drawName()

        select(0)

        // La primera vez que se abre el juego se pide el nombre
        if (playerName == null) openNameDialog { drawName() }
    }
}

private fun Container.dialogPanel(x: Int, y: Int, w: Int, h: Int) {
    solidRect(w + 8, h + 8, Colors["#d9a441"]).position(x - 4, y - 4)   // borde dorado
    solidRect(w, h, Colors["#1b1b2f"]).position(x, y)
}

private fun Container.menuKey(
    label: String, x: Int, y: Int, w: Int, h: Int,
    enabled: Boolean = true, color: RGBA = Colors["#3949ab"], onTap: () -> Unit
): Container = container {
    position(x, y)
    solidRect(w, h, if (enabled) color else Colors["#555555"])
    pixelText(label, 22, if (enabled) Colors.WHITE else Colors["#bbbbbb"]) {
        position((w - width.toInt()) / 2, (h - height.toInt()) / 2)
    }
    if (enabled) onClick { onTap() }
}
