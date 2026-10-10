package tests

import model.Deck
import kotlin.test.*

class DeckTest {
    @Test fun deckHas52UniqueCards() {
        val deck = Deck()
        val all = deck.draw(52)
        assertEquals(52, all.toSet().size)
        assertEquals(0, deck.remaining)
    }
}
