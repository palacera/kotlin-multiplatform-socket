package socket.api.ping

sealed interface PingState {
    data object Sent: PingState
    data object Confirmed: PingState
}
