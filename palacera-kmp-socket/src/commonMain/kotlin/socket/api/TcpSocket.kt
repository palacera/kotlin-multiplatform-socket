package socket.api

import PlatformDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class TcpSocket(
    val socketAddress: SocketAddress,
    val config: TcpSocketConfig,
) {
    private val scope: CoroutineScope = CoroutineScope(PlatformDispatcher.io)

    private val connectionManager: TcpSocketConnectionManager by lazy {
        TcpSocketConnectionManager(this)
    }

    private val messageManager: SocketMessageManager by lazy {
        SocketMessageManager(connectionManager)
    }

    val connectionStateFlow = connectionManager.connectionStateFlow

    val messageStateFlow = messageManager.messageStateFlow

    suspend fun connect(
        pingRequest: PingRequest?,
    ) {
        try {
            connectionManager.apply {
                onConnection { messageManager.startReceivingMessages() }
                onDisconnection { messageManager.stopReceivingMessages() }
                onCancel { messageManager.cancel() }

                connect(pingRequest, messageStateFlow, messageManager.isReceivingMessages)
            }
        } catch (e: Exception) {

        }
    }

    suspend fun send(
        message: SocketMessage,
        messageReceipt: SocketMessageReceipt?,
    ) {
        messageManager.sendMessage(message, messageReceipt)
    }

    fun close() {
        scope.launch {
            messageManager.cancel()
            connectionManager.cancel()
        }
        scope.cancel()
    }
}
