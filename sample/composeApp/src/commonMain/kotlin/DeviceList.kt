import io.ktor.utils.io.core.toByteArray
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.datetime.Clock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import socket.api.SocketAddress
import socket.api.SocketConnection
import socket.api.ping.PingRequest
import socket.api.message.SocketMessage
import socket.api.message.SocketMessageReceipt
import socket.api.TcpSocketConfig

@Serializable
data class PingSendCommand(
    @SerialName("PING") val unixTimestamp: String,
    @SerialName("msgId") val messageId: Int,
    @SerialName("dir") val direction: String,
)

@Serializable
data class SendCommand(
    @SerialName("cmd") val command: String,
    @SerialName("msgId") val messageId: Int,
    @SerialName("dir") val direction: String,
)

@Serializable
data class PingResponseCommand(
    @SerialName("CMD") val cmd: String,
    @SerialName("PONG") val unixTimestamp: String,
    @SerialName("success") val success: Boolean, // seems to actually use string if boolean doesn't work
    @SerialName("dir") val direction: String,
)

class DeviceList {

    private val defaultPingRequest = PingRequest.Config(
        pingInterval = 1500.milliseconds,
        requestMessage = SocketMessage {
            "ping".toByteArray()
        },
        messageReceipt = SocketMessageReceipt {
            it.decodeToString().trim() == "pong"
        }
    )

    fun get(index: Int = -1): List<Device> = listOf(
        Device(
            id = "1",
            name = "Stable Device",
            socketConnection = SocketConnection(
                socketAddress = SocketAddress(
                    host = "10.0.2.2",
                    port = 9001,
                ),
                pingRequest = defaultPingRequest,
            ),
            testMessage = SocketMessage { "stable test".toByteArray() },
        ),
        Device(
            id = "2",
            name = "Unstable Device",
            socketConnection = SocketConnection(
                socketAddress = SocketAddress(
                    host = "10.0.2.2",
                    port = 9002,
                ),
                pingRequest = defaultPingRequest,
            ),
            testMessage = SocketMessage { "unstable test".toByteArray() },
        ),
        Device(
            id = "3",
            name = "Actual Device",
            socketConnection = SocketConnection(
                socketAddress = SocketAddress(
                    host = "192.168.18.72",
                    port = 3000,
                ),
                pingRequest = defaultPingRequest.copy(
                    requestMessage = SocketMessage { messageCount ->
                        val command = PingSendCommand(
                            unixTimestamp = Clock.System.now().toEpochMilliseconds().toString(),
                            messageId = messageCount,
                            direction = "p2d"
                        )
                        Json.encodeToString(command).toByteArray()
                    },
                    messageReceipt = SocketMessageReceipt {
                        try {
                            val obj = Json.decodeFromString<PingResponseCommand>(it.decodeToString())
                            obj.cmd == "PONG" && obj.success && obj.direction == "d2p"
                        } catch (e: Exception) {
                            false
                        }
                    }
                ),
            ),
            config = TcpSocketConfig(
                connectionTimeout = 3.seconds
            ),
            testMessage = SocketMessage { messageCount ->
                val command = SendCommand(
                    command = "OPEN",
                    messageId = messageCount,
                    direction = "p2d"
                )

                Json.encodeToString(command).toByteArray()
            },
        ),
        Device(
            id = "4",
            name = "Test Device",
            socketConnection = SocketConnection(
                socketAddress = SocketAddress(
                    host = "192.168.18.190",
                    port = 3000,
                ),
                pingRequest = defaultPingRequest.copy(
                    requestMessage = SocketMessage { messageCount ->
                        val command = PingSendCommand(
                            unixTimestamp = Clock.System.now().toEpochMilliseconds().toString(),
                            messageId = messageCount,
                            direction = "p2d"
                        )
                        Json.encodeToString(command).toByteArray()
                    },
                    messageReceipt = SocketMessageReceipt {
                        try {
                            val obj = Json.decodeFromString<PingResponseCommand>(it.decodeToString())
                            obj.cmd == "PONG" && obj.success && obj.direction == "d2p"
                        } catch (e: Exception) {
                            false
                        }
                    }
                ),
            ),
            config = TcpSocketConfig(
                connectionTimeout = 5.seconds
            ),
            testMessage = SocketMessage { messageCount ->
                val command = SendCommand(
                    command = "OPEN",
                    messageId = messageCount,
                    direction = "p2d"
                )

                Json.encodeToString(command).toByteArray()
            },
        )
    ).filterIndexed { i, _ ->
        index == -1 || i == index
    }
}
