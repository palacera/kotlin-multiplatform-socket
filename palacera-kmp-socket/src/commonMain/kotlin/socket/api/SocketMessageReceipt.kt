package socket.api

data class SocketMessageReceipt(
    val isConfirmed: (ByteArray) -> Boolean
)
