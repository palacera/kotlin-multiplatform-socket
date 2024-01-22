package socket.api.pool

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import socket.api.TcpSocketConfig

data class TcpSocketConnectionPoolConfig(
    var maxPoolSize: Int = 10,
    val dataStructure: SocketConnectionPoolDataStructure = SocketConnectionPoolDataStructure.Queue,

    val maxAsyncConnections: Int = 3, // higher number is faster but connecting more sockets at once
    val connectionBufferCapacity: Int = 12, // higher number has less reconnections but stores more connections in array
    val confirmConnectionInterval: Duration = 1.seconds,

    val socketConfig: TcpSocketConfig = TcpSocketConfig(),
)
