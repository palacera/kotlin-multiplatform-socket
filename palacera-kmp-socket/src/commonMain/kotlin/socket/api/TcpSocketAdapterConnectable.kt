package socket.api

interface TcpSocketAdapterConnectable {
    suspend fun isConnected(): Boolean
    suspend fun connect()
    suspend fun send(message: ByteArray)
    suspend fun receive(): ByteArray
    fun close()
}
