package socket.api

data class SocketMessage(
    val message: (messageCount: Int) -> ByteArray
) {
    val messageId = message.hashCode()
}

data class SocketMessageReceipt(
    val isConfirmed: (ByteArray) -> Boolean
)
