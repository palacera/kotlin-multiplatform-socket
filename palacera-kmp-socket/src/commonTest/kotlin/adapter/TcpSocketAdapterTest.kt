package adapter

import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.answering.sequentially
import dev.mokkery.answering.throws
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.TcpSocketBuilder
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import socket.adapter.SelectorManagerAdapter
import socket.adapter.TcpSocketAdapter
import socket.api.SocketAddress
import socket.api.SocketConnectionException
import socket.api.SocketConnectionTimeoutException
import socket.api.TcpSocketConfig

@OptIn(ExperimentalCoroutinesApi::class)
class TcpSocketAdapterTest {

    private lateinit var socketAdapter: TcpSocketAdapter
    private val socket: Socket = mock()
    private val readChannel: ByteReadChannel = mock()
    private val writeChannel: ByteWriteChannel = mock()
    private val selectorManager: SelectorManager = SelectorManagerAdapter().resolve()

    @BeforeTest
    fun setup() {
        //every { socket.openReadChannel() } returns readChannel
        //every { socket.openWriteChannel(autoFlush = true) } returns writeChannel

        socketAdapter = TcpSocketAdapter(
            socketAddress = SocketAddress("127.0.0.1", 9002),
            config = TcpSocketConfig(connectionTimeout = 2.seconds),
            selectorManager = SelectorManagerAdapter().resolve()
        )
    }

    @AfterTest
    fun tearDown() {

    }

    @Test
    fun test() = runTest {

        //everySuspend { aSocket(selectorManager).tcp().connect(any(), {}) } throws SocketConnectionException("Connection refused")

        //socketAdapter.connect()
    }
}
