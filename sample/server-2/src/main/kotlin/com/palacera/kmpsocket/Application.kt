package com.palacera.kmpsocket

import PlatformDispatcher
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

fun main() {
    runBlocking {
        val selectorManager = SelectorManager(PlatformDispatcher.io)
        val serverSocket = aSocket(selectorManager).tcp().bind("127.0.0.1", 9002)
        println("Server is listening at ${serverSocket.localAddress}")
        while (isActive) {
            val socket = serverSocket.accept()
            println("Accepted $socket")
            launch {
                val receiveChannel = socket.openReadChannel()
                val sendChannel = socket.openWriteChannel(autoFlush = true)
                try {
                    while (isActive) {
                        val input = receiveChannel.readUTF8Line(1000)
                        val output = when (input) {
                            "ping" -> "pong"
                            else -> input
                        }
                        sendChannel.writeStringUtf8("$output\n")
                    }
                } catch (e: Throwable) {
                    socket.close()
                }
            }
        }
    }
}
