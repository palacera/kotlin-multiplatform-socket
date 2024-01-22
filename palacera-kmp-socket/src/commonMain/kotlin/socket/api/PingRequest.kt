package socket.api

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

data class PingRequest(
    val requestMessage: SocketMessage,
    val pingInterval: Duration = 2.seconds,
    val pingTimeout: Duration = 8.seconds,
    val messageReceipt: SocketMessageReceipt,
) {
    val requestMessageId = requestMessage.hashCode()
}
