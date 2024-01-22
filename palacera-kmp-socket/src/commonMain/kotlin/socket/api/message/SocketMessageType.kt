package socket.api.message

sealed interface SocketMessageType {
    data object Standard : SocketMessageType
    data object Ping : SocketMessageType
}
