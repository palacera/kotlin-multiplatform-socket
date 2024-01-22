package socket.api

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

data class TcpSocketConfig(
    val noDelay: Boolean = true,
    val lingerSeconds: Int = 1, //-1,
    val keepAlive: Boolean? = true, //null,
    val receiveInterval: Duration = 200.milliseconds, // how often to check for new messages
    val connectionRetryInterval: Duration = 2.seconds,
    val maxConnectionAttempts: Int = Int.MAX_VALUE,
    val connectionTimeout: Duration = 30.seconds,
    val socketTimeout: Duration = Duration.INFINITE,
    val receiveBufferSize: Long = -1,
    val sendBufferSize: Long = -1,
    val reuseAddress: Boolean = false,
    val reusePort: Boolean = false,
)
