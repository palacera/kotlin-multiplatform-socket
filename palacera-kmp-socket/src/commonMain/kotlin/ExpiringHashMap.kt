import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

class ExpiringHashMap<K, V>(
    private val defaultExpiresIn: Duration = 30.seconds,
    private val removalIterationDelay: Duration = 1.seconds,
) {
    private val scope: CoroutineScope = CoroutineScope(PlatformDispatcher.io)

    private val now: Long get() = Clock.System.now().toEpochMilliseconds()

    private var isObservingExpiredItems = false

    private val _expiringHashMapFlow: MutableStateFlow<HashMap<K, Item<V>>> = MutableStateFlow(hashMapOf())

    val expiringHashMap get() = _expiringHashMapFlow.value.mapValues { it.value.value }

    private data class Item<V>(
        val value: V,
        val expiresIn: Duration,
        val timestamp: Long,
    )

    init {
        scope.launch {
            _expiringHashMapFlow.collect { map ->
                if (map.isNotEmpty() && !isObservingExpiredItems) {
                    isObservingExpiredItems = true
                    while (isActive) {
                        removeExpired()
                        if (map.isEmpty()) break
                        delay(removalIterationDelay)
                    }
                    isObservingExpiredItems = false
                }
            }
        }
    }

    fun get(key: K): V? {
        return _expiringHashMapFlow.value[key]?.value
    }

    private fun getExpiredItems(): Map<K, Item<V>> = _expiringHashMapFlow.value
        .filter { (now - it.value.timestamp) > it.value.expiresIn.inWholeMilliseconds }

    fun put(key: K, value: V, expiresIn: Duration = defaultExpiresIn) {
        _expiringHashMapFlow.update { currentMap ->
            HashMap(currentMap).apply {
                this[key] = Item(value, expiresIn, now)
            }
        }
    }

    fun remove(key: K): V? {
        var removedItem: V? = null
        _expiringHashMapFlow.update {
            it.also {
                removedItem = it.remove(key)?.value
            }
        }
        return removedItem
    }

    fun removeExpired() {
        _expiringHashMapFlow.update { currentMap ->
            val updatedMap = currentMap.toMutableMap()

            getExpiredItems().keys.forEach { key ->
                updatedMap.remove(key)
            }

            HashMap(updatedMap)
        }
    }

    fun clear() {
        _expiringHashMapFlow.update {
            it.also { it.clear() }
        }
    }

    fun cancel() {
        clear()
        scope.cancel()
    }
}
