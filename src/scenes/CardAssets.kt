
package scenes

import model.Card
import model.Rank
import model.Suit

fun Card.imagePath(): String {

    val rankCode = when (rank) {
        Rank.JACK -> "J"
        Rank.QUEEN -> "Q"
        Rank.KING -> "K"
        Rank.ACE -> "A"
        else -> rank.value.toString()
    }

    val suitCode = when (suit) {
        Suit.CLUBS -> "T" //Treboles
        Suit.DIAMONDS -> "D" //Diamantes
        Suit.HEARTS -> "C" //Corazones
        Suit.SPADES -> "P" //Picas
    }

    val suitFolder = when (suit) {
        Suit.CLUBS -> "treboles"
        Suit.DIAMONDS -> "diamantes"
        Suit.HEARTS -> "corazones"
        Suit.SPADES -> "picas"
    }

    return "cards/$suitFolder/$rankCode$suitCode.png"
}
