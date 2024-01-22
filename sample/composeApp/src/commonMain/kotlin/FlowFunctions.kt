import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.scan

fun <T> Flow<T>.scanToArray(
    groupBy: (existing: T, new: T) -> Boolean,
): Flow<List<T>> = scan(listOf()) { acc, value ->
    acc.indexOfFirst { groupBy(it, value) }.let { index ->
        if (index != -1) {
            acc.toMutableList().apply { this[index] = value }
        } else {
            acc + value
        }
    }
}
