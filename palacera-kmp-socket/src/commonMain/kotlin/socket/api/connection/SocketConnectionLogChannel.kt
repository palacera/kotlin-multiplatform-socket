package socket.api.connection

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

sealed interface ConnectionLog {
    data object ConnectingState : ConnectionLog
    data object ConnectedState : ConnectionLog
    data class ConnectionAttempt(val numAttempt: Int) : ConnectionLog
    data class ConnectionWarning(val reason: String) : ConnectionLog
    data class RetryConnectionWarning(val reason: String) : ConnectionLog
    data object StartPing : ConnectionLog
    data class PingError(val reason: String): ConnectionLog
    data object ConfirmingConnectionState : ConnectionLog
    data object AwaitingConfirmationState : ConnectionLog
    data object ConnectionConfirmedState : ConnectionLog
    data object DisconnectingState : ConnectionLog
    data object DisconnectedState : ConnectionLog
    data object DisposedState: ConnectionLog
    data class ErrorState(val reason: String): ConnectionLog
    data class Debug(val message: String) : ConnectionLog
}

class SocketConnectionLogChannel(
    dispatcher: Dispatcher,
    socketAddress: SocketAddress,
): CoroutineScope {

    private val job = Job()
    override val coroutineContext = job + dispatcher.io

    private val channel: Channel<ConnectionLog> = Channel(Channel.UNLIMITED)
    val channelFlow = channel.receiveAsFlow()

    private val logAppendText = "(${socketAddress.address}) "

    suspend fun send(connectionLog: ConnectionLog) {
        channel.send(connectionLog)
    }



    fun log(connectionLog: ConnectionLog) {
        when (connectionLog) {
            is ConnectionLog.ConnectingState -> logConnectingState(connectionLog)
            is ConnectionLog.ConnectedState -> logConnectedState(connectionLog)
            is ConnectionLog.ConnectionAttempt -> logConnectionAttempt(connectionLog)
            is ConnectionLog.ConnectionWarning -> logConnectionWarning(connectionLog)
            is ConnectionLog.RetryConnectionWarning -> logRetryConnectionWarning(connectionLog)
            is ConnectionLog.StartPing -> logStartPing(connectionLog)
            is ConnectionLog.PingError -> logPingError(connectionLog)
            is ConnectionLog.ConfirmingConnectionState -> logConfirmingConnectionState(connectionLog)
            is ConnectionLog.AwaitingConfirmationState -> logAwaitingConfirmationState(connectionLog)
            is ConnectionLog.ConnectionConfirmedState -> logConnectionConfirmedState(connectionLog)
            is ConnectionLog.DisconnectingState -> logDisconnectingState(connectionLog)
            is ConnectionLog.DisconnectedState -> logDisconnectedState(connectionLog)
            is ConnectionLog.DisposedState -> logDisposedState(connectionLog)
            is ConnectionLog.ErrorState -> logErrorState(connectionLog)
            is ConnectionLog.Debug -> logDebug(connectionLog)
        }
    }

    private val ConnectionLog.name get() = this::class.simpleName

    private fun logConnectingState(connectionLog: ConnectionLog.ConnectingState) {
        logd(tag = "socket.connect.state", append = "$logAppendText${connectionLog.name}: ") { "Connecting to socket" }
    }

    private fun logConnectedState(connectionLog: ConnectionLog.ConnectedState) {
        logd(tag = "socket.connect.state", append = "$logAppendText${connectionLog.name}: ") { "Connected to socket" }
    }

    private fun logConnectionAttempt(connectionLog: ConnectionLog.ConnectionAttempt) {
        logd(tag = "socket.connect.attempt", append = "$logAppendText${connectionLog.name}: ") { "Connection attempt #${connectionLog.numAttempt}" }
    }

    private fun logConnectionWarning(connectionLog: ConnectionLog.ConnectionWarning) {
        logw(tag = "socket.connect.warning", append = "$logAppendText${connectionLog.name}: ") { connectionLog.reason }
    }

    private fun logRetryConnectionWarning(connectionLog: ConnectionLog.RetryConnectionWarning) {
        logw(tag = "socket.connect.warning", append = "$logAppendText${connectionLog.name}: ") { "${connectionLog.reason}. Retrying..." }
    }

    private fun logStartPing(connectionLog: ConnectionLog.StartPing) {
        logd(tag = "socket.connect.ping", append = "$logAppendText${connectionLog.name}: ") { "Sending ping requests" }
    }

    private fun logPingError(connectionLog: ConnectionLog.PingError) {
        loge(tag = "socket.connect.ping", append = "$logAppendText${connectionLog.name}: ") { connectionLog.reason }
    }

    private fun logConfirmingConnectionState(connectionLog: ConnectionLog.ConfirmingConnectionState) {
        logd(tag = "socket.connect.state", append = "$logAppendText${connectionLog.name}: ") { "Confirming connection" }
    }

    private fun logAwaitingConfirmationState(connectionLog: ConnectionLog.AwaitingConfirmationState) {
        logd(tag = "socket.connect.state", append = "$logAppendText${connectionLog.name}: ") { "Awaiting connection confirmation" }
    }

    private fun logConnectionConfirmedState(connectionLog: ConnectionLog.ConnectionConfirmedState) {
        logd(tag = "socket.connect.state", append = "$logAppendText${connectionLog.name}: ") { "Connection confirmed" }
    }

    private fun logDisconnectingState(connectionLog: ConnectionLog.DisconnectingState) {
        logd(tag = "socket.connect.state", append = "$logAppendText${connectionLog.name}: ") { "Disconnecting from socket" }
    }

    private fun logDisconnectedState(connectionLog: ConnectionLog.DisconnectedState) {
        logd(tag = "socket.connect.state", append = "$logAppendText${connectionLog.name}: ") { "Disconnected from socket" }
    }

    private fun logDisposedState(connectionLog: ConnectionLog.DisposedState) {
        logd(tag = "socket.connect.state", append = "$logAppendText${connectionLog.name}: ") { "Socket disposed" }
    }

    private fun logErrorState(connectionLog: ConnectionLog.ErrorState) {
        loge(tag = "socket.connect.state", append = "$logAppendText${connectionLog.name}: ") { connectionLog.reason }
    }

    private fun logDebug(connectionLog: ConnectionLog.Debug) {
        logd(tag = "socket.connect.debug", append = "$logAppendText${connectionLog.name}: ") { connectionLog.message }
    }

    fun dispose() {
        job.cancel()
        channel.close()
    }
}
