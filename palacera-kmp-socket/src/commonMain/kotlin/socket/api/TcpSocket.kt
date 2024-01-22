package socket.api

import PlatformDispatcher
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import socket.adapter.SelectorManagerAdapter
import socket.adapter.TcpSocketAdapter

class TcpSocket(
    val socketAddress: SocketAddress,
    val config: TcpSocketConfig,
    private val selectorManager: SelectorManagerAdapter,
) : TcpSocketConnectable {

    private lateinit var messageReceiver: Job

    private val scope = CoroutineScope(PlatformDispatcher.io)

    private val _connectionStateFlow: MutableStateFlow<SocketConnectionState> =
        MutableStateFlow(SocketConnectionState.Disconnected(socketAddress))

    val connectionStateFlow: StateFlow<SocketConnectionState> =
        _connectionStateFlow.asStateFlow()

    private suspend fun SocketConnectionState.emit() =
        _connectionStateFlow.emit(this)

    private val _messageStateFlow: MutableStateFlow<SocketMessageState> =
        MutableStateFlow(SocketMessageState.Idle(socketAddress))

    val messageStateFlow: StateFlow<SocketMessageState> =
        _messageStateFlow.asStateFlow()

    private suspend fun SocketMessageState.emit() =
        _messageStateFlow.emit(this@emit)

    // TODO inject this
    private val socket by lazy {
        TcpSocketAdapter(
            socketAddress = socketAddress,
            config = config,
            selectorManager = selectorManager.resolve()
        )
    }

    override suspend fun connect(
        pingRequest: PingRequest?,
    ) {
        try {
            //scope.async {
            establishConnection()
            //}.await()

            println("Receiving messages...")
            receiveMessages()

            if (pingRequest != null) {
                verifyConnection(pingRequest)
            }
        } catch (e: Exception) {

        }
    }

    private suspend fun establishConnection() {
        SocketConnectionState.Connecting(socketAddress).emit()

        val maxAttempts = if (config.maxConnectionAttempts == -1) Int.MAX_VALUE else config.maxConnectionAttempts

        run repeatBlock@{
            repeat(maxAttempts) { attempt ->
                try {
                    println("Attempting to connect...")
                    socket.connect()
                    SocketConnectionState.Connected(socketAddress).emit()
                    return@repeatBlock
                } catch (e: Exception) {
                    SocketConnectionState.Connecting(socketAddress, e).emit()

                    if (attempt == maxAttempts - 1) {
                        SocketConnectionMaxAttemptsException("Failed to connect after $maxAttempts attempt(s)").also {
                            SocketConnectionState.Error(socketAddress, it).emit()
                            throw it
                        }
                    } else {
                        println("Connection failed, retrying after delay...")
                        delay(config.connectionRetryInterval)
                    }
                }
            }
        }
    }

    private suspend fun verifyConnection(
        pingRequest: PingRequest,
    ) {
        SocketConnectionState.ConnectionVerifying(socketAddress).emit()

        CoroutineScope(PlatformDispatcher.io).launch {
            launch {
                messageStateFlow.filter { it is SocketMessageState.MessageReceiptConfirmed }.collectLatest {
                    val sentMessageId = (it as SocketMessageState.MessageReceiptConfirmed).sentMessageId
                    if (pingRequest.requestMessageId == sentMessageId) {
                        SocketConnectionState.ConnectionVerified(socketAddress).emit()
                    }
                }
            }

            launch {
                while (isActive) {

                    // delay emitting ConnectionVerifying state to allow time to be verified
                    // this prevents a quick flash between ConnectionVerified and ConnectionVerifying states
                    val verifyingStateJob = launch {
                        delay(pingRequest.pingInterval.coerceIn(500.milliseconds, 2.seconds))
                        SocketConnectionState.ConnectionVerifying(socketAddress).emit()
                    }

                    // send ping with timeout for when expected response to be received before another ping is sent
                    val pingTimeout = withTimeoutOrNull(pingRequest.pingTimeout) {
                        send(pingRequest.requestMessage, pingRequest.messageReceipt)
                    }

                    if (pingTimeout != null) {
                        // received ping response so cancel ConnectionVerifying state emission
                        verifyingStateJob.cancel()

                        // ensure that the ping interval is at least the receive interval to prevent orphan pings
                        delay(pingRequest.pingInterval.coerceAtLeast(config.receiveInterval))
                    }
                }
            }
        }
    }

    private val receiptManager by lazy { SocketMessageReceiptManager() }

    override suspend fun send(
        message: SocketMessage,
        messageReceipt: SocketMessageReceipt?,
    ) {
        try {
            socket.send(message)
            SocketMessageState.MessageSent(socketAddress, message).emit()

            if (messageReceipt != null) {
                receiptManager.awaitReceipts { receiptManager ->
                    messageStateFlow.filter { it is SocketMessageState.MessageReceived }.collectLatest { messageState ->
                        val receivedMessage = (messageState as SocketMessageState.MessageReceived)
                        receiptManager.getConfirmedReceipt(receivedMessage.message.content)?.also {
                            receiptManager.remove(it)
                            SocketMessageState.MessageReceiptConfirmed(socketAddress, message.hashCode(), message).emit()
                        }
                    }
                }
                receiptManager.add(messageReceipt)
            }

            println("message.sent: " + message.content.decodeToString())
        } catch (e: Exception) {
            SocketMessageState.Error(socketAddress, message.hashCode(), -1, e).emit()
        }
    }

    private fun receiveMessages() {
        messageReceiver = scope.launch {
            while (isActive) {
                receive()
                delay(config.receiveInterval)
            }
        }
    }

    override suspend fun receive(): SocketMessage {
        var message: SocketMessage? = null
        return try {
            message = socket.receive()
            if (message.content.isNotEmpty()) {
                SocketMessageState.MessageReceived(socketAddress, message).emit()
                println("message.received: ${message.content.decodeToString()}")
            }
            message
        } catch (e: Exception) {
            println("message.error: ${e.message}")
            SocketMessageState.Error(socketAddress, -1, message?.hashCode() ?: -1, e).emit()
            SocketMessage(ByteArray(0))
        }
    }

    override fun close() {
        if (::messageReceiver.isInitialized) {
            messageReceiver.cancel()
        }

        receiptManager.cancel()
        socket.close()
    }
}
