package com.palacera.kmpsocket

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.cancel
import io.ktor.utils.io.close
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.writeAvailable
import io.ktor.utils.io.writeStringUtf8
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

val timeoutInterval = 5.seconds

fun main() = runBlocking {
    val selectorManager = SelectorManager(Dispatchers.IO)

    while (isActive) {
        startServer(selectorManager)

        println("Waiting to restart...")
        delay(10.seconds)
    }
}

suspend fun startServer(selectorManager: SelectorManager) = withTimeoutOrNull(timeoutInterval) {
    val serverSocket = aSocket(selectorManager).tcp().bind("127.0.0.1", 9002)
    println("Server is listening at ${serverSocket.localAddress}")

    val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    try {
        while (isActive) {
            val socket = serverSocket.accept()
            println("Accepted $socket")
            scope.launch {
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
                    receiveChannel.cancel()
                    sendChannel.close()
                    socket.close()
                }
            }
        }
    } catch (e: TimeoutCancellationException) {
        println("Server timed out")
    } finally {
        serverSocket.close()
        scope.cancel()
    }
}
