package pool

//import cafe.adriel.voyager.core.model.ScreenModel
//import cafe.adriel.voyager.core.model.screenModelScope
//import kotlinx.atomicfu.atomic
//import kotlinx.atomicfu.getAndUpdate
//import kotlinx.coroutines.CoroutineDispatcher
//import kotlinx.coroutines.flow.MutableStateFlow
//import kotlinx.coroutines.flow.SharingStarted
//import kotlinx.coroutines.flow.combine
//import kotlinx.coroutines.flow.onStart
//import kotlinx.coroutines.flow.stateIn
//import kotlinx.coroutines.launch
//import socket.api.ping.PingRequest
//import socket.api.SocketAddress
//import socket.api.connection.SocketConnectionState
//import socket.api.message.SocketMessage
//import socket.api.message.SocketMessageReceipt
//import socket.api.message.SocketMessageState
//import socket.api.TcpSocket
//import socket.api.message.SocketMessageType
//
//data class DeviceOld(
//    val id: String,
//    val name: String,
//    val address: String,
//    val port: Int,
//    val connectionState: SocketConnectionState = SocketConnectionState.Disconnected(SocketAddress(address, port))
//) {
//    val isConnected = connectionState is SocketConnectionState.ConnectionConfirmed
//}
//
//
//class DeviceViewModel(
//    private val dispatcher: CoroutineDispatcher = PlatformDispatcher.io,
//    private val socketConnectionPool: TcpSocket, //TcpSocketConnectionPool,
//): ScreenModel {
//
//    private val _deviceListFlow: MutableStateFlow<List<DeviceOld>> = MutableStateFlow(emptyList())
//    //val deviceListFlow: StateFlow<List<Device>> = _deviceListFlow.asStateFlow()
//
//    private val deviceStateMap = atomic(mapOf<String, SocketConnectionState>())
//
//    val connectionStateFlow = socketConnectionPool.connectionStateFlow
//        .onStart { emit(SocketConnectionState.Disconnected(SocketAddress("", 0))) } // Emitting initial value for connection state
//
//    val deviceListFlow = _deviceListFlow
//        .combine(connectionStateFlow) { devices, state ->
//            devices.map { device ->
//
//                if (device.address == state.socketAddress.host && device.port == state.socketAddress.port) {
//                    deviceStateMap.getAndUpdate { it + (device.id to state) }
//                }
//
//                val currentState = deviceStateMap.value[device.id]
//                if (currentState == null) {
//                    device
//                } else {
//                    device.copy(connectionState = currentState)
//                }
//            }
//        }
//        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(), emptyList())
//
//
//    val deviceMessageState = socketConnectionPool.messageStateFlow
//        .onStart { emit(SocketMessageState.Idle(SocketAddress("", 0), SocketMessageType.Standard)) }
//
//
//    init {
//        _deviceListFlow.value = listOf(
//            DeviceOld("1", "Stable Device", "10.0.2.2", 9001),
//            //Device("2", "Unstable Device", "10.0.2.2", 9002),
//            //Device("1", "Device 1", "192.168.18.72", 3000),
//            //Device("2", "Device 2", "192.168.18.190", 3000),
//        )
//    }
//
//    fun ping(device: DeviceOld, pingRequest: PingRequest) {
//        screenModelScope.launch(dispatcher) {
//            val socket = SocketAddress(device.address, device.port)
//            //socketConnectionPool.connect(socket, pingRequest)
//            socketConnectionPool.connect(pingRequest)
//        }
//    }
//
//    fun send(device: DeviceOld, message: SocketMessage, messageReceipt: SocketMessageReceipt? = null) {
//        screenModelScope.launch(dispatcher) {
//            val socket = SocketAddress(device.address, device.port)
//            //socketConnectionPool.send(socket, message, messageReceipt)
//            socketConnectionPool.send(message, messageReceipt)
//        }
//    }
//
//    override fun onDispose() {
//        super.onDispose()
//        //socketConnectionPool.disconnectAll()
//    }
//}
