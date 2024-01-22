package socket.api

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

data class PingRequest(
    val requestMessage: SocketMessage,
    val messageReceipt: SocketMessageReceipt,
    val pingInterval: Duration = 1.seconds,
    val pingTimeout: Duration = 8.seconds,
) {
    val requestMessageId=  requestMessage.hashCode()
}
