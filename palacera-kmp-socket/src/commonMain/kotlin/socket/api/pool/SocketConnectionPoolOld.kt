package socket.api.pool

import AtomicMap
import MergedFlowRegistry
import UniqueCircularBuffer
import append
import d
import dispatcher.Dispatcher
import e
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import socket.api.SocketAddress
import socket.api.SocketConnection
import socket.api.TcpSocket
import socket.api.connection.SocketConnectionState
import socket.api.message.SocketMessageState
import tag


//class MyClass : CoroutineScope {
//    private val job = Job()
//    override val coroutineContext = job + Dispatchers.Default
//
//    private val _sharedFlow = MutableSharedFlow<Int>()
//    val sharedFlow = _sharedFlow.asSharedFlow()
//
//    private val channel = Channel<Int>()
//
//    private val handleReceivedValueJob = SupervisorJob()
//
//    init {
//        launch { // Am I effected by the failure?
//            sharedFlow.collect { value ->
//                channel.send(value)
//            }
//        }
//
//        repeat(3) {
//            launch {// Am I effected by the failure?
//                for (value in channel) {
//                    handleReceivedValue(value)
//                }
//            }
//        }
//    }
//
//    fun startSomeWork(value: Int) {
//        launch { // Am I affected by the failure?
//            _sharedFlow.emit(value)
//        }
//    }
//
//    private fun handleReceivedValue(value: Int) {
//        launch(handleReceivedValueJob + Dispatchers.Default) { // Am I effected by the failure?
//            println("Handled value: $value")
//            doOtherStuff(this)
//        }
//    }
//
//    private fun doOtherStuff(scope: CoroutineScope) {
//        scope.launch { // Am I effected by the failure?
//            // This Fails
//        }
//    }
//
//    fun cleanUp() {
//        job.cancel()
//        handleReceivedValueJob.cancel()
//        channel.close()
//    }
//}


class SocketConnectionPoolOld(
    private val channel: Channel<TcpSocket>,
    private val connectionPoolConfig: TcpSocketConnectionPoolConfig = TcpSocketConnectionPoolConfig(),
    private val dispatcher: Dispatcher,
) : CoroutineScope {

    private val tag = tag("socket", "pool")

    private val job = Job()
    private val connectJob = SupervisorJob()

    override val coroutineContext = job + dispatcher.io

    private val connectionBuffer = UniqueCircularBuffer<TcpSocket>(connectionPoolConfig.connectionBufferCapacity)

    private val numAsyncConnections = atomic(0)

    private val _socketConnectionFlow: MutableSharedFlow<TcpSocket> = MutableSharedFlow()
    val socketConnectionFlow: SharedFlow<TcpSocket> = _socketConnectionFlow.asSharedFlow()

    private val _connectionStateFlow: MergedFlowRegistry<SocketConnectionState> by lazy { MergedFlowRegistry(dispatcher) }
    val connectionStateFlow = _connectionStateFlow.registeredFlows

    private val _messageStateFlow: MergedFlowRegistry<SocketMessageState> by lazy { MergedFlowRegistry(dispatcher) }
    val messageStateFlow = _connectionStateFlow.registeredFlows

    private val connectionJobs = AtomicMap<SocketAddress, Job>()

    private val socketsMap = AtomicMap<SocketAddress, TcpSocket>()

    init {
        launch {
            _socketConnectionFlow.collect {
                d(tag.append("connect", "send")) { "(${it.socketConnection.socketAddress.address}) Sending connection" }
                receive()
                channel.send(it)
            }
        }
    }

    private fun createSocket(
        socketConnection: SocketConnection,
    ) =
        TcpSocket(
            socketConnection = socketConnection,
            dispatcher = dispatcher
        )

    fun connect(
        socketConnection: SocketConnection,
    ) {
        val socket = getSocket(socketConnection)
        launch { _socketConnectionFlow.emit(socket) }
    }

    private fun getSocket(
        socketConnection: SocketConnection,
    ) : TcpSocket = socketsMap.get(socketConnection.socketAddress) ?: createSocket(socketConnection).also {
        socketsMap.put(socketConnection.socketAddress, it)
    }

    private fun cancelConnectionJob(socket: TcpSocket) {
        val key = socket.socketConnection.socketAddress
        val job = connectionJobs.get(key)
        job?.cancel()
        connectionJobs.remove(key)
    }

    private suspend fun disconnectSocket(socket: TcpSocket) {
        connectionBuffer.remove(socket)
        cancelConnectionJob(socket)

        socket.disconnect()

        while (!socket.isDisconnected) {
            delay(50.milliseconds)
        }

        socketsMap.remove(socket.socketConnection.socketAddress)

        //socket.dispose()
        unregisterFlows(socket)
    }

    suspend fun disconnectNext() {
        connectionBuffer.remove()?.also {
            disconnectSocket(it)
        }
    }

    suspend fun disconnectAll() {
        connectionBuffer.all().forEach { socket ->
            disconnectSocket(socket)
        }
        connectionBuffer.clear()
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun receive() {
        if (numAsyncConnections.value < connectionPoolConfig.maxAsyncConnections && !channel.isClosedForReceive) {
            numAsyncConnections.value++

            launch {
                for (socket in channel) {
                    ensureActive()
                    if (!socket.isDisposed) {
                        attemptConnection(socket)
                    }
                }
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun emitConnectionWithDelay(socket: TcpSocket, delay: Duration = connectionPoolConfig.confirmConnectionInterval) =
        launch {
            if (channel.isClosedForSend || socket.isDisposed) cancel()
            delay(delay)
            _socketConnectionFlow.emit(socket)
        }

    @OptIn(DelicateCoroutinesApi::class)
    private fun attemptConnection(socket: TcpSocket) {
        val job = launch(Job() + dispatcher.io) {
            try {
                val canAttemptConnection = socket.canAttemptConnection
                    && (!connectionBuffer.contains(socket)
                    || connectionBuffer.size() < connectionPoolConfig.connectionBufferCapacity)

                if (canAttemptConnection) {

                    launch { socket.connect() }

                    // wait for connection to be established
                    while (!socket.isConnected) {
                        delay(100.milliseconds)
                    }

                    registerFlows(socket)

                    connectionBuffer.remove()?.also { it.disconnect() }
                    connectionBuffer.add(socket)
                }
            } catch (e: CancellationException) {
                throw e // rethrowing so not caught with Exception so that coroutine cancellation remains cooperative
            } catch (e: ClosedReceiveChannelException) {
                e(tag.append("error")) { e.message }
            } catch (e: ClosedSendChannelException) {
                e(tag.append("error")) { e.message }
            } catch (e: Exception) {
                e(tag.append("error")) { e.message }
            } finally {
                //emitConnectionWithDelay(socket)
                cancel()
                //cancelConnectionJob(socket)
            }
        }
        connectionJobs.put(socket.socketConnection.socketAddress, job)
    }

    private fun registerFlows(socket: TcpSocket) {
        //_connectionStateFlow.register(socket.connectionStateFlow)
        //_messageStateFlow.register(socket.messageStateFlow)
    }

    private fun unregisterFlows(socket: TcpSocket) {
        //_connectionStateFlow.unregister(socket.connectionStateFlow)
        //_messageStateFlow.unregister(socket.messageStateFlow)
    }

    fun dispose() {
        job.cancel()
        connectJob.cancel()
        channel.close()
    }
}
