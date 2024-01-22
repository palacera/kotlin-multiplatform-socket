package socket.api.pool

import MergedFlowRegistry
import UniqueCircularBuffer
import d
import dispatcher.Dispatcher
import e
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import socket.api.SocketConnection
import socket.api.TcpSocket
import socket.api.TcpSocketConfig
import socket.api.connection.SocketConnectionState
import socket.api.message.SocketMessageState
import tag

class SocketConnectionPool(
    private val channel: Channel<TcpSocket>,
    private val connectionPoolConfig: TcpSocketConnectionPoolConfig = TcpSocketConnectionPoolConfig(),
    private val dispatcher: Dispatcher
) {
    private val scope = CoroutineScope(dispatcher.io)
    private val supervisorScope: CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher.io)

    private val connectionBuffer = UniqueCircularBuffer<TcpSocket>(connectionPoolConfig.connectionBufferCapacity)

    private val numAsyncConnections = atomic(0)

    private val defaultSocketConfig by lazy { TcpSocketConfig() }

    private val _connectionStateFlow: MergedFlowRegistry<SocketConnectionState> by lazy { MergedFlowRegistry(dispatcher) }
    val connectionStateFlow = _connectionStateFlow.registeredFlows

    private val _messageStateFlow: MergedFlowRegistry<SocketMessageState> by lazy { MergedFlowRegistry(dispatcher) }
    val messageStateFlow = _connectionStateFlow.registeredFlows

    private fun createSocket(
        socketConnection: SocketConnection,
        socketConfig: TcpSocketConfig,
    ) =
        TcpSocket(
            socketConnection = socketConnection,
            socketConfig = socketConfig,
            dispatcher = dispatcher
        )

    fun connect(
        socketConnections: List<SocketConnection>,
        socketConfig: TcpSocketConfig = defaultSocketConfig,
    ) {
        socketConnections.forEach { socketConnection ->
            connect(socketConnection, socketConfig)
        }
    }

    fun connect(
        socketConnection: SocketConnection,
        socketConfig: TcpSocketConfig = defaultSocketConfig,
    )  {
        supervisorScope.launch {
            if (channel.isClosedForSend) cancel()
            receive()
            channel.send(createSocket(socketConnection, socketConfig))

            // connectSocket(socketConnection, socketConfig)
        }
    }

    private suspend fun connectSocket(
        socket: TcpSocket,
    ): TcpSocket {
        return socket
            .also {
                _connectionStateFlow.register(it.connectionStateFlow)
                _messageStateFlow.register(it.messageStateFlow)
            }
            .apply { connect() }
    }

//    private suspend fun connectSocket(
//        socketConnection: SocketConnection,
//        socketConfig: TcpSocketConfig,
//    ): TcpSocket {
//        return createSocket(socketConnection, socketConfig)
//            .also {
//                _connectionStateFlow.register(it.connectionStateFlow)
//                _messageStateFlow.register(it.messageStateFlow)
//            }
//            .apply { connect() }
//    }

    private fun disconnectSocket(socketConnection: TcpSocket) {
        _connectionStateFlow.unregister(socketConnection.connectionStateFlow)
        _messageStateFlow.unregister(socketConnection.messageStateFlow)

        // TODO close socket
    }

    suspend fun disconnectNext() {
        connectionBuffer.remove()?.also {
            disconnectSocket(it)
        }
    }

//    suspend fun disconnect(socket: SocketAddress) {
//        if (connectionBuffer.isNotEmpty()) {
//            val index = pool.indexOfFirst { it.socketAddress.uid == socket.uid }
//            if (index > -1) {
//                disconnectSocket(pool.removeAt(index))
//            }
//        }
//    }

    suspend fun disconnectAll() {
        connectionBuffer.all().forEach { connection ->
            disconnectSocket(connection)
        }
        connectionBuffer.clear()
    }

    @OptIn(DelicateCoroutinesApi::class)
    private suspend fun receive() {
        if (numAsyncConnections.value < connectionPoolConfig.maxAsyncConnections && !channel.isClosedForReceive) {
            numAsyncConnections.value++
            observeConnections()
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun sendWithDelay(socket: TcpSocket, delay: Duration = connectionPoolConfig.confirmConnectionInterval) = scope.launch {
        if (channel.isClosedForSend) cancel()
        delay(delay)
        channel.send(socket)
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun observeConnections() = scope.launch {
        for (socket in channel) {
            ensureActive()

            var socket: TcpSocket? = null
            try {
                socket = channel.receive()

                val isConnected = connectionBuffer.contains(socket) && socket.isConnected

                if (!isConnected) {
                    d(tag("pooltest")) { "NOT connected: ${socket.hashCode()}" }

                    connectSocket(socket)

                    // what for connection to be established
                    while(!socket.isConnected) {
                        delay(100.milliseconds)
                    }

                    connectionBuffer.remove()?.also { it.disconnect() }
                    connectionBuffer.add(socket)
                }

                if (socket.isConnected) {
                    d(tag("pooltest")) { "Connected: ${socket.hashCode()}" }
                } else {
                    d(tag("pooltest")) { "NOT Connected: ${socket.hashCode()}" }
                }

            } catch (e: ClosedReceiveChannelException) {
                e(tag("pooltest")) { e.message }
            } catch (e: ClosedSendChannelException) {
                e(tag("pooltest")) { e.message }
            } catch (e: Exception) {
                e(tag("pooltest")) { e.message }
            } finally {
                if (socket != null && !channel.isClosedForSend) {
                    sendWithDelay(socket)
                }
            }
        }
    }

    fun close() {
        this.channel.close()
        scope.cancel()
    }
}
