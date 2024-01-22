package socket.api.message

import socket.api.SocketAddress

sealed class SocketMessageState(
    open val socketAddress: SocketAddress,
    open val messageType: SocketMessageType = SocketMessageType.Standard,
) {
    data class Idle(
        override val socketAddress: SocketAddress,
        override val messageType: SocketMessageType,
    ) : SocketMessageState(socketAddress, messageType)

    data class SendingMessage(
        override val socketAddress: SocketAddress,
        override val messageType: SocketMessageType,
        val message: ByteArray,
    ) : SocketMessageState(socketAddress) {
        val sentMessageId: Int = message.hashCode()
    }

    data class MessageSent(
        override val socketAddress: SocketAddress,
        override val messageType: SocketMessageType,
        val sentMessageId: Int,
    ) : SocketMessageState(socketAddress)

    data class AwaitingMessageReceipt(
        override val socketAddress: SocketAddress,
        override val messageType: SocketMessageType,
        val sentMessageId: Int,
    ) : SocketMessageState(socketAddress)

    data class MessageReceived(
        override val socketAddress: SocketAddress,
        override val messageType: SocketMessageType,
        val message: ByteArray,
    ) : SocketMessageState(socketAddress) {
        val receivedMessageId: Int = message.hashCode()
    }

    data class MessageReceiptReceived(
        override val socketAddress: SocketAddress,
        override val messageType: SocketMessageType,
        val sentMessageId: Int,
        val message: ByteArray,
    ) : SocketMessageState(socketAddress) {
        val receivedMessageId: Int = message.hashCode()
    }

    data class Error(
        override val socketAddress: SocketAddress,
        override val messageType: SocketMessageType,
        val sentMessageId: Int = -1,
        val receivedMessageId: Int = -1,
        val exception: Exception,
    ) : SocketMessageState(socketAddress)
}
