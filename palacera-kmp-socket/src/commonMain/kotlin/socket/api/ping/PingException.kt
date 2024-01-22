package socket.api.ping

class MaxUnconfirmedPingsException(
    message: String? = "Sent max ping requests without a response.",
) : Exception(message)
