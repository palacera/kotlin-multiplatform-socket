import io.ktor.utils.io.core.toByteArray
import kotlin.time.Duration.Companion.seconds
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import socket.api.PingRequest
import socket.api.SocketMessage
import socket.api.SocketMessageReceipt
import socket.api.TcpSocketConfig

@Serializable
data class PingSendCommand(
    @SerialName("PING") val unixTimestamp: String,
    @SerialName("msgId") val messageId: Int,
    @SerialName("dir") val dir: String,
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

    private val defaultPingRequest = PingRequest(
        pingInterval = 3.seconds,
        requestMessage = SocketMessage {
            "ping".toByteArray()
        },
        messageReceipt = SocketMessageReceipt {
            it.decodeToString() == "pong"
        }
    )

    fun get(index: Int = -1): List<Device> = listOf(
        Device(
            id = "1",
            name = "Stable Device",
            host = "10.0.2.2",
            port = 9001,
            testMessage = SocketMessage { "stable test".toByteArray() },
            pingRequest = defaultPingRequest
        ),
        Device(
            id = "2",
            name = "Unstable Device",
            host = "10.0.2.2",
            port = 9002,
            testMessage = SocketMessage { "unstable test".toByteArray() },
            pingRequest = defaultPingRequest
        ),
        Device(
            id = "3",
            name = "Actual Device",
            host = "192.168.18.72",
            port = 3000,
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
//            pingRequest = defaultPingRequest.copy(
//                requestMessage = SocketMessage { messageCount ->
//                    val command = PingSendCommand(
//                        unixTimestamp = Clock.System.now().toEpochMilliseconds().toString(),
//                        messageId = messageCount,
//                        dir = "p2d"
//                    )
//                    Json.encodeToString(command).toByteArray()
//                },
//                messageReceipt = SocketMessageReceipt {
//                    try {
//                        val obj = Json.decodeFromString<PingResponseCommand>(it.decodeToString())
//                        obj.cmd == "PONG" && obj.success && obj.dir == "d2p"
//                    } catch (e: Exception) {
//                        false
//                    }
//                }
//            )
        ),
        Device(
            id = "4",
            name = "Test Device",
            host = "192.168.18.190",
            port = 3000,
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
            },//            pingRequest = defaultPingRequest.copy(
//                requestMessage = SocketMessage { messageCount ->
//                    val command = PingSendCommand(
//                        unixTimestamp = Clock.System.now().toEpochMilliseconds().toString(),
//                        messageId = messageCount,
//                        dir = "p2d"
//                    )
//                    Json.encodeToString(command).toByteArray()
//                },
//                messageReceipt = SocketMessageReceipt {
//                    try {
//                        val obj = Json.decodeFromString<PingResponseCommand>(it.decodeToString())
//                        obj.cmd == "PONG" && obj.success && obj.dir == "d2p"
//                    } catch (e: Exception) {
//                        false
//                    }
//                }
//            )
        )
    ).filterIndexed { i, _ ->
        index == -1 || i == index
    }
}
