package socket.api.message

data class SocketMessage(
    val message: (messageCount: Int) -> ByteArray,
)

data class SocketMessageReceipt(
    val isConfirmed: (ByteArray) -> Boolean
)
