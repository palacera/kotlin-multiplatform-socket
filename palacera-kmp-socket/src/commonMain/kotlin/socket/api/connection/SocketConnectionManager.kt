package socket.api.connection

import Mvi
import dispatcher.Dispatcher
import dispatcher.DispatcherProvider
import inAirplaneMode
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import mvi
import socket.adapter.TcpSocketAdapter
import socket.api.SocketAddress
import socket.api.SocketConnectionException
import socket.api.SocketConnectionMaxAttemptsException
import socket.api.SocketUnreachableAirplaneModeException
import socket.api.TcpSocket
import socket.api.TcpSocketAdapterConnectable
import socket.api.TcpSocketConfig
import socket.api.message.SocketMessageType
import socket.api.ping.PingFlow
import socket.api.ping.PingRequest
import socket.api.ping.PingState

val Exception.messageOrDefault: String get() = message ?: "An error occurred"

sealed interface SideEffect {}


class SocketConnectionManager(
    val tcpSocket: TcpSocket,
    private val pingFlow: PingFlow,
    val dispatcher: Dispatcher,
) : CoroutineScope,
    Mvi<SocketConnectionState, SocketConnectionEvent, SideEffect> by mvi(
        SocketConnectionState.Disconnected(
            tcpSocket.socketConnection.socketAddress
        )
    ) {

    private val job = Job()
    override val coroutineContext = job + dispatcher.io

    private val socketAddress: SocketAddress = tcpSocket.socketConnection.socketAddress
    private val config: TcpSocketConfig = tcpSocket.socketConnection.socketConfig

    private val newSocketInstance: TcpSocketAdapterConnectable
        get() = TcpSocketAdapter(
            socketAddress = socketAddress,
            config = config,
            dispatcher = DispatcherProvider(),
        )

    private val socketRef: AtomicRef<TcpSocketAdapterConnectable> = atomic(newSocketInstance)
    val socket: TcpSocketAdapterConnectable get() = socketRef.value

    private val logChannel by lazy {
        SocketConnectionLogChannel(
            dispatcher = dispatcher,
            socketAddress = socketAddress
        )
    }

    private var attemptConnectionJob: Job? = null
    private var pingRequestsJob: Job? = null
    private var observePingState: Job? = null

    val isConnected get() = currentConnectionState.value.isConnected(tcpSocket.socketConnection.pingRequest)

    val isDisconnected get() = currentConnectionState.value.isDisconnected()

    val canAttemptConnection get() = currentConnectionState.value.canAttemptConnection()

    val currentConnectionState: AtomicRef<SocketConnectionState> = atomic(SocketConnectionState.Disconnected(socketAddress))

    init {
        launch(CoroutineName("CollectCollectionState")) {
            state.collect {
                currentConnectionState.value = it
                when (it) {
                    is SocketConnectionState.Connecting -> withConnectingState(it)
                    is SocketConnectionState.Connected -> withConnectedState(it)
                    is SocketConnectionState.ConfirmingConnection -> withConfirmingConnectionState(it)
                    is SocketConnectionState.AwaitingConfirmation -> withAwaitingConfirmationState(it)
                    is SocketConnectionState.ConnectionConfirmed -> withConnectionConfirmedState(it)
                    is SocketConnectionState.Disconnecting -> withDisconnectingState(it)
                    is SocketConnectionState.Disconnected -> withDisconnectedState(it)
                    is SocketConnectionState.Disposed -> withDisposedState(it)
                    is SocketConnectionState.Error -> withErrorState(it)
                }
            }
        }

        launch(CoroutineName("CollectCollectionEvent")) {
            event.collect {
                when (it) {
                    is SocketConnectionEvent.Connect -> onConnectEvent(it)
                    is SocketConnectionEvent.Disconnect -> onDisconnectEvent(it)
                    is SocketConnectionEvent.Dispose -> onDisposeEvent(it)
                }
            }
        }
    }

    private fun log(connectionLog: ConnectionLog) {
        logChannel.log(connectionLog)
    }

    private fun onConnectEvent(event: SocketConnectionEvent.Connect) {
        launch { emitState(SocketConnectionState.Connecting(socketAddress)) }
    }

    private fun onDisconnectEvent(event: SocketConnectionEvent.Disconnect) {
        launch { emitState(SocketConnectionState.Disconnecting(socketAddress, dispose = false)) }
    }

    private fun onDisposeEvent(event: SocketConnectionEvent.Dispose) {
        when (currentConnectionState.value) {
            is SocketConnectionState.Disconnected -> launch { emitState(SocketConnectionState.Disposed(socketAddress)) }
            else -> launch { emitState(SocketConnectionState.Disconnecting(socketAddress, dispose = true)) }
        }
    }

    private fun withConnectingState(state: SocketConnectionState.Connecting) {
        log(ConnectionLog.ConnectingState)

        attemptConnectionJob = launch {
            try {
                attemptConnection()
                emitState(SocketConnectionState.Connected(socketAddress))
            } catch (e: CancellationException) {
                throw e // rethrowing so not caught with Exception so that coroutine cancellation remains cooperative
            } catch (e: Exception) {
                emitState(SocketConnectionState.Error(socketAddress, e))
            }
        }
    }

    private fun withConnectedState(state: SocketConnectionState.Connected) {
        log(ConnectionLog.ConnectedState)

        if (tcpSocket.socketConnection.pingRequest is PingRequest.Config) {
            observePingState = launch {
                launch {
                    pingFlow.pingStateFlow.collect {
                        if (socket.isConnected()) {
                            when (it) {
                                is PingState.Sent -> emitState(SocketConnectionState.ConfirmingConnection(socketAddress))
                                is PingState.Waiting -> emitState(SocketConnectionState.AwaitingConfirmation(socketAddress))
                                is PingState.Confirmed -> emitState(SocketConnectionState.ConnectionConfirmed(socketAddress))
                                is PingState.Error -> emitState(SocketConnectionState.Error(socketAddress, it.exception))
                            }
                        } else {
                            val exception = SocketConnectionException("Socket is not connected")
                            emitState(SocketConnectionState.Error(socketAddress, exception))
                        }
                    }
                }
                launch { pingFlow.observePingState() }
            }

            startPingRequests(tcpSocket.socketConnection.pingRequest)
            launch { emitState(SocketConnectionState.ConfirmingConnection(socketAddress)) }
        }
    }

    private fun withConfirmingConnectionState(state: SocketConnectionState.ConfirmingConnection) {
        log(ConnectionLog.ConfirmingConnectionState)
    }

    private fun withAwaitingConfirmationState(state: SocketConnectionState.AwaitingConfirmation) {
        log(ConnectionLog.AwaitingConfirmationState)
    }

    private fun withConnectionConfirmedState(state: SocketConnectionState.ConnectionConfirmed) {
        log(ConnectionLog.ConnectionConfirmedState)
    }

    private fun withDisconnectingState(state: SocketConnectionState.Disconnecting) {
        log(ConnectionLog.DisconnectingState)
        launch {
            stopPingRequests()
            cancelConnectionAttempt()
            emitState(
                SocketConnectionState.Disconnected(
                    socketAddress = socketAddress,
                    reconnect = state.reconnect,
                    dispose = state.dispose
                )
            )
        }
    }

    private fun withDisconnectedState(state: SocketConnectionState.Disconnected) {
        log(ConnectionLog.DisconnectedState)
        socket.close()

        when {
            state.reconnect -> launch {
                socketRef.value = newSocketInstance
                emitState(SocketConnectionState.Connecting(socketAddress))
            }

            state.dispose -> launch { emitState(SocketConnectionState.Disposed(socketAddress)) }
        }
    }

    private suspend fun withDisposedState(state: SocketConnectionState.Disposed) {
        log(ConnectionLog.DisposedState)

        job.cancel()
        logChannel.dispose()
    }

    private suspend fun withErrorState(state: SocketConnectionState.Error) {
        log(ConnectionLog.ErrorState(state.exception.messageOrDefault))
        reconnectSocket()
    }

    private fun reconnectSocket() {
        launch {
            emitState(
                SocketConnectionState.Disconnecting(
                    socketAddress,
                    reconnect = true,
                    dispose = false
                )
            )
        }
    }

    private suspend fun attemptConnection() {
        for (attempt in 1..config.maxConnectionAttempts) {
            coroutineContext.ensureActive()

            try {
                withTimeout(config.connectionTimeout) {
                    socket.connect()
                }
                return
            } catch (e: CancellationException) {
                throw e // rethrowing so not caught with Exception so that coroutine cancellation remains cooperative
            } catch (e: Exception) {

                if (inAirplaneMode) {
                    throw SocketUnreachableAirplaneModeException()
                }

                if (attempt == config.maxConnectionAttempts) {
                    throw SocketConnectionMaxAttemptsException("Failed to connect after $attempt attempt(s)")
                }

                log(ConnectionLog.RetryConnectionWarning(reason = e.messageOrDefault))

                delay(config.connectionRetryInterval)
            }
        }
    }

    private fun startPingRequests(
        pingRequest: PingRequest.Config,
    ) {
        pingRequestsJob = launch {
            log(ConnectionLog.StartPing)
            while (isActive) {
                try {
                    tcpSocket.send(pingRequest.requestMessage, pingRequest.messageReceipt, SocketMessageType.Ping)

                    // ensure that the ping interval is at least the receive interval to prevent orphan pings
                    delay(pingRequest.pingInterval.coerceAtLeast(config.receiveInterval))

                } catch (e: CancellationException) {
                    throw e // rethrowing so not caught with Exception so that coroutine cancellation remains cooperative
                } catch (e: Exception) {
                    log(ConnectionLog.PingError(reason = e.toString()))
                    cancel()
                }
            }
        }
    }

    private suspend fun stopPingRequests() {
        observePingState?.cancelAndJoin()
        pingRequestsJob?.cancelAndJoin()
    }

    private suspend fun cancelConnectionAttempt() {
        attemptConnectionJob?.cancelAndJoin()
    }
}
