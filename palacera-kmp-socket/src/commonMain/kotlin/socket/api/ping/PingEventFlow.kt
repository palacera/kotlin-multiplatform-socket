package socket.api.ping

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.scan

class PingEventFlow(
    private val numUnresponsivePingsBeforeThrow: Int = 5,
) {
    private val _pingEventFlow = MutableSharedFlow<PingEvent>()
    val pingEventFlow = _pingEventFlow.asSharedFlow()

    suspend fun post(pingEvent: PingEvent) {
        _pingEventFlow.emit(pingEvent)
    }

    suspend fun observePingState(lambda: suspend (PingState) -> Unit) {
        pingEventFlow
            .scan(emptyList<PingEvent>()) { acc, value ->
                (acc + value).takeLast(numUnresponsivePingsBeforeThrow)
            }
            .collect { events ->
                when {
                    // throw exception on specified number of consecutive ping requests without response
                    events.count { it is PingEvent.Send } == numUnresponsivePingsBeforeThrow ->
                        throw Exception("Sent $numUnresponsivePingsBeforeThrow ping requests without response.")

                    // more that one consecutive send event so send sent state
                    events.takeLast(2).all { it is PingEvent.Send } ->
                        lambda(PingState.Sent)

                    // events are toggling correctly so send confirmed state
                    else ->
                        lambda(PingState.Confirmed)
                }
            }
    }
}
