import kotlin.time.Duration.Companion.seconds
import socket.api.PingRequest
import socket.api.SocketAddress
import socket.api.SocketMessage
import socket.api.TcpSocket
import socket.api.TcpSocketConfig

data class Device(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val config: TcpSocketConfig = TcpSocketConfig(),
    val pingRequest: PingRequest? = null,
    val testMessage: SocketMessage,
) {
    val address: String = "$host:$port"

    val viewModel = DeviceViewModel(
        socket = TcpSocket(
            SocketAddress(
                host = host,
                port = port,
            ),
            config = config,
        )
    )
}
