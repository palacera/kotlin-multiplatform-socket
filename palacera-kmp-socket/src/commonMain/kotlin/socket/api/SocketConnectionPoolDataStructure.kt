package socket.api

sealed interface SocketConnectionPoolDataStructure {
    data object Queue : SocketConnectionPoolDataStructure // FIFO
    data object Stack : SocketConnectionPoolDataStructure // LIFO
}
