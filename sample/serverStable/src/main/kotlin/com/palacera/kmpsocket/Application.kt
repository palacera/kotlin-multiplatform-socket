package com.palacera.kmpsocket

import PlatformDispatcher
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.writeAvailable
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

fun main() {
    runBlocking {
        val selectorManager = SelectorManager(PlatformDispatcher.io)
        val serverSocket = aSocket(selectorManager).tcp().bind("127.0.0.1", 9001)
        println("Server is listening at ${serverSocket.localAddress}")
        while (isActive) {
            val socket = serverSocket.accept()
            println("Accepted $socket")
            launch {
                val receiveChannel = socket.openReadChannel()
                val sendChannel = socket.openWriteChannel(autoFlush = true)
                try {
                    while (isActive) {
                        val numBytesAvailable = receiveChannel.availableForRead.takeIf { it > 0 } ?: 1024
                        val input = ByteArray(numBytesAvailable).let {
                            val bytesRead = receiveChannel.readAvailable(it)
                            it.copyOf(bytesRead)
                        }
                        val output = when (input.decodeToString()) {
                            "ping" -> "pong".toByteArray()
                            else -> input
                        }
                        sendChannel.writeAvailable(output)
                        println("Writing ${output.decodeToString()}")
                    }
                } catch (e: Throwable) {
                    socket.close()
                }
            }
        }
    }
}
