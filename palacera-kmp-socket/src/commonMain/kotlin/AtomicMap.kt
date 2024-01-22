import kotlinx.atomicfu.atomic

class AtomicMap<K, V> {
    private val mapRef = atomic(mapOf<K, V>())

    val map: Map<K, V> get() = mapRef.value

    val size: Int get() = map.size

    fun isEmpty(): Boolean = map.isEmpty()

    fun get(key: K): V? = map[key]

    fun put(key: K, value: V) {
        while (true) {
            val currentMap = mapRef.value
            val newMap = currentMap + Pair(key, value)
            if (mapRef.compareAndSet(currentMap, newMap)) {
                return
            }
        }
    }

    fun remove(key: K) {
        while (true) {
            val currentMap = mapRef.value
            val newMap = currentMap - key
            if (mapRef.compareAndSet(currentMap, newMap)) {
                return
            }
        }
    }

    fun clear() {
        mapRef.value = mapOf()
    }
}
