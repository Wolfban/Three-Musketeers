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
private const val HANDS_PANEL_ART_W = 270
private const val HANDS_PANEL_ART_H = 150
private const val HANDS_ROW_STEP = 26
private const val HANDS_CARDS_X = 440
private const val HANDS_CARD_STEP = 60
private const val HANDS_CARD_W = 80
private const val HANDS_CARD_H = 112
private const val HANDS_DIM_ALPHA = 150

private fun hand(vararg cards: Pair<Rank, Suit>) = cards.map { Card(it.first, it.second) }

private val HAND_EXAMPLES = mapOf(
    // Solo con comodines: las cartas repetidas salen de Cambiapalos o Todos As
    HandCategory.FLUSH_FIVE to hand(Rank.ACE to Suit.SPADES, Rank.ACE to Suit.SPADES, Rank.ACE to Suit.SPADES, Rank.ACE to Suit.SPADES, Rank.ACE to Suit.SPADES),
    HandCategory.FLUSH_HOUSE to hand(Rank.QUEEN to Suit.HEARTS, Rank.QUEEN to Suit.HEARTS, Rank.QUEEN to Suit.HEARTS, Rank.SEVEN to Suit.HEARTS, Rank.SEVEN to Suit.HEARTS),
    HandCategory.FIVE_OF_A_KIND to hand(Rank.ACE to Suit.SPADES, Rank.ACE to Suit.HEARTS, Rank.ACE to Suit.DIAMONDS, Rank.ACE to Suit.CLUBS, Rank.ACE to Suit.SPADES),
    HandCategory.ROYAL_FLUSH to hand(Rank.ACE to Suit.SPADES, Rank.KING to Suit.SPADES, Rank.QUEEN to Suit.SPADES, Rank.JACK to Suit.SPADES, Rank.TEN to Suit.SPADES),
    HandCategory.STRAIGHT_FLUSH to hand(Rank.NINE to Suit.HEARTS, Rank.EIGHT to Suit.HEARTS, Rank.SEVEN to Suit.HEARTS, Rank.SIX to Suit.HEARTS, Rank.FIVE to Suit.HEARTS),
    HandCategory.FOUR_OF_A_KIND to hand(Rank.KING to Suit.CLUBS, Rank.KING to Suit.DIAMONDS, Rank.KING to Suit.HEARTS, Rank.KING to Suit.SPADES, Rank.THREE to Suit.DIAMONDS),
    HandCategory.FULL_HOUSE to hand(Rank.QUEEN to Suit.SPADES, Rank.QUEEN to Suit.HEARTS, Rank.QUEEN to Suit.DIAMONDS, Rank.SEVEN to Suit.CLUBS, Rank.SEVEN to Suit.HEARTS),
    HandCategory.FLUSH to hand(Rank.ACE to Suit.DIAMONDS, Rank.JACK to Suit.DIAMONDS, Rank.EIGHT to Suit.DIAMONDS, Rank.FIVE to Suit.DIAMONDS, Rank.TWO to Suit.DIAMONDS),
    HandCategory.STRAIGHT to hand(Rank.TEN to Suit.CLUBS, Rank.NINE to Suit.DIAMONDS, Rank.EIGHT to Suit.SPADES, Rank.SEVEN to Suit.HEARTS, Rank.SIX to Suit.CLUBS),
    HandCategory.THREE_OF_A_KIND to hand(Rank.NINE to Suit.SPADES, Rank.NINE to Suit.HEARTS, Rank.NINE to Suit.CLUBS, Rank.KING to Suit.DIAMONDS, Rank.FOUR to Suit.SPADES),
    HandCategory.TWO_PAIR to hand(Rank.JACK to Suit.HEARTS, Rank.JACK to Suit.CLUBS, Rank.FIVE to Suit.SPADES, Rank.FIVE to Suit.DIAMONDS, Rank.ACE to Suit.CLUBS),
    HandCategory.ONE_PAIR to hand(Rank.TEN to Suit.HEARTS, Rank.TEN to Suit.SPADES, Rank.KING to Suit.CLUBS, Rank.SIX to Suit.DIAMONDS, Rank.THREE to Suit.HEARTS),
    HandCategory.HIGH_CARD to hand(Rank.ACE to Suit.SPADES, Rank.JACK to Suit.DIAMONDS, Rank.EIGHT to Suit.CLUBS, Rank.SIX to Suit.HEARTS, Rank.THREE to Suit.CLUBS)
)

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

