package socket.api

data class SocketAddress(
    val host: String,
    val port: Int,
) {
    val uuid = "$host:$port" // TODO change this to uuid
}
