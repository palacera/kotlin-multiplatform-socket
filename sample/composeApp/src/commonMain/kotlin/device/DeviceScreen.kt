package device

import DeviceList
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import common.DeviceItem
import common.connectAllButton
import common.disconnectAllButton
import dispatcher.DispatcherProvider
import kotlinx.coroutines.channels.Channel
import pool.DeviceConnectionState
import pool.PoolViewModel
import pool.toSocketConnection
import socket.api.SocketAddress
import socket.api.SocketConnection
import socket.api.TcpSocket
import socket.api.connection.SocketConnectionState
import socket.api.pool.SocketConnectionPool
import socket.api.pool.TcpSocketConnectionPoolConfig

class DeviceScreen : Screen {

    @Composable
    override fun Content() {
        val dispatcher = remember { DispatcherProvider() }

        val deviceIndex = remember { intArrayOf(2) }
        val device = remember {
            DeviceList().get(*deviceIndex).first()
        }

        val viewModel = rememberScreenModel {
            DeviceViewModel(
                dispatcher = dispatcher,
                socket = TcpSocket(
                    socketConnection = device.socketConnection,
                    dispatcher = dispatcher
                )
            )
        }
        val combined = viewModel.combinedFlow.collectAsState(listOf())


        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(
                start = 16.dp,
                top = 16.dp,
                end = 16.dp,
                bottom = 16.dp
            )
        ) {
            items(items = combined.value, key = { it.device.id }) { deviceConnectionState ->
                DeviceItem(deviceConnectionState, viewModel)
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
