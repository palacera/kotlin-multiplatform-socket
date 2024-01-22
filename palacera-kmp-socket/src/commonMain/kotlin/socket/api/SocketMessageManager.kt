package socket.api

import AtomicMap
import AtomicSet
import ExpiringHashMap
import append
import d
import e
import kotlin.jvm.JvmInline
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tag


@JvmInline
value class Message(val content: ByteArray)




class SocketMessageManager(
    private val connectionManager: TcpSocketConnectionManager,
) {
    private val scope: CoroutineScope = CoroutineScope(PlatformDispatcher.io)
    private val mutex = Mutex()

    private val tag = tag("socket", "message")

    private val socketAddress = connectionManager.tcpSocket.socketAddress
    private val socketConfig = connectionManager.tcpSocket.config

    private val _messageStateFlow: MutableSharedFlow<SocketMessageState> =
        MutableSharedFlow()

    val messageStateFlow: SharedFlow<SocketMessageState> =
        _messageStateFlow.asSharedFlow()

    private suspend fun SocketMessageState.emit() =
        _messageStateFlow.emit(this)

    private var receiveMessagesJob: Job? = null

    private var messageCount = 1


    private val messagesAwaitingReceipt: ExpiringHashMap<Int, SocketMessageReceipt> by lazy {
        ExpiringHashMap(
            defaultExpiresIn = 10.seconds,
            removalIterationDelay = 1.seconds,
        )
    }

    suspend fun sendMessage(socketMessage: SocketMessage, messageReceipt: SocketMessageReceipt? = null) {
        try {
            val message = socketMessage.message(messageCount)
            val sentMessageId = message.hashCode()

            if (messageReceipt != null) {
                messagesAwaitingReceipt.put(sentMessageId, messageReceipt)
            }

            SocketMessageState.SendingMessage(socketAddress, message).emit()
            connectionManager.socket.send(message)
            d(tag.append("send")) { "(${socketAddress.address}) Sent #$sentMessageId: " + message.decodeToString() }
            SocketMessageState.MessageSent(socketAddress, sentMessageId).emit()

            if (messageReceipt != null) {
                d(tag.append("send", "awaiting")) { "(${socketAddress.address}) Awaiting receipt for message #$sentMessageId" }
                SocketMessageState.AwaitingMessageReceipt(socketAddress, sentMessageId)
            }
        } catch (e: Exception) {
            e(tag.append("send", "fail")) { "(${socketAddress.address}) ${e.message}" }
            SocketMessageState.Error(socketAddress, socketMessage.hashCode(), -1, e).emit()
        }
    }

    private val _isReceivingMessages = MutableStateFlow(false)
    val isReceivingMessages = _isReceivingMessages.asStateFlow()

    fun startReceivingMessages() {
        receiveMessagesJob = scope.launch {
            d(tag.append("receive", "start")) { "(${socketAddress.address}) Start receiving messages" }
            _isReceivingMessages.value = true
            while (isActive) {
                mutex.withLock {
                    receive()
                    delay(200.milliseconds) //socketConfig.receiveInterval)
                }
            }
        }
    }

    suspend fun stopReceivingMessages() {
        _isReceivingMessages.value = false
        d(tag.append("receive", "stop")) { "(${socketAddress.address}) Stop receiving messages" }
        receiveMessagesJob?.cancel()
    }

    fun cancel() {
        messagesAwaitingReceipt.clear()
        scope.cancel()
    }

    private suspend fun receive(): ByteArray {
        var message: ByteArray? = null
        return try {
            message = connectionManager.socket.receive()
            if (message.isNotEmpty()) {

                val messageReceipt = messagesAwaitingReceipt.findMessageReceipt(message)
                if (messageReceipt == null) {
                    d(tag.append("receive")) { "(${socketAddress.address}) Received: " + message.decodeToString() }
                    SocketMessageState.MessageReceived(socketAddress, message).emit()
                } else {
                    val sentMessageId = messageReceipt.key
                    d(tag.append("receive", "receipt")) {
                        "(${socketAddress.address}) Receipt #$sentMessageId: " + message.decodeToString()
                    }
                    messagesAwaitingReceipt.remove(sentMessageId)
                    SocketMessageState.MessageReceiptReceived(socketAddress, sentMessageId, message).emit()
                }
            }
            message
        } catch (e: Exception) {
            e(tag.append("receive", "fail")) { "(${socketAddress.address}) ${e.message}" }
            //SocketMessageState.Error(socketAddress, -1, message?.hashCode() ?: -1, e).emit()
            ByteArray(0)
        }
    }

    private fun ExpiringHashMap<Int, SocketMessageReceipt>.findMessageReceipt(content: ByteArray): Map.Entry<Int, SocketMessageReceipt>? =
        messagesAwaitingReceipt.expiringHashMap.entries.firstOrNull { it.value.isConfirmed(content) }

}
