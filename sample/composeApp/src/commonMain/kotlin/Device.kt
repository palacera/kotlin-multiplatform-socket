import dispatcher.DispatcherProvider
import socket.api.ping.PingRequest
import socket.api.SocketAddress
import socket.api.SocketConnection
import socket.api.message.SocketMessage
import socket.api.TcpSocket
import socket.api.TcpSocketConfig

data class Device(
    val id: String,
    val name: String,
    val socketConnection: SocketConnection,
    val config: TcpSocketConfig = TcpSocketConfig(),
    val testMessage: SocketMessage,
) {
    val address: String = "${socketConnection.socketAddress.host}:${socketConnection.socketAddress.port}"

    val viewModel = DeviceViewModel(
        socket = TcpSocket(
            socketConnection = socketConnection,
            socketConfig = config,
            dispatcher = DispatcherProvider()
        ),
        dispatcher = DispatcherProvider()
    )
}
