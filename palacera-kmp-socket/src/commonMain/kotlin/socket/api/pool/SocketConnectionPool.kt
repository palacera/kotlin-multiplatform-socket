package socket.api.pool

import AtomicMap
import MergedFlowRegistry
import UniqueCircularBuffer
import dispatcher.Dispatcher
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.RENDEZVOUS
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import logd
import socket.api.SocketAddress
import socket.api.SocketConnection
import socket.api.SocketConnectionException
import socket.api.SocketException
import socket.api.SocketPoolException
import socket.api.TcpSocket
import socket.api.connection.ConnectionLog
import socket.api.connection.SocketConnectionState
import socket.api.connection.messageOrDefault
import socket.api.message.SocketMessageState
import socket.api.ping.PingRequest


class SocketConnectionPool(
    private val channel: Channel<TcpSocket>,
    private val connectionPoolConfig: TcpSocketConnectionPoolConfig = TcpSocketConnectionPoolConfig(),
    private val dispatcher: Dispatcher,
) : CoroutineScope {

    private val job = Job()
    override val coroutineContext = job + dispatcher.io

    private val socketsMap = atomic(mapOf<SocketAddress, TcpSocket>())
    private val connectionJobs = atomic(mapOf<SocketAddress, Job>())
    private val connectionBuffer = UniqueCircularBuffer<TcpSocket>(connectionPoolConfig.connectionBufferCapacity)

    private val numAsyncConnections = atomic(0)

    private val _connectionStateFlow: MergedFlowRegistry<SocketConnectionState> by lazy { MergedFlowRegistry(dispatcher) }
    val connectionStateFlow = _connectionStateFlow.registeredFlows

    private val _messageStateFlow: MergedFlowRegistry<SocketMessageState> by lazy { MergedFlowRegistry(dispatcher) }
    val messageStateFlow = _connectionStateFlow.registeredFlows

    // TODO can move this to a delegate class
    private val logChannel by lazy {
        SocketConnectionPoolLogChannel(
            dispatcher = dispatcher,
        )
    }

    init {
        launch {
            connectionStateFlow.collect {
                val socket = getSocket(it.socketAddress)
                    //?: throw SocketPoolException("Socket not found for ${it.socketAddress}")

                if (socket != null) {
                    when (it) {
                        is SocketConnectionState.Connected -> whenConnectedState(socket)
                        is SocketConnectionState.Disconnecting -> whenDisconnectingState(socket)
                        is SocketConnectionState.Disconnected -> whenDisconnectedState(socket)
                        is SocketConnectionState.Disposed -> whenDisposedState(socket)
                        is SocketConnectionState.Error -> whenErrorState(socket, it.exception)
                        else -> Unit
                    }
                }
            }
        }
    }

    private fun log(connectionLog: SocketConnectionPoolLog) {
        logChannel.log(connectionLog)
    }

    private suspend fun whenConnectedState(socket: TcpSocket) {
        addToConnectionBuffer(socket)
    }

    private suspend fun whenDisconnectingState(socket: TcpSocket) {
        cleanUpSocketForDisconnectingState(socket)
    }

    private suspend fun whenDisconnectedState(socket: TcpSocket) {
        //_connectionStateFlow.unregister(socket.connectionStateFlow)
    }

    private suspend fun whenDisposedState(socket: TcpSocket) {
        cleanUpSocketForDisposedState(socket)
    }

    private suspend fun whenErrorState(socket: TcpSocket, exception: Exception) {
        delay(100.milliseconds)
        cleanUpSocketForDisconnectingState(socket)
    }

    private fun getSocket(socketAddress: SocketAddress): TcpSocket? =
        socketsMap.value[socketAddress]

    private fun createSocket(
        socketConnection: SocketConnection,
    ) =
        TcpSocket(
            socketConnection = socketConnection,
            dispatcher = dispatcher
        )

    private fun getSocket(
        socketConnection: SocketConnection,
    ): TcpSocket = socketsMap.value[socketConnection.socketAddress] ?: createSocket(socketConnection).also { socket ->
        socketsMap.update { map -> map + (socketConnection.socketAddress to socket)}
    }

    fun connectSocket(
        socketConnection: SocketConnection,
    ) {
        val socket = getSocket(socketConnection)
        receiveSockets()
        launch { channel.send(socket) }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun receiveSockets() = launch {
        if (numAsyncConnections.value < connectionPoolConfig.maxAsyncConnections && !channel.isClosedForReceive) {
            numAsyncConnections.value++

            supervisorScope { // TODO verify this is the right place to do this since this can be called multiple times
                for (socket in channel) {
                    ensureActive()
                    //if (!socket.isDisposed) {
                        val connectionJob = attemptConnection(socket)
                        connectionJobs.update { map -> map + (socket.socketConnection.socketAddress to connectionJob)}
                    //}
                }
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun attemptConnection(socket: TcpSocket): Job = launch {
        try {
            val canAttemptConnection = socket.canAttemptConnection
                && (!connectionBuffer.contains(socket)
                || connectionBuffer.size() < connectionPoolConfig.connectionBufferCapacity)

            if (canAttemptConnection) {
                withTimeout(socket.socketConnection.socketConfig.connectionTimeout) {
                    _connectionStateFlow.register(socket.connectionStateFlow)
                    _messageStateFlow.register(socket.messageStateFlow)
                    delay(50.milliseconds)
                    socket.connect()
                }
            }
        } catch (e: CancellationException) {
            throw e // rethrowing so not caught with Exception so that coroutine cancellation remains cooperative
        } catch (e: ClosedReceiveChannelException) {
            //e(tag.append("error")) { e.message }
        } catch (e: ClosedSendChannelException) {
            //e(tag.append("error")) { e.message }
        } catch (e: Exception) {
            //e(tag.append("error")) { e.message }
        } finally {
            // TODO maybe need to unregister flows if exception is thrown, might need to do other cleanup as well
            //  but remember it will try to connect again so maybe no need to do anything

            //emitConnectionWithDelay(socket)
            cancel()
            //cancelConnectionJob(socket)
        }
    }

    suspend fun disconnectSocket(socketConnection: SocketConnection)  {
        getSocket(socketConnection.socketAddress)?.also { socket ->
            socket.disconnect()
        }
    }

    private suspend fun disconnectNextSocket() {
        connectionBuffer.remove()?.also {
            disconnectSocket(it.socketConnection)
        }
    }

    suspend fun disconnectAllSockets() {
        socketsMap.value.forEach { (_, socket) ->
            disconnectSocket(socket.socketConnection)
        }
        cleanUpAllSockets()
    }

    suspend fun disposeSocket(socketConnection: SocketConnection) {
        getSocket(socketConnection.socketAddress)?.also { socket ->
            socket.dispose() // handles socket disconnection as well
        }
    }

    suspend fun disposeAllSockets() {
        socketsMap.value.forEach { (_, socket) ->
            disposeSocket(socket.socketConnection)
        }
        cleanUpAllSockets()
    }

    private suspend fun cleanUpSocketForDisconnectingState(socket: TcpSocket) {
        connectionBuffer.remove(socket)
        cancelConnectionJob(socket)
        _messageStateFlow.unregister(socket.messageStateFlow)
    }

    private suspend fun cleanUpSocketForDisposedState(socket: TcpSocket) {
        _connectionStateFlow.unregister(socket.connectionStateFlow)
    }

    private fun addToConnectionBuffer(socket: TcpSocket) = launch {
        connectionBuffer.remove()?.also { it.disconnect() }
        connectionBuffer.add(socket)
    }

    private fun cancelConnectionJob(socket: TcpSocket) {
        val key = socket.socketConnection.socketAddress
        connectionJobs.value[key]?.cancel()
        connectionJobs.update { map -> map - key }
    }

    private suspend fun cleanUpAllSockets() {
        connectionJobs.value = mapOf()
        connectionBuffer.clear()
        socketsMap.value = mapOf()
    }

    fun dispose() {
        launch {
            disposeAllSockets()
            channel.close()
            _connectionStateFlow.dispose()
            _messageStateFlow.dispose()
            job.cancel()
        }
    }
}
