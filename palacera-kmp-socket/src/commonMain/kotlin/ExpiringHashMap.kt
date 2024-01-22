import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock


class ExpiringHashMap<K, V>(
    private val defaultExpiresIn: Duration = 30.seconds,
    private val removalIterationDelay: Duration = 1.seconds
) {
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
    private val mutex = Mutex()

    private val now: Long get() =  Clock.System.now().toEpochMilliseconds()

    private val _expiringHashMapFlow: MutableStateFlow<Map<K, Item<V>>> = MutableStateFlow(emptyMap())

    val expiringHashMap: Map<K, V> get() = runBlocking { mutex.withLock { _expiringHashMapFlow.value.mapValues { it.value.value } } }

    private data class Item<V>(
        val value: V,
        val expiresIn: Duration,
        val timestamp: Long,
    )

    init {
        scope.launch {
            _expiringHashMapFlow.collect { map ->
                if (map.isNotEmpty()) {
                    delay(removalIterationDelay)
                    removeExpired()
                }
            }
        }
    }

    fun get(key: K): V? = runBlocking {
        mutex.withLock {
            _expiringHashMapFlow.value[key]?.value
        }
    }

    suspend fun put(key: K, value: V, expiresIn: Duration = defaultExpiresIn) {
        mutex.withLock {
            val currentMap = _expiringHashMapFlow.value.toMutableMap()
            currentMap[key] = Item(value, expiresIn, now)
            _expiringHashMapFlow.emit(currentMap)
        }
    }

    fun remove(key: K): V? = runBlocking {
        mutex.withLock {
            val currentMap = _expiringHashMapFlow.value.toMutableMap()
            val removedValue = currentMap.remove(key)?.value
            _expiringHashMapFlow.emit(currentMap)
            removedValue
        }
    }

    private suspend fun removeExpired() {
        mutex.withLock {
            val currentMap = _expiringHashMapFlow.value.toMutableMap()
            val expiredKeys = currentMap.filter { (now - it.value.timestamp) > it.value.expiresIn.inWholeMilliseconds }.keys
            expiredKeys.forEach { currentMap.remove(it) }
            _expiringHashMapFlow.emit(currentMap)
        }
    }

    fun clear() = runBlocking {
        mutex.withLock {
            _expiringHashMapFlow.emit(emptyMap())
        }
    }

    fun cancel() {
        clear()
        scope.cancel()
    }
}

//class ExpiringHashMap<K, V>(
//    private val defaultExpiresIn: Duration = 30.seconds,
//    private val removalIterationDelay: Duration = 1.seconds,
//) {
//    private val scope: CoroutineScope = CoroutineScope(PlatformDispatcher.io)
//
//    private val now: Long get() = Clock.System.now().toEpochMilliseconds()
//
//    private var isObservingExpiredItems = false
//
//    private val _expiringHashMapFlow: MutableStateFlow<HashMap<K, Item<V>>> = MutableStateFlow(hashMapOf())
//
//    val expiringHashMap get() = _expiringHashMapFlow.value.mapValues { it.value.value }
//
//    private data class Item<V>(
//        val value: V,
//        val expiresIn: Duration,
//        val timestamp: Long,
//    )
//
//    init {
//        scope.launch {
//            _expiringHashMapFlow.collect { map ->
//                if (map.isNotEmpty() && !isObservingExpiredItems) {
//                    isObservingExpiredItems = true
//                    while (isActive) {
//                        removeExpired()
//                        if (map.isEmpty()) break
//                        delay(removalIterationDelay)
//                    }
//                    isObservingExpiredItems = false
//                }
//            }
//        }
//    }
//
//    fun get(key: K): V? {
//        return _expiringHashMapFlow.value[key]?.value
//    }
//
//    private fun getExpiredItems(): Map<K, Item<V>> = _expiringHashMapFlow.value
//        .filter { (now - it.value.timestamp) > it.value.expiresIn.inWholeMilliseconds }
//
//    fun put(key: K, value: V, expiresIn: Duration = defaultExpiresIn) {
//        _expiringHashMapFlow.update { currentMap ->
//            HashMap(currentMap).apply {
//                this[key] = Item(value, expiresIn, now)
//            }
//        }
//    }
//
//    fun remove(key: K): V? {
//        var removedItem: V? = null
//        _expiringHashMapFlow.update {
//            it.also {
//                removedItem = it.remove(key)?.value
//            }
//        }
//        return removedItem
//    }
//
//    fun removeExpired() {
//        _expiringHashMapFlow.update { currentMap ->
//            val updatedMap = currentMap.toMutableMap()
//
//            getExpiredItems().keys.forEach { key ->
//                updatedMap.remove(key)
//            }
//
//            HashMap(updatedMap)
//        }
//    }
//
//    fun clear() {
//        _expiringHashMapFlow.update {
//            it.also { it.clear() }
//        }
//    }
//
//    fun cancel() {
//        clear()
//        scope.cancel()
//    }
//}
