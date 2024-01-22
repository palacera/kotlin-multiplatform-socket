import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import dispatcher.Dispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import socket.api.message.SocketMessage
import socket.api.message.SocketMessageReceipt
import socket.api.TcpSocket

class DeviceViewModel(
    private val dispatcher: Dispatcher,
    private val socket: TcpSocket,
): ScreenModel {

    val connectionStateFlow = socket.connectionStateFlow

    fun ping() {
        screenModelScope.launch(dispatcher.io) {
            try {
                socket.connect()
            } catch (e: Exception) {
                // handle error
            }
        }
    }

    fun send(device: Device, message: SocketMessage, messageReceipt: SocketMessageReceipt? = null) {
        screenModelScope.launch(dispatcher.io) {
            try {
                socket.send(message, messageReceipt)
            } catch (e: Exception) {
                // handle error
            }
        }
    }
}
