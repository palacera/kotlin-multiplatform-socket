package socket.api.pool

import dispatcher.Dispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import logd
import loge
import logw
import socket.api.SocketAddress

sealed class SocketConnectionPoolLog(open val socketAddress: SocketAddress) {
    data class ConnectingState(override val socketAddress: SocketAddress) : SocketConnectionPoolLog(socketAddress)
    data class ConnectedState(override val socketAddress: SocketAddress) : SocketConnectionPoolLog(socketAddress)
    data class ConnectionAttempt(override val socketAddress: SocketAddress, val numAttempt: Int) : SocketConnectionPoolLog(socketAddress)
    data class ConnectionWarning(override val socketAddress: SocketAddress, val reason: String) : SocketConnectionPoolLog(socketAddress)
    data class StartPing(override val socketAddress: SocketAddress) : SocketConnectionPoolLog(socketAddress)
    data class PingError(override val socketAddress: SocketAddress, val reason: String): SocketConnectionPoolLog(socketAddress)
    data class ConfirmingConnectionState(override val socketAddress: SocketAddress) : SocketConnectionPoolLog(socketAddress)
    data class AwaitingConfirmationState(override val socketAddress: SocketAddress) : SocketConnectionPoolLog(socketAddress)
    data class ConnectionConfirmedState(override val socketAddress: SocketAddress) : SocketConnectionPoolLog(socketAddress)
    data class DisconnectingState(override val socketAddress: SocketAddress) : SocketConnectionPoolLog(socketAddress)
    data class DisconnectedState(override val socketAddress: SocketAddress) : SocketConnectionPoolLog(socketAddress)
    data class DisposedState(override val socketAddress: SocketAddress): SocketConnectionPoolLog(socketAddress)
    data class ErrorState(override val socketAddress: SocketAddress, val reason: String): SocketConnectionPoolLog(socketAddress)
    data class Debug(override val socketAddress: SocketAddress, val message: String) : SocketConnectionPoolLog(socketAddress)
}

class SocketConnectionPoolLogChannel(
    dispatcher: Dispatcher,
): CoroutineScope {

    private val job = Job()
    override val coroutineContext = job + dispatcher.io

    private val channel: Channel<SocketConnectionPoolLog> = Channel(Channel.UNLIMITED)
    val channelFlow = channel.receiveAsFlow()

    suspend fun send(connectionLog: SocketConnectionPoolLog) {
        channel.send(connectionLog)
    }

    fun log(connectionLog: SocketConnectionPoolLog) {
        when (connectionLog) {
            is SocketConnectionPoolLog.ConnectingState -> logConnectingState(connectionLog)
            is SocketConnectionPoolLog.ConnectedState -> logConnectedState(connectionLog)
            is SocketConnectionPoolLog.ConnectionAttempt -> logConnectionAttempt(connectionLog)
            is SocketConnectionPoolLog.ConnectionWarning -> logConnectionWarning(connectionLog)
            is SocketConnectionPoolLog.StartPing -> logStartPing(connectionLog)
            is SocketConnectionPoolLog.PingError -> logPingError(connectionLog)
            is SocketConnectionPoolLog.ConfirmingConnectionState -> logConfirmingConnectionState(connectionLog)
            is SocketConnectionPoolLog.AwaitingConfirmationState -> logAwaitingConfirmationState(connectionLog)
            is SocketConnectionPoolLog.ConnectionConfirmedState -> logConnectionConfirmedState(connectionLog)
            is SocketConnectionPoolLog.DisconnectingState -> logDisconnectingState(connectionLog)
            is SocketConnectionPoolLog.DisconnectedState -> logDisconnectedState(connectionLog)
            is SocketConnectionPoolLog.DisposedState -> logDisposedState(connectionLog)
            is SocketConnectionPoolLog.ErrorState -> logErrorState(connectionLog)
            is SocketConnectionPoolLog.Debug -> logDebug(connectionLog)
        }
    }

    private val SocketConnectionPoolLog.append get() = "(${socketAddress.address}) ${this::class.simpleName}: "

    private fun logConnectingState(connectionLog: SocketConnectionPoolLog.ConnectingState) {
        logd(tag = "socket.pool.connect.state", append = connectionLog.append) { "Connecting to socket" }
    }

    private fun logConnectedState(connectionLog: SocketConnectionPoolLog.ConnectedState) {
        logd(tag = "socket.pool.connect.state", append = connectionLog.append) { "Connected to socket" }
    }

    private fun logConnectionAttempt(connectionLog: SocketConnectionPoolLog.ConnectionAttempt) {
        logd(tag = "socket.pool.connect.attempt", append = connectionLog.append) { "Connection attempt #${connectionLog.numAttempt}" }
    }

    private fun logConnectionWarning(connectionLog: SocketConnectionPoolLog.ConnectionWarning) {
        logw(tag = "socket.pool.connect.warning", append = connectionLog.append) { connectionLog.reason }
    }

    private fun logStartPing(connectionLog: SocketConnectionPoolLog.StartPing) {
        logd(tag = "socket.pool.connect.ping", append = connectionLog.append) { "Sending ping requests" }
    }

    private fun logPingError(connectionLog: SocketConnectionPoolLog.PingError) {
        loge(tag = "socket.pool.connect.ping", append = connectionLog.append) { connectionLog.reason }
    }

    private fun logConfirmingConnectionState(connectionLog: SocketConnectionPoolLog.ConfirmingConnectionState) {
        logd(tag = "socket.pool.connect.state", append = connectionLog.append) { "Confirming connection" }
    }

    private fun logAwaitingConfirmationState(connectionLog: SocketConnectionPoolLog.AwaitingConfirmationState) {
        logd(tag = "socket.pool.connect.state", append = connectionLog.append) { "Awaiting connection confirmation" }
    }

    private fun logConnectionConfirmedState(connectionLog: SocketConnectionPoolLog.ConnectionConfirmedState) {
        logd(tag = "socket.pool.connect.state", append = connectionLog.append) { "Connection confirmed" }
    }

    private fun logDisconnectingState(connectionLog: SocketConnectionPoolLog.DisconnectingState) {
        logd(tag = "socket.pool.connect.state", append = connectionLog.append) { "Disconnecting from socket" }
    }

    private fun logDisconnectedState(connectionLog: SocketConnectionPoolLog.DisconnectedState) {
        logd(tag = "socket.pool.connect.state", append = connectionLog.append) { "Disconnected from socket" }
    }

    private fun logDisposedState(connectionLog: SocketConnectionPoolLog.DisposedState) {
        logd(tag = "socket.pool.connect.state", append = connectionLog.append) { "Socket disposed" }
    }

    private fun logErrorState(connectionLog: SocketConnectionPoolLog.ErrorState) {
        loge(tag = "socket.pool.connect.state", append = connectionLog.append) { connectionLog.reason }
    }

    private fun logDebug(connectionLog: SocketConnectionPoolLog.Debug) {
        logd(tag = "socket.pool.connect.debug", append = connectionLog.append) { connectionLog.message }
    }

    fun dispose() {
        job.cancel()
        channel.close()
    }
}
