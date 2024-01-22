import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

actual object PlatformDispatcher {
    actual val io: CoroutineDispatcher = Dispatchers.Default
}

actual val inAirplaneMode: Boolean
    get() = false
