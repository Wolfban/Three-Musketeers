package model


data class Debuff(val suit: Suit? = null, val ranks: Set<Rank> = emptySet()) {

    fun affects(card: Card): Boolean = card.suit == suit || card.rank in ranks

    companion object {
        val NONE = Debuff()
    }
}
