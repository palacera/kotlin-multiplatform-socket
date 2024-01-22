package common

import socket.api.SocketAddress
import socket.api.SocketConnection
import socket.api.message.SocketMessage
import socket.api.message.SocketMessageReceipt

interface CommonViewModel {

    fun connect(socketConnection: SocketConnection)

    fun disconnect(socketConnection: SocketConnection)

    fun disconnectAll()

    fun dispose(socketConnection: SocketConnection)

    fun disposeAll()

    fun send(socketAddress: SocketAddress, message: SocketMessage, messageReceipt: SocketMessageReceipt? = null)
}
