package socket.api.pool

sealed interface SocketConnectionPoolDataStructure {
    data object Queue : SocketConnectionPoolDataStructure // FIFO
    data object Stack : SocketConnectionPoolDataStructure // LIFO
}
