package socket.api.ping

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.scan

class PingFlow(
    val maxConsecutiveBeforeWaitingState: Int = 2,
    val maxUnconfirmedPingsBeforeError: Int = 10,
) {
    private val _pingEventFlow = MutableSharedFlow<PingEvent>()
    private val pingEventFlow = _pingEventFlow.asSharedFlow()

    private val _pingStateFlow = MutableSharedFlow<PingState>()
    val pingStateFlow = _pingStateFlow.asSharedFlow()

    suspend fun post(pingEvent: PingEvent) {
        _pingEventFlow.emit(pingEvent)
    }

    suspend fun observePingState() {
        pingEventFlow
            .scan(emptyList<PingEvent>()) { acc, value ->
                (acc + value).takeLast(maxUnconfirmedPingsBeforeError)
            }
            .collect { events ->
                when(val currentEvent = events.lastOrNull()) {
                    is PingEvent.Send -> handleSendEvent(events)
                    is PingEvent.Receive -> handleReceiveEvent()
                    is PingEvent.Error -> handleErrorEvent(currentEvent.exception)
                    else -> Unit
                }
            }
    }

    private suspend fun handleSendEvent(previousEvents: List<PingEvent>) {
        when {
            previousEvents.takeLast(maxUnconfirmedPingsBeforeError)
                .count { it is PingEvent.Send } == maxUnconfirmedPingsBeforeError  -> {
                handleErrorEvent(MaxUnconfirmedPingsException(
                    "Sent $maxUnconfirmedPingsBeforeError ping requests without a response."
                ))
            }
            previousEvents.takeLast(maxConsecutiveBeforeWaitingState)
                .count { it is PingEvent.Send } == maxConsecutiveBeforeWaitingState  -> {
                _pingStateFlow.emit(PingState.Waiting)
            }
            else -> _pingStateFlow.emit(PingState.Sent)
        }
    }

    private suspend fun handleReceiveEvent() {
        _pingStateFlow.emit(PingState.Confirmed)
    }

    private suspend fun handleErrorEvent(exception: Exception) {
        _pingStateFlow.emit(PingState.Error(exception))
    }
}
