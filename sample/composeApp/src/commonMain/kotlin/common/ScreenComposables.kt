package common

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.Button
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pool.DeviceConnectionState
import pool.toSocketConnection
import socket.api.SocketConnection
import socket.api.connection.SocketConnectionState
import socket.api.ping.PingRequest

@Composable
fun DeviceItem(deviceConnectionState: DeviceConnectionState, viewModel: CommonViewModel) {

    LaunchedEffect(Unit) {
        viewModel.connect(deviceConnectionState.toSocketConnection())
    }

    val isConnected = (deviceConnectionState.device.socketConnection.pingRequest == PingRequest.None
        && deviceConnectionState.connectionState is SocketConnectionState.Connected)
        || deviceConnectionState.connectionState is SocketConnectionState.ConnectionConfirmed

    val isConfirmingConnection = deviceConnectionState.connectionState is SocketConnectionState.ConfirmingConnection

    val isConnecting = deviceConnectionState.connectionState is SocketConnectionState.Connecting
        || deviceConnectionState.connectionState is SocketConnectionState.AwaitingConfirmation

    KeyValueRow("Name:", deviceConnectionState.device.name)
    KeyValueRow("Address:", deviceConnectionState.device.socketConnection.socketAddress.address)
    KeyValueRow("State:", deviceConnectionState.connectionState::class.simpleName ?: "N/A")
    Spacer(modifier = Modifier.height(8.dp))

    Row(verticalAlignment = Alignment.CenterVertically) {
        if (isConnecting) {
            deviceConnectingButton(deviceConnectionState.device.socketConnection, viewModel)
        } else if (isConnected || isConfirmingConnection) {
            deviceDisconnectButton(deviceConnectionState.device.socketConnection, viewModel)
        } else {
            deviceConnectButton(deviceConnectionState.device.socketConnection, viewModel)
        }

        Spacer(modifier = Modifier.width(8.dp))

        deviceDisposeButton(deviceConnectionState.device.socketConnection, viewModel)

        Spacer(modifier = Modifier.width(8.dp))

        Button(
            enabled = isConnected || isConfirmingConnection,
            onClick = {
                viewModel.send(deviceConnectionState.connectionState.socketAddress, deviceConnectionState.device.testMessage)
            }
        ) {
            Text("Send")
        }

        Spacer(modifier = Modifier.width(8.dp))

        if (isConnected) {
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = "Heartbeat",
                tint = Color.Red,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
fun connectAllButton(devices: List<DeviceConnectionState>, viewModel: CommonViewModel) {
    Button(
        enabled = true,
        onClick = {
            devices.forEach {
                viewModel.connect(it.toSocketConnection())
            }
        },
    ) {
        Text("Connect All")
    }
}

@Composable
fun disconnectAllButton(devices: List<DeviceConnectionState>, viewModel: CommonViewModel) {
    Button(
        enabled = true,
        onClick = { viewModel.disconnectAll() },
    ) {
        Text("Disconnect All")
    }
}

@Composable
fun disposeAllButton(viewModel: CommonViewModel) {
    Button(
        enabled = true,
        onClick = { viewModel.disposeAll() },
    ) {
        Text("Dispose All")
    }
}

@Composable
fun deviceConnectionButton(
    enabled: Boolean,
    text: String,
    onClick: () -> Unit = {},
) {
    Button(
        enabled = enabled,
        onClick = onClick,
    ) {
        Text(text)
    }
}

@Composable
fun deviceConnectButton(socketConnection: SocketConnection, viewModel: CommonViewModel) {
    deviceConnectionButton(true, "Connect") {
        viewModel.connect(socketConnection)
    }
}

@Composable
fun deviceConnectingButton(socketConnection: SocketConnection, viewModel: CommonViewModel) {
    deviceConnectionButton(true, "Cancel") {
        viewModel.disconnect(socketConnection)
    }
}

@Composable
fun deviceDisconnectButton(socketConnection: SocketConnection, viewModel: CommonViewModel) {
    deviceConnectionButton(true, "Disconnect") {
        viewModel.disconnect(socketConnection)
    }
}

@Composable
fun deviceDisposeButton(socketConnection: SocketConnection, viewModel: CommonViewModel) {
    deviceConnectionButton(true, "Dispose") {
        viewModel.dispose(socketConnection)
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
