import kotlinx.atomicfu.atomic

class AtomicSet<T> {
    private val setRef = atomic(setOf<T>())

    val set: Set<T> get() = setRef.value

    val size: Int get() = set.size

    fun isEmpty(): Boolean = set.isEmpty()

    fun add(item: T) {
        while (true) {
            val currentSet = setRef.value
            val newSet = currentSet + item
            if (setRef.compareAndSet(currentSet, newSet)) {
                return
            }
        }
    }

    fun remove(item: T) {
        while (true) {
            val currentSet = setRef.value
            val newSet = currentSet - item
            if (setRef.compareAndSet(currentSet, newSet)) {
                return
            }
        }
    }

    fun clear() {
        setRef.value = setOf()
    }
}
