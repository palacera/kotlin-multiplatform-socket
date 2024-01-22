package socket.adapter

import d
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.TcpSocketBuilder
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.isClosed
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.cancel
import io.ktor.utils.io.close
import io.ktor.utils.io.core.toByteArray
import io.ktor.utils.io.errors.IOException
import io.ktor.utils.io.readUTF8Line
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.CoroutineScope
import socket.api.SocketAddress
import socket.api.SocketConnectionException
import socket.api.SocketException
import socket.api.SocketMessage
import socket.api.SocketReadChannelException
import socket.api.SocketWriteChannelException
import socket.api.TcpSocketAdapterConnectable
import socket.api.TcpSocketConfig
import tag

internal class TcpSocketAdapter(
    val socketAddress: SocketAddress,
    val config: TcpSocketConfig,
) : TcpSocketAdapterConnectable {

    private val scope = CoroutineScope(PlatformDispatcher.io)

    private val selectorManager by lazy { SelectorManager(PlatformDispatcher.io) }

    private lateinit var connectedTcpSocket: Socket

    override suspend fun isConnected(): Boolean = try {
        if (::connectedTcpSocket.isInitialized) {
            // TODO instead of sending a string, can send a byte or byte array?
            //writeChannel?.writeStringUtf8("\n")
            //readChannel?.readUTF8Line()
            !connectedTcpSocket.isClosed && !(writeChannel?.isClosedForWrite ?: true) && !(readChannel?.isClosedForRead ?: true)
        } else false
    } catch (e: Exception) {
        false
    }

    private val tcpSocketBuilder: TcpSocketBuilder by lazy {
        try {
            aSocket(selectorManager).tcp()
        } catch (e: Exception) {
            throw SocketException(e.message)
        }
    }

    private var readChannel: ByteReadChannel? = null

    private var writeChannel: ByteWriteChannel? = null

    private fun Socket.connectReadChannel() {
        readChannel = try {
            openReadChannel()
        } catch (e: Exception) {
            throw SocketReadChannelException(e.message)
        }
    }

    private fun Socket.disconnectReadChannel() {
        readChannel?.cancel()
    }

    private fun Socket.connectWriteChannel() {
        writeChannel = try {
            openWriteChannel(autoFlush = true) //config.writeAutoFlush)
        } catch (e: Exception) {
            throw SocketWriteChannelException(e.message)
        }
    }

    private fun Socket.disconnectWriteChannel() {
        writeChannel?.close()
    }

    override suspend fun connect() {
        if (isConnected()) return

        try {
            connectedTcpSocket = tcpSocketBuilder.connect(socketAddress.host, socketAddress.port) {
                resolve(config)
            }.apply {
                connectReadChannel()
                connectWriteChannel()
            }
        } catch (e: Exception) {
            throw SocketConnectionException(e.message)
        }
    }

    override suspend fun send(message: ByteArray) {
        try {
            // TODO might need to make SocketMessage generic if server needs to match message.content type
            //writeChannel.writeFully(message.content)
            writeChannel?.writeStringUtf8("${message.decodeToString()}\n")
        } catch (e: IOException) {
            throw SocketConnectionException(e.message)
        } catch (e: Exception) {
            throw SocketWriteChannelException(e.message)
        }
    }

    override suspend fun receive(): ByteArray {
        return try {
            val message = readChannel?.readUTF8Line()

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

            byteArray
        } catch (e: Exception) {
            throw SocketReadChannelException(e.message)
        }
    }

    override fun close() {
        try {
            if (::connectedTcpSocket.isInitialized) {
                connectedTcpSocket.apply {
                    disconnectReadChannel()
                    disconnectWriteChannel()
                    close()
                }
            }
        } catch (e: Exception) {
            throw SocketException(e.message)
        }
    }
}
