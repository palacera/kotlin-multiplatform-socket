package socket.api

import PlatformDispatcher
import append
import d
import e
import inAirplaneMode
import io.ktor.utils.io.core.toByteArray
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.atomicfu.AtomicBoolean
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import socket.adapter.TcpSocketAdapter
import tag

class TcpSocketConnectionManager(
    val tcpSocket: TcpSocket,
) {
    private val scope by lazy { CoroutineScope(PlatformDispatcher.io) }

    private val tag = tag("socket", "connect")

    private val socketAddress: SocketAddress = tcpSocket.socketAddress
    private val config: TcpSocketConfig = tcpSocket.config

    private val newSocket: TcpSocketAdapterConnectable
        get() = TcpSocketAdapter(
            socketAddress = socketAddress,
            config = config,
        )

    private var socketAdapter: TcpSocketAdapterConnectable = newSocket
    val socket get() = socketAdapter

    private val maxAttempts: Int by lazy {
        if (config.maxConnectionAttempts == -1) {
            Int.MAX_VALUE
        } else {
            config.maxConnectionAttempts
        }
    }

    private val defaultPingRequest
        get() = PingRequest(
            requestMessage = SocketMessage { ByteArray(0) },
            messageReceipt = SocketMessageReceipt { it.isEmpty() },
        )

    private val isConnectedRef: AtomicBoolean = atomic(false)

    private val _connectionStateFlow: MutableSharedFlow<SocketConnectionState> =
        MutableStateFlow(SocketConnectionState.Disconnected(socketAddress))

    val connectionStateFlow: SharedFlow<SocketConnectionState> =
        _connectionStateFlow.asSharedFlow()

    private suspend fun SocketConnectionState.emit() =
        _connectionStateFlow.emit(this)

    private var pingRequestsJob: Job? = null


    suspend fun connect(
        pingRequest: PingRequest? = null,
        messageStateFlow: SharedFlow<SocketMessageState>,
        isReceivingMessages: StateFlow<Boolean>
    ) {
        d(tag.append("establishing")) { "(${socketAddress.address}) Establishing connection" }

        establishConnection(
            pingRequest = pingRequest,
            messageStateFlow = messageStateFlow,
            isReceivingMessages = isReceivingMessages
        )
    }

    suspend fun disconnect() {
        SocketConnectionState.Disconnecting(socketAddress).emit()
        socketAdapter.close()
        SocketConnectionState.Disconnected(socketAddress).emit()
    }

    suspend fun cancel() {
        onCancelCallback()
        disconnect()
        scope.cancel()
    }

//    private suspend fun onConnected(state: SocketConnectionState.Connected) {
//        d(tag.append("established")) { "(${socketAddress.address}) Connection established" }
//
//        monitorPingReceipts(state.pingRequest, state.messageStateFlow)
//        pingRequestsJob = sendPingRequests(state.pingRequest)
//    }

//    private suspend fun onConfirmingConnection(state: SocketConnectionState.ConfirmingConnection) {
//        d(tag.append("confirming")) { "(${socketAddress.address}) Confirming connection" }
//    }

//    private suspend fun onConnectionConfirmed(state: SocketConnectionState.ConnectionConfirmed) {
//        d(tag.append("confirmed")) { "(${socketAddress.address}) Connection confirmed" }
//    }


//    private suspend fun onDisconnecting(state: SocketConnectionState.Disconnecting) {
//        d(tag.append("disconnecting")) { "(${socketAddress.address}) Disconnecting connection" }
//
//        pingRequestsJob?.cancel()
//    }

//    private suspend fun onDisconnected(state: SocketConnectionState.Disconnected) {
//        d(tag.append("disconnected")) { "(${socketAddress.address}) Connection disconnected" }
//
//        //if (canReconect or is first attempt) {
//
//        //}
//    }


//    private suspend fun emit(event: SocketConnectionEvent) {
//        _connectionEventFlow.emit(event)
//    }

    private suspend fun emitx(state: SocketConnectionState) {
        _connectionStateFlow.emit(state)
    }

    private suspend fun establishConnection(
        pingRequest: PingRequest? = null,
        messageStateFlow: SharedFlow<SocketMessageState>,
        isReceivingMessages: StateFlow<Boolean>
    ) {
        emitx(SocketConnectionState.Connecting(socketAddress))

        var attempt = 0
        while (!isConnectedRef.value && attempt < maxAttempts) {
            attempt++
            try {
                attemptConnection(attempt)
                onConnectionCallback()

                if (pingRequest != null) {
                    startSendingPingRequests(pingRequest, messageStateFlow, isReceivingMessages)
                }

                break
            } catch (e: Exception) {
                handleConnectionError(e, attempt)
            }
        }
    }

    private suspend fun resolveException(exception: Exception) {
        when {
            inAirplaneMode -> SocketUnreachableAirplaneModeException()
            else -> exception
        }.also {
            emitx(SocketConnectionState.Connecting(socketAddress, it))
            logError(it)
        }
    }

    private suspend fun attemptConnection(attempt: Int) {
        logAttempt(attempt)
        connectSocket()
        emitx(SocketConnectionState.Connected(socketAddress))
        d(tag.append("established")) { "(${socketAddress.address}) Connection established" }
    }

    private var onConnectionCallback = suspend {}
    private var onDisconnectionCallback = suspend {}
    private var onCancelCallback = suspend {}

    fun onConnection(lambda: suspend () -> Unit) {
        onConnectionCallback = lambda
    }

    fun onDisconnection(lambda: suspend () -> Unit) {
        onDisconnectionCallback = lambda
    }

    fun onCancel(lambda: suspend () -> Unit) {
        onCancelCallback = lambda
    }

    private suspend fun startSendingPingRequests(
        pingRequest: PingRequest,
        messageStateFlow: SharedFlow<SocketMessageState>,
        isReceivingMessages: StateFlow<Boolean>
    ) {
        observePingReceipts(pingRequest, messageStateFlow)

        isReceivingMessages.collectLatest { isReceiving ->
            if (isReceiving) {
                pingRequestsJob = sendPingRequests(pingRequest)
            } else {
                pingRequestsJob?.cancel()
            }
        }
    }

    private suspend fun onDisconnecting(pingRequest: PingRequest, messageStateFlow: SharedFlow<SocketMessageState>) {
        d(tag.append("disconnecting")) { "(${socketAddress.address}) Disconnecting connection" }
        onDisconnectionCallback()
        pingRequestsJob?.cancel()
    }

    private suspend fun handleConnectionError(exception: Exception, attempt: Int) {
        resolveException(exception)

        if (attempt == maxAttempts) {
            throwMaxAttemptsException()
        } else {
            logRetry()
            delay(config.connectionRetryInterval)
        }
    }

    private suspend fun throwMaxAttemptsException(): Nothing {
        val exception = SocketConnectionMaxAttemptsException("Failed to connect after $maxAttempts attempt(s)")
        logError(exception)
        emitx(SocketConnectionState.Error(socketAddress, exception))
        throw exception
    }

    private suspend fun connectSocket() {
        withTimeout(config.connectionTimeout) {
            socketAdapter.connect()

            // block until isConnected is updated
            while (!socketAdapter.isConnected()) {
                delay(50.milliseconds)
            }
            isConnectedRef.value = true
        }
    }

    private fun observePingReceipts(
        pingRequest: PingRequest,
        messageStateFlow: SharedFlow<SocketMessageState>
    ) {
        scope.launch {
            SocketConnectionState.ConfirmingConnection(socketAddress).emit()

            messageStateFlow
                .collect {
                    when {
                        it is SocketMessageState.MessageReceiptReceived
                            //&& it.sentMessageId == pingRequest.requestMessageId  // TODO fix message ids
                        -> {
                            SocketConnectionState.ConnectionConfirmed(socketAddress).emit()
                        }

                        it is SocketMessageState.Error -> {
                            if (it.exception is SocketConnectionException) {
                                //tcpSocket.close()
                                //isConnected.value = false
                                //pingRequestsJob?.cancel()
                                //socketAdapter.close()
                                //socketAdapter = newSocket
                                //establishConnection()
                                //tcpSocket.reconnect(pingRequest)
                            }
                        }

                        else -> Unit
                    }
                }
        }
    }

    private suspend fun sendPingRequests(
        pingRequest: PingRequest,
    ): Job {
        return scope.launch {
            d(tag.append("ping")) { "(${socketAddress.address}) Sending ping requests" }
            while (isActive) {
                try {
                    //d(tag.append("ping")) { "(${socketAddress.address}) ping" }
                    // delay emitting ConnectionVerifying state to allow time to be verified
                    // this prevents a quick flash between ConnectionVerified and ConnectionVerifying states
//                val verifyingStateJob = launch {
//                    delay(pingRequest.pingInterval.coerceIn(500.milliseconds, 2.seconds))
//                    SocketConnectionState.ConnectionVerifying(socketAddress).emit()
//                }

                    // send ping with timeout for when expected response to be received before another ping is sent
                    withTimeout(pingRequest.pingTimeout) {
                        tcpSocket.send(pingRequest.requestMessage, pingRequest.messageReceipt)
                    }

                    // received ping response so cancel ConnectionVerifying state emission
                    //verifyingStateJob.cancel()

                    // ensure that the ping interval is at least the receive interval to prevent orphan pings
                    delay(pingRequest.pingInterval.coerceAtLeast(config.receiveInterval))

                } catch (e: TimeoutCancellationException) {
                    continue
                } catch (e: Exception) {
                    e(tag.append("ping")) { "(${socketAddress.address}) ${e.message}" }
                }
            }

            // TODO need to rewrite this for when the socket is disconnected and reconnects
//            var i = 0
//            while (isActive) {
//                i++
//
//                val isConnected = socketAdapter.isConnected()
//
//                var message = if (isConnected) {
//                    "#$i Socket connected"
//                } else {
//                    "#$i Socket disconnected"
//                }
//
//                d(tag.append("monitor")) { message }
//
//
//
//                if (!isConnected) {
//                    //socketAdapter.close()
//                    socketAdapter = newSocket
//                    this@TcpSocketConnectionManager.isConnected.value = false
//                    establishConnection()
//                }
//
//                delay(1.seconds) // TODO get from config
//            }
        }

    }

    private suspend fun logError(exception: Exception) {
        e(tag.append("error")) { "(${socketAddress.address}) ${exception.message}" }
    }

    private suspend fun logX(exception: Exception) {
        e(tag.append("error"), exception = exception) { "(${socketAddress.address}) ${exception.message}" }
    }

    private suspend fun logAttempt(attempt: Int) {
        d(tag.append("attempt")) { "(${socketAddress.address}) Connection attempt #$attempt" }
    }

    private suspend fun logRetry() {
        e(tag.append("retry")) { "(${socketAddress.address}) Connection failed, retrying after delay..." }
    }
}
