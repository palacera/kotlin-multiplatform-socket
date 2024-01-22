package socket.api

class SocketException(
    message: String? = "A socket exception occurred.",
) : Exception(message)

class SocketUnreachableException(
    message: String? = "Network is unreachable.",
) : Exception(message)

class SocketUnreachableAirplaneModeException(
    message: String? = "Network is unreachable. Device may be in airplane mode.",
) : Exception(message)

class SocketConnectionTimeoutException(
    message: String? = "Socket connection timed out.",
) : Exception(message)

class SocketConnectionException(
    message: String? = "A socket connection exception occurred.",
) : Exception(message)

class SocketConnectionMaxAttemptsException(
    message: String? = "Failed to connect after maximum allowed attempts.",
) : Exception(message)

class SocketReadChannelException(
    message: String? = "Failed to read from channel.",
) : Exception(message)

class SocketWriteChannelException(
    message: String? = "Failed to write to channel.",
) : Exception(message)
