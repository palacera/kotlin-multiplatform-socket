package socket.api.pool

import dispatcher.Dispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import socket.adapter.SelectorManagerAdapter
import socket.api.SocketAddress
import socket.api.SocketConnection
import socket.api.message.SocketMessage
import socket.api.message.SocketMessageReceipt
import socket.api.TcpSocket

class TcpSocketConnectionPool(
    private val config: TcpSocketConnectionPoolConfig = TcpSocketConnectionPoolConfig(),
    val dispatcher: Dispatcher,

) {
    private val supervisorScope: CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher.io)

    private val pool = ArrayDeque<TcpSocket>(config.maxPoolSize)

//    private val _connectionStateFlow: MutableStateFlow<List<SharedFlow<SocketConnectionState>>> = MutableStateFlow(emptyList())
//
//    @OptIn(ExperimentalCoroutinesApi::class)
//    val connectionStateFlow = _connectionStateFlow.flatMapLatest { flows ->
//        merge(*flows.toTypedArray())
//    }.shareIn(
//        CoroutineScope(dispatcher.io),
//        SharingStarted.WhileSubscribed()
//    )
//
//    private fun MutableStateFlow<List<SharedFlow<SocketConnectionState>>>.addFlow(flow: SharedFlow<SocketConnectionState>) =
//        update { it + flow }
//
//    private fun MutableStateFlow<List<SharedFlow<SocketConnectionState>>>.removeFlow(flow: SharedFlow<SocketConnectionState>) =
//        update { it - flow }


//    private val _messageStateFlow: MutableStateFlow<List<SharedFlow<SocketMessageState>>> = MutableStateFlow(emptyList())
//
//    @OptIn(ExperimentalCoroutinesApi::class)
//    val messageStateFlow = _messageStateFlow.flatMapLatest { flows ->
//        merge(*flows.toTypedArray())
//    }
//
//    @JvmName("addConnectionFlowx")
//    private fun MutableStateFlow<List<SharedFlow<SocketMessageState>>>.addFlow(flow: SharedFlow<SocketMessageState>) =
//        update { it + flow }
//
//    @JvmName("addConnectionFlowxx")
//    private fun MutableStateFlow<List<SharedFlow<SocketMessageState>>>.removeFlow(flow: SharedFlow<SocketMessageState>) =
//        update { it - flow }

    private val selectorManager by lazy {
        SelectorManagerAdapter()
    }

    private inline fun <T> Collection<T>.atMaxCapacity(): Boolean = isNotEmpty() && size == config.maxPoolSize

    private inline fun ArrayDeque<TcpSocket>.hasSocket(socket: SocketAddress): Boolean =
        getSocket(socket) == null

    private inline fun ArrayDeque<TcpSocket>.getSocket(socket: SocketAddress): TcpSocket? =
        firstOrNull { it.socketConnection.socketAddress.uid == socket.uid }

    private inline fun ArrayDeque<TcpSocket>.addSocket(socket: TcpSocket) {
        addLast(socket)
    }

    private inline fun ArrayDeque<TcpSocket>.removeSocket(): TcpSocket {
        return when (config.dataStructure) {
            is SocketConnectionPoolDataStructure.Stack -> removeLast()
            is SocketConnectionPoolDataStructure.Queue -> removeFirst()
        }
    }

    fun connect(
        socketConnections: List<SocketConnection>,
    ) {
        socketConnections.forEach { socketConnection ->
            connect(socketConnection)
        }
    }

    fun connect(
        socketConnection: SocketConnection,
    )  {
        supervisorScope.launch {
            connectSocket(socketConnection)
        }
    }

    fun cancel() {
        supervisorScope.cancel()
    }

//        if (pool.hasSocket(socketConnection.socketAddress)) {
//            if (pool.atMaxCapacity()) {
//                disconnectSocket(pool.removeSocket())
//            }
//            pool.addSocket(connectSocket(socketConnection))
//        }
 //   }

    fun disconnectNext() {
        if (pool.isNotEmpty()) {
            disconnectSocket(pool.removeSocket())
        }
    }

    fun disconnect(socket: SocketAddress) {
        if (pool.isNotEmpty()) {
            val index = pool.indexOfFirst { it.socketConnection.socketAddress.uid == socket.uid }
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
        pool.getSocket(socket)?.send(message, messageReceipt)

//        if (pool.isNotEmpty()) {
//            pool.getSocket(socket)?.send(message, messageReceipt)
//        } else {
//            // TODO connect and send when connected
//        }
    }

    suspend fun receive(socket: SocketAddress) {
//        if (pool.isNotEmpty()) {
//            pool.getSocket(socket)?.receive()
//        }
    }

    private fun createSocket(socketConnection: SocketConnection) =
        TcpSocket(
            socketConnection = socketConnection,
            socketConfig = config.socketConfig,
            dispatcher = dispatcher
        )

    private suspend fun connectSocket(
        socketConnection: SocketConnection,
    ): TcpSocket {
        return createSocket(socketConnection)
            .also {
//                _connectionStateFlow.addFlow(it.connectionStateFlow)
//                _messageStateFlow.addFlow(it.messageStateFlow)
            }
            .apply { connect() }
    }

    private fun disconnectSocket(socketConnection: TcpSocket) {
//        _connectionStateFlow.removeFlow(socketConnection.connectionStateFlow)
//        _messageStateFlow.removeFlow(socketConnection.messageStateFlow)
        socketConnection.close()
    }
}
