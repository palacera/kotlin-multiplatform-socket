import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import io.ktor.utils.io.core.toByteArray
import socket.api.PingRequest
import socket.api.SocketAddress
import socket.api.SocketMessage
import socket.api.SocketMessageReceipt
import socket.api.SocketMessageState
import socket.api.TcpSocketConnectionPool

class DeviceScreen : Screen {

    @Composable
    override fun Content() {

        val scope = rememberCoroutineScope()

        val socketConnectionPool = remember {
            TcpSocketConnectionPool()
        }

        val viewModel = rememberScreenModel { DeviceViewModel(PlatformDispatcher.io, socketConnectionPool) }
        val deviceList = viewModel.deviceListFlow.collectAsState()

        val messageState = viewModel.deviceMessageState.collectAsState(SocketMessageState.Idle(SocketAddress("", 0)))

//        LaunchedEffect(Unit) {
//            deviceList.value.forEach { device ->
//                val pingRequest = PingRequest(
//                    requestMessage = SocketMessage("ping".toByteArray()),
//                    messageReceipt = SocketMessageReceipt {
//                        it.decodeToString() == "pong"
//                    }
//                )
//                viewModel.ping(device, pingRequest)
//            }
//        }


        Text(messageState.value.toString())
        Spacer(modifier = Modifier.height(48.dp))

        LazyColumn {
            items(items = deviceList.value, key = { it.id }) { device ->
                DeviceItem(device, viewModel)
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    @Composable
    fun DeviceItem(device: Device, viewModel: DeviceViewModel) {

        LaunchedEffect(Unit) {
            val pingRequest = PingRequest(
                requestMessage = SocketMessage("ping".toByteArray()),
                messageReceipt = SocketMessageReceipt {
                    it.decodeToString() == "pong"
                }
            )
            viewModel.ping(device, pingRequest)
        }

        Text(text = "Device ID: ${device.name}")
        Text(text = "Device ID: ${device.connectionState}")
        println("xxxx" + device.connectionState.toString())
        Button(
            enabled = device.isConnected,
            onClick = {
                viewModel.send(device, SocketMessage("{\"cmd\":\"OPEN\",\"msgId\":250,\"dir\":\"p2d\"}".toByteArray()))
            }) {
            Text("Send")
        }
    }
}
