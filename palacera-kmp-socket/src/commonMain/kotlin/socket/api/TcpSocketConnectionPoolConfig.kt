package socket.api

data class TcpSocketConnectionPoolConfig(
    var maxPoolSize: Int = 10,
    val dataStructure: SocketConnectionPoolDataStructure = SocketConnectionPoolDataStructure.Queue,
    val socketConfig: TcpSocketConfig = TcpSocketConfig(),
)
