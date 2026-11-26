package model

import kotlin.random.Random

/**
 * Los 6 comodines del juego.
 * needsCard: después de elegirlo, el jugador toca una carta de su mano para aplicarlo.
 * file: nombre del arte en resources/comodines/<file>.ase
 */
enum class JokerType(
    val title: String,
    val file: String,
    val description: String,
    val needsCard: Boolean
) {
    CHANGE_SUIT("Cambiapalos", "cambiapalos", "Cambia el palo de una carta de tu mano.", true),
    TO_ACE("Todos As", "todos_as", "Convierte una carta de tu mano en un as.", true),
    STEAL_SWAP("Intercambio", "intercambio", "Da una carta y roba una al azar del rival.", true),
    RECOVER("Recuperar", "recuperar", "Cambia una carta por tu último descarte.", true),
    KINGS_AS_ACES("Reyes Ases", "reyes_ases", "Los reyes cuentan como ases en el duelo.", false),
    VISION("Futuro", "futuro", "Mira la próxima carta del mazo.", false)
}

/** true si el comodín ya se aplicó en este duelo. */
fun Duel.jokerUsed(type: JokerType): Boolean = when (type) {
    JokerType.CHANGE_SUIT -> suitChangeUsed
    JokerType.TO_ACE -> aceChangeUsed
    JokerType.STEAL_SWAP -> stealSwapUsed
    JokerType.RECOVER -> recoverDiscardUsed
    JokerType.KINGS_AS_ACES -> kingsAsAcesActive
    JokerType.VISION -> visionUsed
}

/** Por qué no se puede usar un comodín ahora mismo, o null si se puede. */
fun Duel.jokerBlockedReason(type: JokerType): String? = when {
    isFinished -> "El duelo ya terminó."
    jokerUsed(type) -> "Ya lo usaste en este duelo."
    type == JokerType.RECOVER && discardPile.isEmpty() -> "Primero tienes que descartar."
    type == JokerType.VISION && peekNextCard() == null -> "No quedan cartas en el mazo."
    else -> null
}

fun Duel.canUseJoker(type: JokerType): Boolean = jokerBlockedReason(type) == null

/**
 * Comodines que el jugador tiene en la run. Máximo 2, sin repetidos.
 * Son de un solo uso: al usarlo, el comodín sale del inventario y libera su espacio.
 */
data class JokerInventory(val jokers: List<JokerType> = emptyList()) {

    init {
        require(jokers.size <= MAX_SIZE) { "El inventario admite como máximo $MAX_SIZE comodines" }
        require(jokers.toSet().size == jokers.size) { "El inventario no puede repetir comodines" }
    }

    val isFull: Boolean get() = jokers.size >= MAX_SIZE

    operator fun contains(type: JokerType): Boolean = type in jokers

    fun add(type: JokerType): JokerInventory {
        require(!isFull) { "El inventario está lleno" }
        require(type !in jokers) { "Ya tienes ese comodín" }
        return copy(jokers = jokers + type)
    }

    /** Suelta old y toma new en su lugar (para cuando el inventario está lleno). */
    fun replace(old: JokerType, new: JokerType): JokerInventory {
        require(old in jokers) { "No tienes ese comodín" }
        require(new !in jokers) { "Ya tienes ese comodín" }
        return copy(jokers = jokers.map { if (it == old) new else it })
    }

    /** Gasta un comodín (al usarlo sale del inventario). */
    fun remove(type: JokerType): JokerInventory {
        require(type in jokers) { "No tienes ese comodín" }
        return copy(jokers = jokers - type)
    }

    /** Oferta de la tienda: comodines al azar que el jugador todavía no tiene. */
    fun shopOffer(random: Random = Random.Default, count: Int = SHOP_CHOICES): List<JokerType> =
        JokerType.entries.filter { it !in jokers }.shuffled(random).take(count)

    fun encode(): String = jokers.joinToString(",") { it.name }

    companion object {
        const val MAX_SIZE = 2
        const val SHOP_CHOICES = 3

        /** Lee el inventario guardado. Ignora nombres desconocidos, repetidos y lo que pase del máximo. */
        fun decode(text: String?): JokerInventory {
            if (text.isNullOrBlank()) return JokerInventory()
            val types = text.split(",")
                .mapNotNull { name -> JokerType.entries.firstOrNull { it.name == name.trim() } }
                .distinct()
                .take(MAX_SIZE)
            return JokerInventory(types)
        }
    }
}

/** Comodines desbloqueados en la colección: se guardan por nombre, separados por coma. */
object JokerUnlocks {
    fun encode(unlocked: Set<JokerType>): String =
        unlocked.sortedBy { it.ordinal }.joinToString(",") { it.name }

    fun decode(text: String?): Set<JokerType> =
        text.orEmpty().split(",")
            .mapNotNull { name -> JokerType.entries.firstOrNull { it.name == name.trim() } }
            .toSet()
}
