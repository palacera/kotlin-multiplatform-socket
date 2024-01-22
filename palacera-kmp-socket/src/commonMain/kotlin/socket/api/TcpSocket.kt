package socket.api

import dispatcher.Dispatcher
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import socket.api.connection.SocketConnectionState
import socket.api.connection.TcpSocketConnectionManager
import socket.api.message.SocketMessage
import socket.api.message.SocketMessageManager
import socket.api.message.SocketMessageReceipt
import socket.api.message.SocketMessageState
import socket.api.message.SocketMessageType
import socket.api.ping.PingEvent
import socket.api.ping.PingEventFlow
import socket.api.ping.PingRequest

class TcpSocket(
    val socketConnection: SocketConnection,
    val socketConfig: TcpSocketConfig,
    val dispatcher: Dispatcher,
) {
    private val pingEventFlow: PingEventFlow by lazy {
        PingEventFlow(10) // TODO get from config
    }

    private val connectionManager: TcpSocketConnectionManager by lazy {
        TcpSocketConnectionManager(this, pingEventFlow, dispatcher)
    }

    private val messageManager: SocketMessageManager by lazy {
        SocketMessageManager(connectionManager, dispatcher)
    }

    val connectionStateFlow = connectionManager.connectionStateFlow

    val messageStateFlow = messageManager.messageStateFlow

    private val isConnectedRef = atomic(false)
    val isConnected get() = isConnectedRef.value

    private fun setIsConnected(state: SocketConnectionState) {
        isConnectedRef.value = when {
            state is SocketConnectionState.Connected && socketConnection.pingRequest == null -> true
            state is SocketConnectionState.ConnectionConfirmed -> true
            else -> false
        }
    }

    private suspend fun observeConnection() {
        connectionManager.connectionStateFlow.collectLatest { state ->
            setIsConnected(state)

            when (state) {
                is SocketConnectionState.Connected -> {
                    messageManager.startReceivingMessages()
                }

                is SocketConnectionState.Disconnecting -> {
                    messageManager.stopReceivingMessages()
                }

                is SocketConnectionState.Error -> {
                    messageManager.cancel()
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
                    is SocketMessageState.MessageSent -> pingEventFlow.post(PingEvent.Send)
                    is SocketMessageState.MessageReceiptReceived -> pingEventFlow.post(PingEvent.Receive)
                    else -> Unit
                }
            }
    }

    suspend fun connect() = withContext(dispatcher.io) {

        launch { observeConnection() }

        if (socketConnection.pingRequest is PingRequest.Config) {
            launch { observePingEvents() }
        }

        connectionManager.connect(socketConnection.pingRequest)
    }

    suspend fun disconnect() {
        connectionManager.disconnect()
    }

    suspend fun send(
        message: SocketMessage,
        messageReceipt: SocketMessageReceipt?,
        messageType: SocketMessageType = SocketMessageType.Standard,
    ) {
        messageManager.sendMessage(message, messageReceipt, messageType)
    }

    fun close() {
        messageManager.cancel()

        CoroutineScope(dispatcher.io).launch {
            connectionManager.cancel()
        }
        //scope.cancel()
    }
}
