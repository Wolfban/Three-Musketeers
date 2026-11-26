package model


object Combinations {
    fun <T> of(items: List<T>, k: Int): List<List<T>> {
        val result = mutableListOf<List<T>>()
        fun pick(start: Int, current: List<T>) {
            if (current.size == k) {
                result.add(current)
                return
            }
            for (i in start until items.size) pick(i + 1, current + items[i])
        }
        pick(0, emptyList())
        return result
    }
}
