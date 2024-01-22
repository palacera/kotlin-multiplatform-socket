package socket.api.ping

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import socket.api.message.SocketMessage
import socket.api.message.SocketMessageReceipt

sealed class PingRequest {
    data object None : PingRequest()

    data class Config(
        val requestMessage: SocketMessage,
        val messageReceipt: SocketMessageReceipt,
        val pingInterval: Duration = 1.seconds,
        val pingTimeout: Duration = 8.seconds
    ) : PingRequest()
}
