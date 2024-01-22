import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.painterResource

@OptIn(ExperimentalResourceApi::class)
@Composable
fun App() {
    MaterialTheme {
        var showContent by remember { mutableStateOf(false) }
        val greeting = remember { Greeting().greet() }

        /*
        actual class TcpSocket {
    private val socket = Socket()
    private lateinit var input: BufferedReader
    private lateinit var output: BufferedWriter

    actual fun connect(host: String, port: Int) {
        socket.connect(InetSocketAddress(host, port))
        input = BufferedReader(InputStreamReader(socket.getInputStream()))
        output = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))
    }

    actual fun send(data: String) {
        output.write(data)
        output.flush()
    }

    actual fun receive(): String {
        return input.readLine()
    }

    actual fun close() {
        socket.close()
    }
}
         */

        LaunchedEffect(Unit) {
            CoroutineScope(PlatformDispatcher.io).launch {
                try {
                    val selectorManager = SelectorManager(PlatformDispatcher.io)
                    val socket = aSocket(selectorManager).tcp().connect("192.168.18.72", 3000)

                    val receiveChannel = socket.openReadChannel()
                    val sendChannel = socket.openWriteChannel(autoFlush = true)

                    sendChannel.writeStringUtf8("{\"cmd\":\"OPEN\",\"msgId\":250,\"dir\":\"p2d\"}")

                    // Handle incoming data...
                    // Note: This is a simplified example. You'll need to implement the logic
                    // to properly read and process incoming data from receiveChannel

                } catch (e: Exception) {
                    // Handle exception
                    println("Errorxxx: ${e.message}")
                }
            }
        }


        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Button(onClick = { showContent = !showContent }) {
                Text("Click me!")
            }
            AnimatedVisibility(showContent) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(painterResource("compose-multiplatform.xml"), null)
                    Text("Compose: $greeting")
                }
            }
        }
    }
}
