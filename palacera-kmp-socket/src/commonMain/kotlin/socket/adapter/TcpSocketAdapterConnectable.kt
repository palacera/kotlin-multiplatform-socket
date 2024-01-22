package socket.adapter

import socket.api.SocketMessage

internal interface TcpSocketAdapterConnectable {
    suspend fun connect()
    suspend fun send(message: SocketMessage)
    suspend fun receive(): SocketMessage
    fun close()
}
