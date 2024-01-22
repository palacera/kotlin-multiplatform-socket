package socket.adapter

import inAirplaneMode
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.TcpSocketBuilder
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.cancel
import io.ktor.utils.io.close
import io.ktor.utils.io.core.toByteArray
import io.ktor.utils.io.readUTF8Line
import io.ktor.utils.io.writeStringUtf8
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import socket.api.SocketAddress
import socket.api.SocketConnectionException
import socket.api.SocketConnectionTimeoutException
import socket.api.SocketException
import socket.api.SocketMessage
import socket.api.SocketReadChannelException
import socket.api.SocketUnreachableAirplaneModeException
import socket.api.SocketWriteChannelException
import socket.api.TcpSocketConfig

internal class TcpSocketAdapter(
    private val socketAddress: SocketAddress,
    private val config: TcpSocketConfig,
    private val selectorManager: SelectorManager,
) : TcpSocketAdapterConnectable {

    private lateinit var connectedTcpSocket: Socket

    private val isReadChannelOpenRef = atomic(false)
    private val isWriteChannelOpenRef = atomic(false)

    private val isConnectedRef = atomic(false)

    private val tcpSocketBuilder: TcpSocketBuilder by lazy {
        try {
            aSocket(selectorManager).tcp()
        } catch (t: Throwable) {
            throw SocketException(t.message)
        }
    }

    private val readChannel: ByteReadChannel by lazy {
        try {
            connectedTcpSocket.openReadChannel()
                .also { isReadChannelOpenRef.value = true }
        } catch (t: Throwable) {
            throw SocketReadChannelException(t.message)
        }
    }

    private fun closeReadChannel() {
        if (isReadChannelOpenRef.value) {
            readChannel.cancel()
            isReadChannelOpenRef.value = false
        }
    }

    private val writeChannel: ByteWriteChannel by lazy {
        try {
            connectedTcpSocket.openWriteChannel(autoFlush = true) //config.writeAutoFlush)
                .also { isWriteChannelOpenRef.value = true }
        } catch (t: Throwable) {
            throw SocketWriteChannelException(t.message)
        }
    }

    private fun closeWriteChannel() {
        if (isWriteChannelOpenRef.value) {
            writeChannel.close()
            isWriteChannelOpenRef.value = false
        }
    }

    override suspend fun connect() {
        try {
            withTimeout(config.connectionTimeout) {
                connectedTcpSocket = tcpSocketBuilder.connect(socketAddress.host, socketAddress.port) {
                    resolve(config)
                }
                isConnectedRef.value = true
            }
        } catch (t: TimeoutCancellationException) {
            throw SocketConnectionTimeoutException(t.message)
        } catch (t: Exception) {
            when {
                inAirplaneMode -> throw SocketUnreachableAirplaneModeException()
                else -> throw SocketConnectionException(t.message)
            }
        }
    }

    override suspend fun send(message: SocketMessage) {
        try {
            // TODO might need to make SocketMessage generic if server needs to match message.content type
            //writeChannel.writeFully(message.content)
            writeChannel.writeStringUtf8("${message.content.decodeToString()}\n")
        } catch (t: Exception) {
            throw SocketWriteChannelException(t.message)
        }
    }

    override suspend fun receive(): SocketMessage {
        return try {
            val message = readChannel.readUTF8Line()
            val byteArray = if (message == null) {
                ByteArray(0)
            } else {
                message.toByteArray()
            }

            // TODO might need to make SocketMessage generic if server needs to match message.content type
//            val byteArray = ByteArray(1024).let {
//                val bytesRead = readChannel.readAvailable(it)
//                if (bytesRead >= 0) it.copyOf(bytesRead) else ByteArray(0)
//            }

            SocketMessage(byteArray)
        } catch (t: Throwable) {
            throw SocketReadChannelException(t.message)
        }
    }

    override fun close() {
        try {
            closeReadChannel()
            closeWriteChannel()

            if (isConnectedRef.value) {
                connectedTcpSocket.close()
            }
        } catch (t: Throwable) {
            throw SocketException(t.message)
        }
    }
}
