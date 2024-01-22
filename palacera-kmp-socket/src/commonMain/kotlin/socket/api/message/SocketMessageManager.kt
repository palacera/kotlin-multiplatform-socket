package socket.api.message

import ExpiringHashMap
import append
import d
import dispatcher.Dispatcher
import e
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import socket.api.connection.SocketConnectionManager
import tag


data class AwaitingMessage(
    val messageType: SocketMessageType,
    val messageReceipt: SocketMessageReceipt,
)

class SocketMessageManager(
    private val connectionManager: SocketConnectionManager,
    val dispatcher: Dispatcher,
) : CoroutineScope {

    private val job = Job()
    override val coroutineContext = job + dispatcher.io

    private val mutex = Mutex()

    private val tag = tag("socket", "message")

    private val socketAddress = connectionManager.tcpSocket.socketConnection.socketAddress
    private val socketConfig = connectionManager.tcpSocket.socketConnection.socketConfig

    private val _messageStateFlow: MutableSharedFlow<SocketMessageState> =
        MutableSharedFlow()

    val messageStateFlow: SharedFlow<SocketMessageState> =
        _messageStateFlow.asSharedFlow()

    private suspend fun SocketMessageState.emit() =
        _messageStateFlow.emit(this)

    private var receiveMessagesJob: Job? = null

    private var messageCount = 1

    private val messagesAwaitingReceipt: ExpiringHashMap<Int, AwaitingMessage> by lazy {
        ExpiringHashMap(
            defaultExpiresIn = 10.seconds,
            removalIterationDelay = 1.seconds,
        )
    }

    suspend fun sendMessage(
        socketMessage: SocketMessage,
        messageReceipt: SocketMessageReceipt? = null,
        messageType: SocketMessageType,
    ) {
        try {
            val message = socketMessage.message(messageCount)
            val sentMessageId = message.hashCode()

            if (messageReceipt != null) {
                messagesAwaitingReceipt.put(
                    sentMessageId, AwaitingMessage(
                        messageType = messageType,
                        messageReceipt = messageReceipt,
                    )
                )
            }

            SocketMessageState.SendingMessage(socketAddress, messageType, message).emit()
            connectionManager.socket.send(message)
            d(tag.append("send")) { "(${socketAddress.address}) Sent #$sentMessageId: " + message.decodeToString() }
            SocketMessageState.MessageSent(socketAddress, messageType, sentMessageId).emit()

            if (messageReceipt != null) {
                d(tag.append("send", "awaiting")) { "(${socketAddress.address}) Awaiting receipt for message #$sentMessageId" }
                SocketMessageState.AwaitingMessageReceipt(socketAddress, messageType, sentMessageId).emit()
            }
        } catch (e: Exception) {
            e(tag.append("send", "fail")) { "(${socketAddress.address}) ${e.message}" }
            SocketMessageState.Error(socketAddress, messageType, socketMessage.hashCode(), -1, e).emit()
        }
    }

    fun startReceivingMessages() {
        receiveMessagesJob = launch {
            d(tag.append("receive", "start")) { "(${socketAddress.address}) Start receiving messages" }
            while (isActive) {
                mutex.withLock {
                    try {
                        receive()
                        delay(200.milliseconds) //socketConfig.receiveInterval)
                    } catch (e: CancellationException) {
                        throw e // rethrowing so not caught with Exception so that coroutine cancellation remains cooperative
                    } catch (e: Exception) {
                        cancel()
                    }
                }
            }
        }
    }

    suspend fun stopReceivingMessages() {
        d(tag.append("receive", "stop")) { "(${socketAddress.address}) Stop receiving messages" }
        receiveMessagesJob?.cancel()
    }

    private suspend fun receive(): ByteArray {
        var message: ByteArray? = null
        var messageType: SocketMessageType = SocketMessageType.Standard
        return try {
            message = connectionManager.socket.receive()
            if (message.isNotEmpty()) {

                val awaitingMessage = messagesAwaitingReceipt.findMessageReceipt(message)
                if (awaitingMessage == null) {
                    d(tag.append("receive")) { "(${socketAddress.address}) Received: " + message.decodeToString() }
                    SocketMessageState.MessageReceived(socketAddress, messageType, message).emit()
                } else {
                    val sentMessageId = awaitingMessage.key
                    d(tag.append("receive", "receipt")) {
                        "(${socketAddress.address}) Receipt #$sentMessageId: " + message.decodeToString()
                    }
                    messagesAwaitingReceipt.remove(sentMessageId)
                    messageType = awaitingMessage.value.messageType
                    SocketMessageState.MessageReceiptReceived(socketAddress, messageType, sentMessageId, message).emit()
                }
            }
            message
        } catch (e: Exception) {
            e(tag.append("receive", "fail")) { "(${socketAddress.address}) ${e.message}" }
            SocketMessageState.Error(socketAddress, messageType, -1, message?.hashCode() ?: -1, e).emit()
            throw e
        }
    }

    private fun ExpiringHashMap<Int, AwaitingMessage>.findMessageReceipt(content: ByteArray): Map.Entry<Int, AwaitingMessage>? =
        messagesAwaitingReceipt.expiringHashMap.entries.firstOrNull { it.value.messageReceipt.isConfirmed(content) }

    fun dispose() {
        messagesAwaitingReceipt.clear()
        job.cancel()
    }
}