internal fun Container.handsTable(panelArt: Bitmap, cardImages: Map<Card, Bitmap>, screen: Rectangle, onClose: () -> Unit) {
    solidRect(screen.width, screen.height, RGBA(0, 0, 0, 170)).position(screen.x, screen.y).onClick { onClose() }
    val w = HANDS_PANEL_ART_W * ART_SCALE.toInt()
    val x = (960 - w) / 2
    val y = (540 - HANDS_PANEL_ART_H * ART_SCALE.toInt()) / 2
    framePanel(panelArt, HANDS_PANEL_ART_W, HANDS_PANEL_ART_H).position(x, y)
    pixelText("Manos de póker", 22) { position((960 - width.toInt()) / 2, y + 22) }

    val categories = HandCategory.entries.reversed()
    val rows = container()
    val example = container()
    val cardsX = x + HANDS_CARDS_X
    val cardsW = 4 * HANDS_CARD_STEP + HANDS_CARD_W
    val centerX = cardsX + cardsW / 2

    fun show(selected: HandCategory) {
        rows.removeChildren()
        categories.forEachIndexed { i, category ->
            val rowY = y + 66 + i * HANDS_ROW_STEP
            val active = category == selected
            val hit = rows.solidRect(HANDS_CARDS_X - 38, HANDS_ROW_STEP - 2, if (active) RGBA(255, 224, 138, 40) else Colors.TRANSPARENT)
                .position(x + 28, rowY - 6)
            hit.onOver { if (!active) show(category) }
            hit.onClick { show(category) }
            rows.pixelText(categoryName(category), 16, if (active) Colors["#ffe08a"] else Colors.WHITE) { position(x + 36, rowY) }
            rows.pixelText("${Scoring.baseChipsOf(category)} fichas", 16, Colors["#81c784"]) { position(x + 285, rowY) }
            rows.pixelText("x${Scoring.multiplierOf(category)}", 16, Colors["#ffd54f"]) { position(x + 392, rowY) }
        }

        val cards = HAND_EXAMPLES.getValue(selected)
        val score = Scoring.score(cards)
        example.removeChildren()
        example.pixelText(categoryName(selected), 22, Colors["#ffe08a"]) { position(centerX - width.toInt() / 2, y + 110) }
        cards.forEachIndexed { i, card ->
            val bitmap = cardImages[card] ?: return@forEachIndexed
            val cardX = cardsX + i * HANDS_CARD_STEP
            example.solidRect(HANDS_CARD_W + 4, HANDS_CARD_H + 4, Colors["#2b1608"]).position(cardX - 2, y + 148)
            example.image(bitmap) {
                smoothing = false
                position(cardX, y + 150)
            }
            if (card !in score.scoringCards) example.solidRect(HANDS_CARD_W, HANDS_CARD_H, RGBA(0, 0, 0, HANDS_DIM_ALPHA)).position(cardX, y + 150)
        }
        example.pixelText("(${score.baseChips} + ${score.cardChips}) x ${score.multiplier} = ${score.total}", 16) {
            position(centerX - width.toInt() / 2, y + 286)
        }
    }

    show(categories.first())
    pixelText("Toca una mano para ver un ejemplo  -  H o Esc para cerrar", 11, Colors["#d9b26a"]) {
        position((960 - width.toInt()) / 2, y + 400)
    }
}
