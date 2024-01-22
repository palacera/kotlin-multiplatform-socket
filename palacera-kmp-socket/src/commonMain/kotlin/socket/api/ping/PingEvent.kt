package socket.api.ping

sealed interface PingEvent {
    data object Send: PingEvent
    data object Receive: PingEvent
    data class Error(val exception: Exception): PingEvent
}
