package api

import kotlin.test.DefaultAsserter.assertEquals
import kotlin.test.Test
import socket.api.SocketAddress

class SocketAddressTest {

    @Test
    fun test() {
        val host = "127.0.0.1"
        val port = 8080
        val expectedUuid = "$host:$port"

        val socketAddress = SocketAddress(host, port)

        assertEquals( "The uuid property should correctly concatenate host and port.", expectedUuid, socketAddress.uuid)
    }
}
