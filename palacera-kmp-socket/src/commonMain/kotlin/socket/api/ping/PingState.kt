package socket.api.ping

sealed interface PingState {
    data object Sent: PingState
    data object Waiting: PingState
    data object Confirmed: PingState
    data class Error(val exception: Exception): PingState
}
