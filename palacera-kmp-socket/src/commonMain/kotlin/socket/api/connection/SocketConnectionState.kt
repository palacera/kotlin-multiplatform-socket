package socket.api.connection

import socket.api.SocketAddress

sealed class SocketConnectionState(open val socketAddress: SocketAddress) {
    data class Connecting(override val socketAddress: SocketAddress, val exception: Exception? = null) : SocketConnectionState(socketAddress)
    data class Connected(override val socketAddress: SocketAddress): SocketConnectionState(socketAddress)
    data class ConfirmingConnection(override val socketAddress: SocketAddress) : SocketConnectionState(socketAddress)
    data class ConnectionConfirmed(override val socketAddress: SocketAddress) : SocketConnectionState(socketAddress)
    data class Disconnecting(override val socketAddress: SocketAddress) : SocketConnectionState(socketAddress)
    data class Disconnected(override val socketAddress: SocketAddress): SocketConnectionState(socketAddress)
    data class Error(override val socketAddress: SocketAddress, val exception: Exception): SocketConnectionState(socketAddress)
}
