package socket.api

import kotlin.jvm.JvmInline

//sealed interface SocketMessage {
//    data class Message(val message: String) : SocketMessage
//
//    data object Empty : SocketMessage
//}

sealed class SocketMessageState(open val socketAddress: SocketAddress) {
    data class Idle(
        override val socketAddress: SocketAddress,
    ) : SocketMessageState(socketAddress)

    data class SendingMessage(
        override val socketAddress: SocketAddress,
        val message: ByteArray,
    ) : SocketMessageState(socketAddress) {
        val sentMessageId: Int = message.hashCode()
    }

    data class MessageSent(
        override val socketAddress: SocketAddress,
        val sentMessageId: Int,
    ) : SocketMessageState(socketAddress)

    data class AwaitingMessageReceipt(
        override val socketAddress: SocketAddress,
        val sentMessageId: Int,
    ) : SocketMessageState(socketAddress)

    data class MessageReceived(
        override val socketAddress: SocketAddress,
        val message: ByteArray,
    ) : SocketMessageState(socketAddress) {
        val receivedMessageId: Int = message.hashCode()
    }

    data class MessageReceiptReceived(
        override val socketAddress: SocketAddress,
        val sentMessageId: Int,
        val message: ByteArray,
    ) : SocketMessageState(socketAddress) {
        val receivedMessageId: Int = message.hashCode()
    }

    data class Error(
        override val socketAddress: SocketAddress,
        val sentMessageId: Int = -1,
        val receivedMessageId: Int = -1,
        val exception: Exception,
    ) : SocketMessageState(socketAddress)
}
