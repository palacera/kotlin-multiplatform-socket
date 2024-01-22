package pool

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
import kotlinx.coroutines.channels.Channel
import socket.api.connection.SocketConnectionState
import socket.api.ping.PingRequest
import socket.api.pool.SocketConnectionPool
import socket.api.pool.TcpSocketConnectionPool
import socket.api.pool.TcpSocketConnectionPoolConfig

class PoolScreen : Screen {

    @Composable
    override fun Content() {
        val dispatcher = remember { DispatcherProvider() }
        val poolViewModel = rememberScreenModel {
            PoolViewModel(
                dispatcher = dispatcher,
                socketConnectionPool = SocketConnectionPool(
                    channel = Channel(),
                    connectionPoolConfig = TcpSocketConnectionPoolConfig(),
                    dispatcher = dispatcher,
                )
            )
        }
        val combined = poolViewModel.combinedFlow.collectAsState(listOf())

        LaunchedEffect(Unit) {
            poolViewModel.connect(combined.value.toSocketConnection())
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(
                start = 16.dp,
                top = 16.dp,
                end = 16.dp,
                bottom = 16.dp
            )
        ) {
            items(items = combined.value, key = { it.device.id }) { deviceConnectionState ->
                DeviceItem(deviceConnectionState, poolViewModel)
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    @Composable
    fun DeviceItem(deviceConnectionState: DeviceConnectionState, viewModel: PoolViewModel) {

        KeyValueRow("Name:", deviceConnectionState.device.name)
        KeyValueRow("Address:", deviceConnectionState.device.socketConnection.socketAddress.address)
        KeyValueRow("State:", deviceConnectionState.connectionState::class.simpleName ?: "N/A")
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            enabled = (deviceConnectionState.device.socketConnection.pingRequest == PingRequest.None && deviceConnectionState.connectionState is SocketConnectionState.Connected)
                || deviceConnectionState.connectionState is SocketConnectionState.ConnectionConfirmed,
            onClick = {
                viewModel.send(deviceConnectionState.connectionState.socketAddress, deviceConnectionState.device.testMessage)
            }
        ) {
            Text("Send")
        }
    }

    @Composable
    fun KeyValueRow(key: String, value: String) {
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
