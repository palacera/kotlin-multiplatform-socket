package pool

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key.Companion.R
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import common.DeviceItem
import common.connectAllButton
import common.disconnectAllButton
import common.disposeAllButton
import dispatcher.DispatcherProvider
import kotlinx.coroutines.channels.Channel
import org.jetbrains.compose.resources.painterResource
import socket.api.SocketConnection
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
                    channel = Channel(capacity = Channel.UNLIMITED),
                    connectionPoolConfig = TcpSocketConnectionPoolConfig(),
                    dispatcher = dispatcher,
                )
            )
        }
        val combined = poolViewModel.combinedFlow.collectAsState(listOf())


        val allDisconnected = combined.value.all {
            it.connectionState is SocketConnectionState.Disconnected
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(
                start = 16.dp,
                top = 16.dp,
                end = 16.dp,
                bottom = 0.dp
            )
        ) {
            if (allDisconnected) {
                connectAllButton(combined.value, poolViewModel)
            } else {
                disconnectAllButton(combined.value, poolViewModel)
            }

            Spacer(modifier = Modifier.width(8.dp))

            disposeAllButton(poolViewModel)
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
}
