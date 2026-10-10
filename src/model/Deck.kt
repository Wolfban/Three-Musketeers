package model

import kotlin.random.Random

class Deck(private val random: Random = Random.Default) {
    private val cards = ArrayDeque<Card>()

    init { reset() }

    fun reset() {
        cards.clear()
        for (suit in Suit.entries) for (rank in Rank.entries) cards.add(Card(rank, suit))
        shuffle()
    }

    fun shuffle() {
        val shuffled = cards.shuffled(random)
        cards.clear()
        cards.addAll(shuffled)
    }

    fun draw(): Card = cards.removeFirst()
    fun draw(n: Int): List<Card> = List(n) { draw() }
    val remaining: Int get() = cards.size
}
