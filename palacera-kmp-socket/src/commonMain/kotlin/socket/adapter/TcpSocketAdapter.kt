package socket.adapter

import dispatcher.Dispatcher
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
import io.ktor.utils.io.errors.IOException
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.writeAvailable
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.CoroutineScope
import socket.api.SocketAddress
import socket.api.SocketConnectionException
import socket.api.SocketException
import socket.api.SocketReadChannelException
import socket.api.SocketWriteChannelException
import socket.api.TcpSocketAdapterConnectable
import socket.api.TcpSocketConfig

internal class TcpSocketAdapter(
    val socketAddress: SocketAddress,
    val config: TcpSocketConfig,
    val dispatcher: Dispatcher,
) : TcpSocketAdapterConnectable {

    private val scope = CoroutineScope(dispatcher.io)

    private val selectorManager by lazy { SelectorManager(dispatcher.io) }

    private lateinit var connectedTcpSocket: Socket

    private var readChannel: ByteReadChannel? = null

    private var writeChannel: ByteWriteChannel? = null

    override suspend fun isConnected(): Boolean =
        !isSocketClosed() && !writeChannel.isClosedForWrite() && !readChannel.isClosedForRead()

    private fun isSocketClosed() = !::connectedTcpSocket.isInitialized || connectedTcpSocket.isClosed

    private fun ByteWriteChannel?.isClosedForWrite() = writeChannel?.isClosedForWrite ?: true

    private fun ByteReadChannel?.isClosedForRead() = writeChannel?.isClosedForWrite ?: true

    private val tcpSocketBuilder: TcpSocketBuilder by lazy {
        try {
            aSocket(selectorManager).tcp()
        } catch (e: Exception) {
            throw SocketException(e.message)
        }
    }

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
        writeChannel ?: throw SocketWriteChannelException("Write channel is not initialized.")

        try {
            writeChannel?.writeAvailable(message)
        } catch (e: IOException) {
            throw SocketConnectionException(e.message)
        } catch (e: Exception) {
            throw SocketWriteChannelException(e.message)
        }
    }

    override suspend fun receive(): ByteArray {
        readChannel ?: throw SocketReadChannelException("Read channel is not initialized.")

        return try {
            val numBytesAvailable = readChannel?.availableForRead?.takeIf { it > 0 } ?: 1024
            ByteArray(numBytesAvailable).let {
                val bytesRead = readChannel?.readAvailable(it) ?: 0
                it.copyOf(bytesRead)
            }
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
