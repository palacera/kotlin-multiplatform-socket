package socket.api

sealed class SocketConnectionState(open val socketAddress: SocketAddress) {
    data class Connecting(override val socketAddress: SocketAddress, val exception: Exception? = null) : SocketConnectionState(socketAddress)
    data class Connected(override val socketAddress: SocketAddress): SocketConnectionState(socketAddress)
    data class ConnectionVerifying(override val socketAddress: SocketAddress): SocketConnectionState(socketAddress)
    data class ConnectionVerified(override val socketAddress: SocketAddress): SocketConnectionState(socketAddress)
    data class Disconnecting(override val socketAddress: SocketAddress) : SocketConnectionState(socketAddress)
    data class Disconnected(override val socketAddress: SocketAddress): SocketConnectionState(socketAddress)
    data class Error(override val socketAddress: SocketAddress, val exception: Exception): SocketConnectionState(socketAddress)
}
