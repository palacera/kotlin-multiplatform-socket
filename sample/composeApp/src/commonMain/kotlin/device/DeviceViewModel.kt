package device

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
import socket.api.TcpSocket
import socket.api.connection.SocketConnectionState
import socket.api.message.SocketMessage
import socket.api.message.SocketMessageReceipt

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

class DeviceViewModel(
    private val dispatcher: Dispatcher,
    private val socket: TcpSocket,
) : ScreenModel, CommonViewModel {

    val deviceIndex = intArrayOf(0)

    val deviceFlow: Flow<List<pool.DeviceConnectionState>> = flow {
        val deviceConnectionState = DeviceList().get(*deviceIndex).map { device ->
            pool.DeviceConnectionState(device, SocketConnectionState.Disconnected(device.socketConnection.socketAddress))
        }
        emit(deviceConnectionState)
    }

    val connectionStateFlow = socket.connectionStateFlow
        .filter { it.socketAddress.host != "" || it.socketAddress.port != 0 }
        .scanToArray { existing, new ->
            existing.socketAddress.host == new.socketAddress.host && existing.socketAddress.port == new.socketAddress.port
        }
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(), emptyList())

    val combinedFlow: StateFlow<List<pool.DeviceConnectionState>> = deviceFlow
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
                socket.connect()
            } catch (e: Exception) {
                // handle error
            }
        }
    }

    override fun disconnect(socketConnection: SocketConnection) {
        screenModelScope.launch {
            try {
                socket.disconnect()
            } catch (e: Exception) {
                // handle error
            }
        }
    }

    override fun disconnectAll() {}

    override fun dispose(socketConnection: SocketConnection) {
        screenModelScope.launch {
            try {
                socket.dispose()
            } catch (e: Exception) {
                // handle error
            }
        }
    }

    override fun disposeAll() {}

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
        //socket.dispose()
    }
}
