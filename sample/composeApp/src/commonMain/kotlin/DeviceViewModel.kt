import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.getAndUpdate
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import socket.api.PingRequest
import socket.api.SocketAddress
import socket.api.SocketConnectionState
import socket.api.SocketMessage
import socket.api.SocketMessageReceipt
import socket.api.SocketMessageState
import socket.api.TcpSocketConnectionPool

data class Device(
    val id: String,
    val name: String,
    val address: String,
    val port: Int,
    val connectionState: SocketConnectionState = SocketConnectionState.Disconnected(SocketAddress(address, port))
) {
    val isConnected = connectionState is SocketConnectionState.ConnectionVerified
}


class DeviceViewModel(
    private val dispatcher: CoroutineDispatcher = PlatformDispatcher.io,
    private val socketConnectionPool: TcpSocketConnectionPool,
): ScreenModel {

    private val _deviceListFlow: MutableStateFlow<List<Device>> = MutableStateFlow(emptyList())
    //val deviceListFlow: StateFlow<List<Device>> = _deviceListFlow.asStateFlow()

    private val deviceStateMap = atomic(mapOf<String, SocketConnectionState>())

    val connectionStateFlow = socketConnectionPool.connectionStateFlow
        .onStart { emit(SocketConnectionState.Disconnected(SocketAddress("", 0))) } // Emitting initial value for connection state

    val deviceListFlow = _deviceListFlow
        .combine(connectionStateFlow) { devices, state ->
            devices.map { device ->

                if (device.address == state.socketAddress.host && device.port == state.socketAddress.port) {
                    deviceStateMap.getAndUpdate { it + (device.id to state) }
                }

                val currentState = deviceStateMap.value[device.id]
                if (currentState == null) {
                    device
                } else {
                    device.copy(connectionState = currentState)
                }
            }
        }
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(), emptyList())


    val deviceMessageState = socketConnectionPool.messageStateFlow
        .onStart { emit(SocketMessageState.Idle(SocketAddress("", 0))) }


    init {
        _deviceListFlow.value = listOf(
            Device("1", "Device 1", "10.0.2.2", 9001),
            //Device("2", "Device 2", "10.0.2.2", 9002),
            //Device("1", "Device 1", "192.168.18.72", 3000),
            //Device("2", "Device 2", "192.168.18.190", 3000),
        )
    }

    fun ping(device: Device, pingRequest: PingRequest) {
        screenModelScope.launch(dispatcher) {
            val socket = SocketAddress(device.address, device.port)
            socketConnectionPool.connect(socket, pingRequest)
        }
    }

    fun send(device: Device, message: SocketMessage, messageReceipt: SocketMessageReceipt? = null) {
        screenModelScope.launch(dispatcher) {
            val socket = SocketAddress(device.address, device.port)
            socketConnectionPool.send(socket, message, messageReceipt)
        }
    }

    override fun onDispose() {
        super.onDispose()
        socketConnectionPool.disconnectAll()
    }
}
