package socket.api.connection

import socket.api.SocketAddress
import socket.api.ping.PingRequest

sealed class SocketConnectionState(open val socketAddress: SocketAddress) {

    data class Connecting(
        override val socketAddress: SocketAddress,
        val exception: Exception? = null,
    ) : SocketConnectionState(socketAddress)

    data class Connected(
        override val socketAddress: SocketAddress,
    ): SocketConnectionState(socketAddress)

    data class ConfirmingConnection(
        override val socketAddress: SocketAddress,
    ) : SocketConnectionState(socketAddress)

    data class AwaitingConfirmation(
        override val socketAddress: SocketAddress,
    ) : SocketConnectionState(socketAddress)

    data class ConnectionConfirmed(
        override val socketAddress: SocketAddress,
    ) : SocketConnectionState(socketAddress)

    data class Disconnecting(
        override val socketAddress: SocketAddress,
        val reconnect: Boolean = false,
        val dispose: Boolean = false,
    ) : SocketConnectionState(socketAddress)

    data class Disconnected(
        override val socketAddress: SocketAddress,
        val reconnect: Boolean = false,
        val dispose: Boolean = false,
    ): SocketConnectionState(socketAddress)

    data class Disposed(
        override val socketAddress: SocketAddress,
    ): SocketConnectionState(socketAddress)

    data class Error(
        override val socketAddress: SocketAddress,
        val exception: Exception,
    ): SocketConnectionState(socketAddress)
}

fun SocketConnectionState.isConnected(pingRequest: PingRequest) = when {
    this is SocketConnectionState.Connected && pingRequest is PingRequest.None -> true
    this is SocketConnectionState.ConnectionConfirmed -> true
    else -> false
}

fun SocketConnectionState.isDisconnected() = this is SocketConnectionState.Disconnected

fun SocketConnectionState.canAttemptConnection() = isDisconnected() || this is SocketConnectionState.Error
