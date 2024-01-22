import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ConnectionPoolBak(
    private val channel: Channel<Socket>,
    private val maxAsyncConnections: Int = 3, // higher number is faster but connecting more sockets at once
    private val connectionBufferCapacity: Int = 12, // higher number has less reconnections but stores more connections in array
    private val confirmConnectionInterval: Duration = 1.seconds,
) {
    private val scope = CoroutineScope(PlatformDispatcher.io)

    private val connectionBuffer = UniqueCircularBuffer<Socket>(connectionBufferCapacity)

    private val numAsyncConnections = atomic(0)

    @OptIn(DelicateCoroutinesApi::class)
    fun connect(socket: Socket) = scope.launch {
        if (channel.isClosedForSend) cancel()
        receive()
        channel.send(socket)
    }

    @OptIn(DelicateCoroutinesApi::class)
    private suspend fun receive() {
        if (numAsyncConnections.value < maxAsyncConnections && !channel.isClosedForReceive) {
            numAsyncConnections.value++
            observeConnections()
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun sendWithDelay(socket: Socket, delay: Duration = confirmConnectionInterval) = scope.launch {
        if (channel.isClosedForSend) cancel()
        delay(delay)
        channel.send(socket)
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun observeConnections() = scope.launch {
        while (isActive && !channel.isClosedForReceive) {

            var socket: Socket? = null
            try {
                socket = channel.receive()

                val isConnected = connectionBuffer.contains(socket) && socket.isConnected

                if (!isConnected) {
                    d(tag("pooltest")) { "NOT connected: ${socket.id}" }

                    socket.connect()

                    // what for connection to be established
                    while(!socket.isConnected) {
                        delay(100.milliseconds)
                    }

                    connectionBuffer.remove()?.also { it.disconnect() }
                    connectionBuffer.add(socket)
                }

                if (socket.isConnected) {
                    d(tag("pooltest")) { "Connected: ${socket.id}" }
                } else {
                    d(tag("pooltest")) { "NOT Connected: ${socket.id}" }
                }

            } catch (e: ClosedReceiveChannelException) {
                e(tag("pooltest")) { e.message }
            } catch (e: ClosedSendChannelException) {
                e(tag("pooltest")) { e.message }
            } catch (e: Exception) {
                e(tag("pooltest")) { e.message }
            } finally {
                if (socket != null && !channel.isClosedForSend) {
                    sendWithDelay(socket)
                }
            }
        }
    }

    fun close() {
        this.channel.close()
        scope.cancel()
    }
}
