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
import cafe.adriel.voyager.core.screen.Screen
import socket.api.SocketAddress
import socket.api.SocketConnectionState
import socket.api.SocketMessage

class DeviceScreen : Screen {

    @Composable
    override fun Content() {

        val deviceListIndex = -1 // change this to display a specific device, -1 for all
        val deviceList = remember { DeviceList().get(deviceListIndex) }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(
                start = 16.dp,
                top = 16.dp,
                end = 16.dp,
                bottom = 16.dp
            )
        ) {
            items(items = deviceList, key = { it.id }) { device ->
                DeviceItem(device)
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    @Composable
    fun DeviceItem(device: Device) {

        val connectionState = device.viewModel.connectionStateFlow
            .collectAsState(SocketConnectionState.Disconnected(SocketAddress(device.host, device.port)))

        LaunchedEffect(Unit) {
            device.viewModel.ping(device)
        }

        KeyValueRow("Name:", device.name)
        KeyValueRow("Address:", "${device.host}:${device.port}")
        KeyValueRow("State:", connectionState!!.value::class.simpleName ?: "N/A")
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            enabled = (device.pingRequest == null && connectionState.value is SocketConnectionState.Connected)
                || connectionState.value is SocketConnectionState.ConnectionConfirmed,
            onClick = {
                device.viewModel.send(device, device.testMessage)
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
