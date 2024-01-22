package socket.adapter

import io.ktor.network.sockets.SocketOptions
import kotlin.time.DurationUnit
import socket.api.TcpSocketConfig

internal fun SocketOptions.TCPClientSocketOptions.resolve(config: TcpSocketConfig) {
    keepAlive = config.keepAlive
    socketTimeout = config.socketTimeout.toLong(DurationUnit.MILLISECONDS)
    lingerSeconds = config.lingerSeconds
    noDelay = config.noDelay
    reuseAddress = config.reuseAddress
    reusePort = config.reusePort
    receiveBufferSize = config.receiveBufferSize.toInt()
    sendBufferSize = config.sendBufferSize.toInt()
    //typeOfService = typeOfService
}
