package socket.api

sealed class SocketMessageEvent {
    data class SendMessage(val message: SocketMessage, val messageReceipt: SocketMessageReceipt? = null): SocketMessageEvent()
    data object StartReceivingMessages: SocketMessageEvent()
    data object StopReceivingMessages: SocketMessageEvent()
    data object Cancel: SocketMessageEvent()
}
