import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlin.random.Random
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import pool.PoolScreen

sealed class ConnectionState {
    data object Connecting : ConnectionState()
    data object Connected : ConnectionState()
    data object ConfirmingConnection : ConnectionState()
    data object ConnectionConfirmed : ConnectionState()
    data object Disconnecting : ConnectionState()
    data object Disconnected : ConnectionState()
    data class Error(val exception: Exception) : ConnectionState()
}


class Socket(
    val id: Int = 1,
) {
    private val _connectionState: MutableStateFlow<ConnectionState> = MutableStateFlow(ConnectionState.Disconnected)
    val connectionState = _connectionState.asStateFlow()

    var isConnected: Boolean = false

    suspend fun connect() {
        _connectionState.value = ConnectionState.Connecting
        delay(Random.nextLong(500.milliseconds.inWholeMilliseconds, 3.seconds.inWholeMilliseconds))
        isConnected = true
        _connectionState.value = ConnectionState.Connected
    }

    fun disconnect() {
        _connectionState.value = ConnectionState.Disconnecting
        isConnected = false
        _connectionState.value = ConnectionState.Connecting
    }
}










@Composable
fun App() {

//    runBlocking {
//        val channel = Channel<Int>(0)
//        launch {
//            repeat(10) {
//                yield()
//                println("$it: Customer ordering")
//                channel.send(it)
//                println("$it: Customer paying")
//                delay(5.seconds)
//                println("$it: Customer leaves line")
//            }
//        }
//
//        repeat(1) {
//            val baristaNum = it + 1
//            launch {
//                //delay(500.milliseconds)
//                yield()
//                for (it in channel) {
//                    println("$it: Barista #$baristaNum making")
//                    delay(1.seconds)
//                    println("$it: Order complete")
//                }
//            }
//        }
//    }


//    runBlocking {
//        val connectionPool = ConnectionPool(
//            channel = Channel(2),
//            maxAsyncConnections = 1,
//            connectionBufferCapacity = 10,
//        )
//        for (i in 1..10) {
//            val socket = Socket(i)
//            connectionPool.connect(socket)
//        }
//
//        delay(30.seconds)
//        connectionPool.close()
//    }


    MaterialTheme {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            PoolScreen().Content()
            //DeviceScreen().Content()
            //MainScreen().Content()
        }
    }
}
