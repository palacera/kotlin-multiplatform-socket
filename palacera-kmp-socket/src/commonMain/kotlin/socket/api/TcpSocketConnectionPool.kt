package socket.api

import kotlin.jvm.JvmName
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update
import socket.adapter.SelectorManagerAdapter

class TcpSocketConnectionPool(
    private val config: TcpSocketConnectionPoolConfig = TcpSocketConnectionPoolConfig(),
) {
    private val pool = ArrayDeque<TcpSocket>(config.maxPoolSize)

    private val _connectionStateFlow: MutableStateFlow<List<StateFlow<SocketConnectionState>>> = MutableStateFlow(emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val connectionStateFlow = _connectionStateFlow.flatMapLatest { flows ->
        merge(*flows.toTypedArray())
    }

    private fun MutableStateFlow<List<StateFlow<SocketConnectionState>>>.addFlow(flow: StateFlow<SocketConnectionState>) =
        update { it + flow }

    private fun MutableStateFlow<List<StateFlow<SocketConnectionState>>>.removeFlow(flow: StateFlow<SocketConnectionState>) =
        update { it - flow }

    private val _messageStateFlow: MutableStateFlow<List<Flow<SocketMessageState>>> = MutableStateFlow(emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val messageStateFlow = _messageStateFlow.flatMapLatest { flows ->
        merge(*flows.toTypedArray())
    }

    @JvmName("addConnectionFlowx")
    private fun MutableStateFlow<List<Flow<SocketMessageState>>>.addFlow(flow: StateFlow<SocketMessageState>) =
        update { it + flow }

    @JvmName("addConnectionFlowxx")
    private fun MutableStateFlow<List<Flow<SocketMessageState>>>.removeFlow(flow: StateFlow<SocketMessageState>) =
        update { it - flow }

    private val selectorManager by lazy {
        SelectorManagerAdapter()
    }

    private inline fun <T> Collection<T>.atMaxCapacity(): Boolean = isNotEmpty() && size == config.maxPoolSize

    private inline fun ArrayDeque<TcpSocket>.hasSocket(socket: SocketAddress): Boolean =
        getSocket(socket) == null

    private inline fun ArrayDeque<TcpSocket>.getSocket(socket: SocketAddress): TcpSocket? =
        firstOrNull { it.socketAddress.uuid == socket.uuid }

    private inline fun ArrayDeque<TcpSocket>.addSocket(socket: TcpSocket) {
        addLast(socket)
    }

    private inline fun ArrayDeque<TcpSocket>.removeSocket(): TcpSocket {
        return when (config.dataStructure) {
            is SocketConnectionPoolDataStructure.Stack -> removeLast()
            is SocketConnectionPoolDataStructure.Queue -> removeFirst()
        }
    }

    suspend fun connect(
        socket: SocketAddress,
        pingRequest: PingRequest? = null,
    ) {
        if (pool.hasSocket(socket)) {
            if (pool.atMaxCapacity()) {
                disconnectSocket(pool.removeSocket())
            }
            pool.addSocket(connectSocket(socket, pingRequest))
        }
    }

    fun disconnectNext() {
        if (pool.isNotEmpty()) {
            disconnectSocket(pool.removeSocket())
        }
    }

    fun disconnect(socket: SocketAddress) {
        if (pool.isNotEmpty()) {
            val index = pool.indexOfFirst { it.socketAddress.uuid == socket.uuid }
            if (index > -1) {
                disconnectSocket(pool.removeAt(index))
            }
        }
    }

    fun disconnectAll() {
        pool.forEach { connection ->
            disconnectSocket(connection)
        }
        pool.clear()
    }

    suspend fun send(
        socket: SocketAddress,
        message: SocketMessage,
        messageReceipt: SocketMessageReceipt? = null,
    ) {
        if (pool.isNotEmpty()) {
            pool.getSocket(socket)?.send(message, messageReceipt)
        } else {
            // TODO connect and send when connected
        }
    }

    suspend fun receive(socket: SocketAddress) {
        if (pool.isNotEmpty()) {
            pool.getSocket(socket)?.receive()
        }
    }

    private suspend fun connectSocket(
        socket: SocketAddress,
        pingRequest: PingRequest? = null,
    ): TcpSocket {
        return TcpSocket(socket, config.socketConfig, selectorManager)
            .also {
                _connectionStateFlow.addFlow(it.connectionStateFlow)
                _messageStateFlow.addFlow(it.messageStateFlow)
            }
            .apply { connect(pingRequest) }
    }

    private fun disconnectSocket(socketConnection: TcpSocket) {
        _connectionStateFlow.removeFlow(socketConnection.connectionStateFlow)
        _messageStateFlow.removeFlow(socketConnection.messageStateFlow)
        socketConnection.close()
    }
}
