package socket.api

import socket.api.ping.PingRequest

data class SocketConnection(
    val socketAddress: SocketAddress,
    val socketConfig: TcpSocketConfig,
    val pingRequest: PingRequest = PingRequest.None,
)

data class SocketAddress(
    val host: String,
    val port: Int,
) {
    val address = "$host:$port"
    val uid = address.hashCode()
}
