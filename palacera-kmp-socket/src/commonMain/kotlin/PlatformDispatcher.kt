import kotlinx.coroutines.CoroutineDispatcher

expect object PlatformDispatcher {
    val io: CoroutineDispatcher
}

expect val inAirplaneMode: Boolean
