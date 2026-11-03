package scenes

import korlibs.image.bitmap.*
import korlibs.image.color.*
import korlibs.korge.input.*
import korlibs.korge.view.*
import korlibs.math.geom.*
import model.*
import kotlin.math.sin

internal const val TUTORIAL_SEED = 30
internal const val TUTORIAL_DONE_KEY = "tutorialVisto"

private const val TUTORIAL_PANEL_ART_W = 210
private const val TUTORIAL_PANEL_ART_H = 64
private const val TUTORIAL_PANEL_X = 252
private const val TUTORIAL_PANEL_TOP_Y = 12
private const val TUTORIAL_PANEL_BOTTOM_Y = 350
private const val HANDS_PANEL_ART_W = 200
private const val HANDS_PANEL_ART_H = 110

internal enum class TutorialWait { NEXT, SELECT, DISCARD, FIGHT }

internal class TutorialStep(
    val text: String,
    val wait: TutorialWait,
    val panelAtTop: Boolean,
    val highlights: List<Rectangle>
)

internal class DuelTutorial(
    private val layer: Container,
    private val panelArt: Bitmap,
    private val screenBounds: () -> Rectangle,
    private val stepAt: (Int) -> TutorialStep?,
    private val onFinish: () -> Unit
) {
    private var index = 0
    var active = true
        private set

    private val current: TutorialStep? get() = if (active) stepAt(index) else null

    fun allows(wait: TutorialWait): Boolean = !active || current?.wait == wait

    fun waiting(wait: TutorialWait): Boolean = active && current?.wait == wait

    fun advance() {
        index++
        if (stepAt(index) == null) finish() else render()
    }

    fun finish() {
        if (!active) return
        active = false
        layer.removeChildren()
        onFinish()
    }

    fun render() {
        val step = current ?: return
        layer.removeChildren()
        if (step.wait == TutorialWait.NEXT) {
            val screen = screenBounds()
            layer.solidRect(screen.width, screen.height, Colors.TRANSPARENT).position(screen.x, screen.y).onClick { advance() }
        }

        val glow = layer.container()
        step.highlights.forEach { glow.outline(it) }
        var t = 0.0
        glow.addUpdater { dt ->
            t += dt.inWholeMilliseconds / 1000.0
            alpha = 0.55 + 0.45 * sin(t * 6)
        }

        val panelY = if (step.panelAtTop) TUTORIAL_PANEL_TOP_Y else TUTORIAL_PANEL_BOTTOM_Y
        layer.framePanel(panelArt, TUTORIAL_PANEL_ART_W, TUTORIAL_PANEL_ART_H).position(TUTORIAL_PANEL_X, panelY)
        layer.pixelText(step.text, 22) { position(TUTORIAL_PANEL_X + 48, panelY + 30) }
        if (step.wait == TutorialWait.NEXT) {
            layer.pixelText("Toca para continuar", 22, Colors["#ffe08a"]) {
                position(TUTORIAL_PANEL_X + TUTORIAL_PANEL_ART_W * ART_SCALE.toInt() - 54 - width.toInt(), panelY + 136)
            }
        }
        layer.pixelText("Saltar tutorial", 22, Colors["#ff9e8a"]) { position(TUTORIAL_PANEL_X + 54, panelY + 136) }.onClick { finish() }
    }

    private fun Container.outline(r: Rectangle) {
        val c = Colors["#ffd54f"]
        solidRect(r.width, 3.0, c).position(r.x, r.y)
        solidRect(r.width, 3.0, c).position(r.x, r.y + r.height - 3)
        solidRect(3.0, r.height, c).position(r.x, r.y)
        solidRect(3.0, r.height, c).position(r.x + r.width - 3, r.y)
    }
}

internal fun Container.handsTable(panelArt: Bitmap, screen: Rectangle, onClose: () -> Unit) {
    solidRect(screen.width, screen.height, RGBA(0, 0, 0, 170)).position(screen.x, screen.y).onClick { onClose() }
    val w = HANDS_PANEL_ART_W * ART_SCALE.toInt()
    val x = (960 - w) / 2
    val y = (540 - HANDS_PANEL_ART_H * ART_SCALE.toInt()) / 2
    framePanel(panelArt, HANDS_PANEL_ART_W, HANDS_PANEL_ART_H).position(x, y)
    pixelText("Manos de póker", 22) { position((960 - width.toInt()) / 2, y + 22) }
    HandCategory.entries.reversed().forEachIndexed { i, category ->
        val rowY = y + 62 + i * 22
        pixelText(categoryName(category), 16) { position(x + 36, rowY) }
        pixelText("${Scoring.baseChipsOf(category)} fichas", 16, Colors["#81c784"]) { position(x + w - 190, rowY) }
        pixelText("x${Scoring.multiplierOf(category)}", 16, Colors["#ffd54f"]) { position(x + w - 70, rowY) }
    }
    pixelText("H o Esc para cerrar", 11, Colors["#d9b26a"]) { position((960 - width.toInt()) / 2, y + 290) }
}
