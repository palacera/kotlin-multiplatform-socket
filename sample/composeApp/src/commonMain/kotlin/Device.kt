import socket.api.SocketConnection
import socket.api.message.SocketMessage

data class Device(
    val id: String,
    val name: String,
    val socketConnection: SocketConnection,
    val testMessage: SocketMessage,
) {
    val address: String = "${socketConnection.socketAddress.host}:${socketConnection.socketAddress.port}"
}
