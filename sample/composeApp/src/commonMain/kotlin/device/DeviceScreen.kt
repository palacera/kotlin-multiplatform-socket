package device

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import dispatcher.DispatcherProvider
import io.ktor.utils.io.core.toByteArray
import socket.api.ping.PingRequest
import socket.api.SocketAddress
import socket.api.SocketConnection
import socket.api.message.SocketMessage
import socket.api.message.SocketMessageReceipt
import socket.api.TcpSocket
import socket.api.TcpSocketConfig


class DeviceScreen : Screen {

    @Composable
    override fun Content() {

        val scope = rememberCoroutineScope()

        val socketConnectionPool = remember {
            //TcpSocketConnectionPool()
            TcpSocket(
                SocketConnection(
                    socketAddress = SocketAddress(
                        "10.0.2.2", 9001
                    )
                ),
                TcpSocketConfig(),
                DispatcherProvider(),
            )
        }

        val viewModel = rememberScreenModel { DeviceViewModel(PlatformDispatcher.io, socketConnectionPool) }
        val deviceList = viewModel.deviceListFlow.collectAsState()

        LazyColumn(

            modifier = Modifier.fillMaxWidth().padding(
                start = 16.dp,
                top = 16.dp,
                end = 16.dp,
                bottom = 16.dp
            )
        ) {
            items(items = deviceList.value, key = { it.id }) { device ->
                DeviceItemOld(device, viewModel)
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    @Composable
    fun DeviceItemOld(device: Device, viewModel: DeviceViewModel) {

        LaunchedEffect(Unit) {
            val pingRequest = PingRequest.Config(
                requestMessage = SocketMessage { "ping".toByteArray() },
                messageReceipt = SocketMessageReceipt {
                    it.decodeToString() == "pong"
                }
            )
            viewModel.ping(device)
        }

        KeyValueRowOld("Name:", device.name)
        KeyValueRowOld("State:", device.connectionState::class.simpleName ?: "N/A")
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            enabled = device.isConnected,
            onClick = {
                viewModel.send(device, SocketMessage { "{\"cmd\":\"OPEN\",\"msgId\":250,\"dir\":\"p2d\"}".toByteArray() })
            }) {
            Text("Send")
        }
    }

    @Composable
    fun KeyValueRowOld(key: String, value: String) {
        val commonFontSize = 16.sp // Define a common font size for both texts

        Row {
            Text(
                text = buildAnnotatedString {
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(key)
                    }
                },
                style = TextStyle(fontSize = commonFontSize), // Apply the common font size
                modifier = Modifier.width(80.dp) // Adjust the width as needed
            )
            Text(
                text = value,
                style = TextStyle(fontSize = commonFontSize) // Apply the same font size
            )
        }
    }
}
