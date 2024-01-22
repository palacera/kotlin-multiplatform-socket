package socket.api.connection

import append
import d
import dispatcher.Dispatcher
import dispatcher.DispatcherProvider
import e
import inAirplaneMode
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
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import socket.adapter.TcpSocketAdapter
import socket.api.SocketAddress
import socket.api.SocketConnectionMaxAttemptsException
import socket.api.message.SocketMessage
import socket.api.message.SocketMessageReceipt
import socket.api.message.SocketMessageState
import socket.api.SocketUnreachableAirplaneModeException
import socket.api.TcpSocket
import socket.api.TcpSocketAdapterConnectable
import socket.api.TcpSocketConfig
import socket.api.message.SocketMessageType
import socket.api.ping.PingEventFlow
import socket.api.ping.PingRequest
import socket.api.ping.PingState
import tag

class TcpSocketConnectionManager(
    val tcpSocket: TcpSocket,
    private val pingEventFlow: PingEventFlow,
    val dispatcher: Dispatcher,
) {
    private val scope by lazy { CoroutineScope(dispatcher.io) }

    private val tag = tag("socket", "connect")

    private val socketAddress: SocketAddress = tcpSocket.socketConnection.socketAddress
    private val config: TcpSocketConfig = tcpSocket.socketConfig

    private val newSocket: TcpSocketAdapterConnectable
        get() = TcpSocketAdapter(
            socketAddress = socketAddress,
            config = config,
            dispatcher = DispatcherProvider(),
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
        get() = PingRequest.Config(
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

    private val _isConnectedFlow: MutableStateFlow<Boolean?> = MutableStateFlow(null)

    init {
        scope.launch {
            _isConnectedFlow.collectLatest { isConnected ->
                isConnectedRef.value = isConnected ?: false
                if (isConnected == false) {
                    //reconnect()
                }
            }
        }

        scope.launch {
            while (isActive) {
                _isConnectedFlow.value = socketAdapter.isConnected()
                delay(100.milliseconds)
            }
        }
    }

    private var establishConnectionFunc: (suspend () -> Unit)? = null

    suspend fun connect(
        pingRequest: PingRequest,
    ) {
        establishConnectionFunc = { establishConnection(pingRequest = pingRequest) }
        establishConnectionFunc?.invoke()
    }

    private suspend fun reconnect() {
        disconnect()
        socketAdapter = newSocket
        establishConnectionFunc?.invoke()
    }

    suspend fun disconnect() {
        stopPingRequests()

        SocketConnectionState.Disconnecting(socketAddress).emit()
        socketAdapter.close()
        SocketConnectionState.Disconnected(socketAddress).emit()
    }

    suspend fun cancel() {
        disconnect()
        scope.cancel()
    }

    private suspend fun establishConnection(
        pingRequest: PingRequest,
    ) {
        SocketConnectionState.Connecting(socketAddress).emit()
        d(tag.append("establishing")) { "(${socketAddress.address}) Establishing connection" }

        var attempt = 0
        while (!isConnectedRef.value && attempt < maxAttempts) {
            attempt++
            try {
                attemptConnection(attempt)

                if (pingRequest is PingRequest.Config) {
                    startSendingPingRequests(pingRequest)
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
            SocketConnectionState.Connecting(socketAddress, it).emit()
            logError(it)
        }
    }

    private suspend fun attemptConnection(attempt: Int) {
        logAttempt(attempt)
        connectSocket()
        SocketConnectionState.Connected(socketAddress).emit()
        d(tag.append("established")) { "(${socketAddress.address}) Connection established" }
    }

    private suspend fun startSendingPingRequests(
        pingRequest: PingRequest.Config,
        //messageStateFlow: SharedFlow<SocketMessageState>,
        //isReceivingMessages: StateFlow<Boolean>
    ) {
        observePingState()
        startPingRequests(pingRequest)
        //observePingState(pingRequest, messageStateFlow)
    }

    private suspend fun onDisconnecting(pingRequest: PingRequest, messageStateFlow: SharedFlow<SocketMessageState>) {
        d(tag.append("disconnecting")) { "(${socketAddress.address}) Disconnecting connection" }
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
        SocketConnectionState.Error(socketAddress, exception).emit()
        throw exception
    }

    private suspend fun connectSocket() {
        withTimeout(config.connectionTimeout) {
            socketAdapter.connect()

            // block until isConnected is updated
            while (!isConnectedRef.value) {
                delay(50.milliseconds)
            }
        }
    }

    private fun observePingState() {
        scope.launch {
            try {
                SocketConnectionState.ConfirmingConnection(socketAddress).emit()
                pingEventFlow.observePingState {
                    when (it) {
                        PingState.Sent -> SocketConnectionState.Connecting(socketAddress).emit()
                        PingState.Confirmed-> SocketConnectionState.ConnectionConfirmed(socketAddress).emit()
                    }
                }
            } catch (e: Exception) {
                e(tag.append("ping", "fail")) { "(${socketAddress.address}) ${e.message}" }
                reconnect()
            }
        }
    }

    private suspend fun startPingRequests(
        pingRequest: PingRequest.Config,
    ) {
        pingRequestsJob =  scope.launch {
            d(tag.append("ping")) { "(${socketAddress.address}) Sending ping requests" }
            while (isActive) {
                try {
                    tcpSocket.send(pingRequest.requestMessage, pingRequest.messageReceipt, SocketMessageType.Ping)

                    // ensure that the ping interval is at least the receive interval to prevent orphan pings
                    delay(pingRequest.pingInterval.coerceAtLeast(config.receiveInterval))

                } catch (e: TimeoutCancellationException) {
                    continue
                } catch (e: Exception) {
                    e(tag.append("ping")) { "(${socketAddress.address}) ${e.message}" }
                }
            }
        }
    }

    private fun stopPingRequests() {
        pingRequestsJob?.cancel()
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
