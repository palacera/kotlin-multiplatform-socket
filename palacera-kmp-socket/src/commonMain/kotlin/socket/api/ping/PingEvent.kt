package socket.api.ping

sealed interface PingEvent {
    data object Send: PingEvent
    data object Receive: PingEvent
}
