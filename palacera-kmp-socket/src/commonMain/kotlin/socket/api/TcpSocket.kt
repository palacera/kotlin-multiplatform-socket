package socket.api

import dispatcher.Dispatcher
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import socket.api.connection.SocketConnectionEvent
import socket.api.connection.SocketConnectionState
import socket.api.connection.SocketConnectionManager
import socket.api.message.SocketMessage
import socket.api.message.SocketMessageManager
import socket.api.message.SocketMessageReceipt
import socket.api.message.SocketMessageState
import socket.api.message.SocketMessageType
import socket.api.ping.PingEvent
import socket.api.ping.PingFlow
import socket.api.ping.PingRequest

class TcpSocket(
    val socketConnection: SocketConnection,
    val dispatcher: Dispatcher,
) : CoroutineScope {

    private val job = Job()
    override val coroutineContext = job + dispatcher.io

    private val pingFlow: PingFlow by lazy {
        PingFlow(
            maxConsecutiveBeforeWaitingState = 2,
            maxUnconfirmedPingsBeforeError = 10,
        ) // TODO get from config
    }

    private val connectionManager: SocketConnectionManager by lazy {
        SocketConnectionManager(this, pingFlow, dispatcher)
    }

    private val messageManager: SocketMessageManager by lazy {
        SocketMessageManager(connectionManager, dispatcher)
    }

    val connectionStateFlow = connectionManager.state
        //.stateIn(this, SharingStarted.WhileSubscribed(), SocketConnectionState.Disconnected(socketConnection.socketAddress))

    val messageStateFlow = messageManager.messageStateFlow

    private val isConnectedRef = atomic(false)

    val isConnected get() = connectionManager.isConnected

    val isDisconnected get() = connectionManager.isDisconnected

    val canAttemptConnection get() = connectionManager.canAttemptConnection

    private fun setIsConnected(state: SocketConnectionState) {
        isConnectedRef.value = when {
            state is SocketConnectionState.Connected && socketConnection.pingRequest !is PingRequest.None -> true
            state is SocketConnectionState.ConnectionConfirmed -> true
            else -> false
        }
    }

    private suspend fun observeConnection() {
        // collects on shared flow to avoid skipping states due to conflation
        connectionManager.state.collect { state ->
            setIsConnected(state)
            when (state) {
                is SocketConnectionState.Connected -> {
                    messageManager.startReceivingMessages()
                }

                is SocketConnectionState.Disconnected -> {
                    messageManager.stopReceivingMessages()
                }

                is SocketConnectionState.Error -> {
                    messageManager.stopReceivingMessages()
                }

                is SocketConnectionState.Disposed -> {
                    messageManager.dispose()
                }

                else -> Unit
            }
        }
    }

    private suspend fun observePingEvents() {
        messageManager.messageStateFlow
            .filter { it.messageType is SocketMessageType.Ping }
            .collect { messageState ->
                when (messageState) {
                    is SocketMessageState.MessageSent -> pingFlow.post(PingEvent.Send)
                    is SocketMessageState.MessageReceiptReceived -> pingFlow.post(PingEvent.Receive)
                    is SocketMessageState.Error -> pingFlow.post(PingEvent.Error(messageState.exception))
                    else -> Unit
                }
            }
    }

    init {
        launch { observeConnection() }

        if (socketConnection.pingRequest is PingRequest.Config) {
            launch { observePingEvents() }
        }
    }


    private var connectJob: Job? = null

    fun connect() {
        connectJob?.cancel()

        connectJob = launch {
            if (isDisconnected) {
                connectionManager.postEvent(SocketConnectionEvent.Connect)
            }
        }
    }

    suspend fun send(
        message: SocketMessage,
        messageReceipt: SocketMessageReceipt?,
        messageType: SocketMessageType = SocketMessageType.Standard,
    ) {
        messageManager.sendMessage(message, messageReceipt, messageType)
    }

    suspend fun disconnect() {
        connectionManager.postEvent(SocketConnectionEvent.Disconnect)
    }

    private val _isDisposed = atomic(false)
    val isDisposed: Boolean get() = _isDisposed.value

    suspend fun dispose() {
        _isDisposed.update { true }
        messageManager.dispose()
        connectionManager.postEvent(SocketConnectionEvent.Dispose)
        job.cancel()
    }
}
