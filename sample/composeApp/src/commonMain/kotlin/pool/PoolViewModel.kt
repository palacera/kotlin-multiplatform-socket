package pool

import Device
import DeviceList
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import d
import dispatcher.Dispatcher
import e
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import scanToArray
import socket.api.SocketAddress
import socket.api.SocketConnection
import socket.api.connection.SocketConnectionState
import socket.api.message.SocketMessage
import socket.api.message.SocketMessageReceipt
import socket.api.pool.SocketConnectionPool
import socket.api.pool.TcpSocketConnectionPool
import tag

fun List<DeviceConnectionState>.toSocketConnection() = map {
    SocketConnection(
        socketAddress = it.device.socketConnection.socketAddress,
        pingRequest = it.device.socketConnection.pingRequest,
    )
}

data class DeviceConnectionState(
    val device: Device,
    val connectionState: SocketConnectionState,
)

class PoolViewModel(
    private val dispatcher: Dispatcher,
    private val socketConnectionPool: SocketConnectionPool,
) : ScreenModel {

    val deviceFlow: Flow<List<DeviceConnectionState>> = flow {
        val deviceConnectionState = DeviceList().get().map { device ->
            DeviceConnectionState(device, SocketConnectionState.Disconnected(device.socketConnection.socketAddress))
        }
        emit(deviceConnectionState)
    }

    val connectionStateFlow = socketConnectionPool.connectionStateFlow
        .filter { it.socketAddress.host != "" || it.socketAddress.port != 0 }
        .scanToArray { existing, new ->
            existing.socketAddress.host == new.socketAddress.host && existing.socketAddress.port == new.socketAddress.port
        }
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(), emptyList())

    val combinedFlow: StateFlow<List<DeviceConnectionState>> = deviceFlow
        .combine(connectionStateFlow) { devices, states ->
            devices.map { device ->
                states.find {
                    it.socketAddress == device.connectionState.socketAddress
                }?.let { state ->
                    device.copy(connectionState = state)
                } ?: device
            }
        }
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(), emptyList())


    fun connect(socketConnections: List<SocketConnection>) {
        screenModelScope.launch(dispatcher.io) {
            try {
                socketConnectionPool.connect(socketConnections)
            } catch (e: Exception) {
                e(tag("pool", "error")) { e.message }
                // handle error
            }
        }
    }

    fun connect(socketConnection: SocketConnection) {
        screenModelScope.launch(dispatcher.io) {
            try {
                socketConnectionPool.connect(socketConnection)
            } catch (e: Exception) {
                // handle error
            }
        }
    }

    fun send(socketAddress: SocketAddress, message: SocketMessage, messageReceipt: SocketMessageReceipt? = null) {
        screenModelScope.launch(dispatcher.io) {
            try {
                //socketConnectionPool.send(socketAddress, message, messageReceipt)
            } catch (e: Exception) {
                // handle error
            }
        }
    }
}
