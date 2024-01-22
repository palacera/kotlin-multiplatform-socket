package pool

import Device
import DeviceList
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import common.CommonViewModel
import dispatcher.Dispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import scanToArray
import socket.api.SocketAddress
import socket.api.SocketConnection
import socket.api.connection.SocketConnectionState
import socket.api.message.SocketMessage
import socket.api.message.SocketMessageReceipt
import socket.api.pool.SocketConnectionPool

// TODO remove this
fun List<DeviceConnectionState>.toSocketConnection() = map {
    SocketConnection(
        socketAddress = it.device.socketConnection.socketAddress,
        socketConfig = it.device.socketConnection.socketConfig,
        pingRequest = it.device.socketConnection.pingRequest,
    )
}

fun DeviceConnectionState.toSocketConnection() = SocketConnection(
    socketAddress = device.socketConnection.socketAddress,
    socketConfig = device.socketConnection.socketConfig,
    pingRequest = device.socketConnection.pingRequest,
)

data class DeviceConnectionState(
    val device: Device,
    val connectionState: SocketConnectionState,
)

class PoolViewModel(
    private val dispatcher: Dispatcher,
    private val socketConnectionPool: SocketConnectionPool,
) : ScreenModel, CommonViewModel {

    val deviceIndex = intArrayOf(0, 2, 3)

    val deviceFlow: Flow<List<DeviceConnectionState>> = flow {
        val deviceConnectionState = DeviceList().get(*deviceIndex).map { device ->
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


    override fun connect(socketConnection: SocketConnection) {
        screenModelScope.launch {
            try {
                socketConnectionPool.connectSocket(socketConnection)
            } catch (e: Exception) {
                // handle error
            }
        }
    }

    override fun disconnect(socketConnection: SocketConnection) {
        screenModelScope.launch {
            try {
                socketConnectionPool.disconnectSocket(socketConnection)
            } catch (e: Exception) {
                // handle error
            }
        }
    }

    override fun disconnectAll() {
        screenModelScope.launch {
            try {
                socketConnectionPool.disconnectAllSockets()
            } catch (e: Exception) {
                // handle error
            }
        }
    }

    override fun dispose(socketConnection: SocketConnection) {
        screenModelScope.launch {
            try {
                socketConnectionPool.disposeSocket(socketConnection)
            } catch (e: Exception) {
                // handle error
            }
        }
    }

    override fun disposeAll() {
        screenModelScope.launch {
            try {
                socketConnectionPool.disposeAllSockets()
            } catch (e: Exception) {
                // handle error
            }
        }
    }

    override fun send(socketAddress: SocketAddress, message: SocketMessage, messageReceipt: SocketMessageReceipt?) {
        screenModelScope.launch {
            try {
                //socketConnectionPool.send(socketAddress, message, messageReceipt)
            } catch (e: Exception) {
                // handle error
            }
        }
    }

    override fun onDispose() {
        super.onDispose()
        socketConnectionPool.dispose()
    }
}
