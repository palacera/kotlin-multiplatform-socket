package com.palacera.kmpsocket

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.ServerSocket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.writeStringUtf8
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

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

//fun main() = runBlocking {
//    val selectorManager = SelectorManager(Dispatchers.IO)
//
//    while (isActive) {
//        startServer(selectorManager)
//
//        println("Waiting to restart...")
//        delay(10.seconds)
//    }
//}
//
//suspend fun startServer(selectorManager: SelectorManager) = withTimeoutOrNull(10.seconds) {
//    val serverSocket = aSocket(selectorManager).tcp().bind("127.0.0.1", 9001)
//    println("Server is listening at ${serverSocket.localAddress}")
//
//    try {
//        while (isActive) {
//            val socket = serverSocket.accept()
//            println("Accepted $socket")
//            launch {
//                val receiveChannel = socket.openReadChannel()
//                val sendChannel = socket.openWriteChannel(autoFlush = true)
//                try {
//                    while (isActive) {
//                        val input = receiveChannel.readUTF8Line(1000)
//                        val output = when (input) {
//                            "ping" -> "pong"
//                            else -> input
//                        }
//                        sendChannel.writeStringUtf8("$output\n")
//                    }
//                } catch (e: Throwable) {
//                    socket.close()
//                }
//            }
//        }
//    } catch (e: TimeoutCancellationException) {
//        println("Server timed out")
//    } finally {
//        serverSocket.close()
//    }
//}
