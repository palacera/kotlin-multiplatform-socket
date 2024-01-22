package socket.api

interface TcpSocketConnectable2 {
    suspend fun connect()
    fun close()
}

interface TcpSocketConnectable {
    suspend fun connect(pingRequest: PingRequest? = null)
    suspend fun send(message: SocketMessage, messageReceipt: SocketMessageReceipt? = null)
    suspend fun receive(): SocketMessage
    fun close()
}
