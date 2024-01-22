package socket.api.connection

sealed class SocketConnectionEvent {
    data object Connect: SocketConnectionEvent()
    data object Disconnect: SocketConnectionEvent()
    data object Dispose: SocketConnectionEvent()
}
