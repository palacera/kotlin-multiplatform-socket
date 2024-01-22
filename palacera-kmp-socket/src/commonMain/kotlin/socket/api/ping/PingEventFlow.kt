package socket.api.ping

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.scan
import logd

class PingEventFlow(
    private val numUnconfirmedPingsBeforeError: Int = 5,
) {
    private val _pingEventFlow = MutableSharedFlow<PingEvent>()
    val pingEventFlow = _pingEventFlow.asSharedFlow()

    suspend fun post(pingEvent: PingEvent) {
        _pingEventFlow.emit(pingEvent)
    }

    suspend fun observePingState(lambda: suspend (PingState) -> Unit) {
        pingEventFlow
            .scan(emptyList<PingEvent>()) { acc, value ->
                (acc + value).takeLast(numUnconfirmedPingsBeforeError)
            }
            .collect { events ->
                when {
                    // throw exception on specified number of consecutive ping requests without response
                    events.count { it is PingEvent.Send } == numUnconfirmedPingsBeforeError -> {
                        val exception = MaxUnconfirmedPingsException("Sent $numUnconfirmedPingsBeforeError ping requests without a response.")
                        lambda(PingState.Error(exception))
                    }

                    // more that one consecutive send event so send sent state
                    events.takeLast(2).all { it is PingEvent.Send } ->
                        lambda(PingState.Waiting)

                    // events are toggling correctly so send confirmed state
                    else ->
                        when (events.last()) {
                            PingEvent.Send -> lambda(PingState.Sent)
                            PingEvent.Receive -> lambda(PingState.Confirmed)
                        }
                }
            }
    }
}
