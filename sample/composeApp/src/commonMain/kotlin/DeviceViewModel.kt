import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import socket.api.PingRequest
import socket.api.SocketAddress
import socket.api.SocketMessage
import socket.api.SocketMessageReceipt
import socket.api.TcpSocket

class DeviceViewModel(
    private val dispatcher: CoroutineDispatcher = PlatformDispatcher.io,
    private val socket: TcpSocket,
): ScreenModel {

    val connectionStateFlow = socket.connectionStateFlow

    fun ping(device: Device) {
        screenModelScope.launch(dispatcher) {
            socket.connect(device.pingRequest)
        }
    }

    fun send(device: Device, message: SocketMessage, messageReceipt: SocketMessageReceipt? = null) {
        screenModelScope.launch(dispatcher) {
            socket.send(message, messageReceipt)
        }
    }
}
