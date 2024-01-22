import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class UniqueCircularBuffer<T>(private val capacity: Int) {

    private val set = HashSet<T>(capacity)
    private val queue = ArrayDeque<T>(capacity)
    private val mutex = Mutex()

    init {
        if (capacity < 1) {
            throw IllegalArgumentException("Capacity must be at least 1.")
        }
    }

    fun all(): List<T> = queue.toList()

    suspend fun add(item: T): Boolean = mutex.withLock {

        if (set.contains(item)) return false

        if (queue.size == capacity) {
            throw Exception("Buffer is full. Cannot add item.")
        }

        queue.addLast(item)
        set.add(item)

        return true
    }

    suspend fun remove(): T? = mutex.withLock {
        queue.takeIf { it.size == capacity }
            ?.removeFirstOrNull()
            ?.also { set.remove(it) }
    }

    suspend fun remove(item: T): Boolean = mutex.withLock {
        return queue.remove(item)
            .takeIf { true }
            ?.also { set.remove(item) } ?: false
    }

    suspend fun clear() = mutex.withLock {
        queue.clear()
        set.clear()
    }

    suspend fun contains(item: T): Boolean = mutex.withLock {
        set.contains(item)
    }

    suspend fun size(): Int = mutex.withLock {
        queue.size
    }

    suspend fun isFull(): Boolean = mutex.withLock {
        queue.size == capacity
    }
}
