package com.cofopt.cashregister.network

import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CallingMachineWsClientTest {
    @Test
    fun staleCallbacksCannotDisconnectReplacementSocket() {
        val sockets = mutableListOf<FakeConnection>()
        val client = testClient(sockets)
        val firstListener = RecordingListener()
        val secondListener = RecordingListener()

        client.connect("10.0.0.10", 9090, firstListener)
        val first = sockets.single()
        client.connect("10.0.0.11", 9090, secondListener)
        val second = sockets.last()

        first.listener.onFailure(first.socket, IOException("stale failure"), null)
        second.listener.onOpen(second.socket, switchingProtocols(second.request))

        assertEquals(2, sockets.size)
        assertEquals(0, firstListener.errors.size)
        assertEquals(0, firstListener.disconnected)
        assertEquals(1, secondListener.connected)
        assertTrue(client.isConnected())
    }

    @Test
    fun reconnectContinuesPastFormerTenAttemptLimit() {
        val sockets = mutableListOf<FakeConnection>()
        val listener = RecordingListener()
        val client = testClient(sockets)

        client.connect("192.168.1.20", 9090, listener)
        repeat(12) { attempt ->
            val current = sockets.last()
            current.listener.onFailure(current.socket, IOException("offline-$attempt"), null)
            assertEquals(attempt + 2, sockets.size)
        }

        assertEquals(12, client.getReconnectAttempts())
        assertEquals(12, listener.disconnected)
        assertFalse(client.isConnected())
    }

    @Test
    fun closingWaitsForTerminalCallbackBeforeReconnect() {
        val sockets = mutableListOf<FakeConnection>()
        val listener = RecordingListener()
        val client = testClient(sockets)

        client.connect("192.168.1.21", 9090, listener)
        val first = sockets.single()
        first.listener.onOpen(first.socket, switchingProtocols(first.request))
        first.listener.onClosing(first.socket, 1001, "restart")

        assertTrue(client.isConnected())
        assertEquals(0, listener.disconnected)
        assertEquals(1, sockets.size)

        first.listener.onClosed(first.socket, 1001, "restart")

        assertFalse(client.isConnected())
        assertEquals(1, listener.disconnected)
        assertEquals(2, sockets.size)
    }

    @Test
    fun normalizesSchemesPortsAndIpv6() {
        assertEquals("192.168.1.8", normalizeCallingMachineHost("ws://192.168.1.8:9090/"))
        assertEquals("example.local", normalizeCallingMachineHost("HTTPS://example.local/"))
        assertEquals("2001:db8::5", normalizeCallingMachineHost("[2001:db8::5]:9090"))
        assertEquals("2001:db8::6", normalizeCallingMachineHost("2001:db8::6"))
        assertNull(normalizeCallingMachineHost("example.local:not-a-port"))
        assertNull(normalizeCallingMachineHost("ws://example.local/path"))
        assertNull(normalizeCallingMachineHost("  "))
    }

    @Test
    fun stableSourceIdIsSentAndRetainedAcrossReconnect() {
        val sockets = mutableListOf<FakeConnection>()
        val client = testClient(sockets)

        client.connect(
            host = "192.168.1.22",
            port = 9090,
            sourceId = "android-cash-01",
        )
        assertEquals("android-cash-01", sockets.single().request.url.queryParameter("sourceId"))

        val first = sockets.single()
        first.listener.onFailure(first.socket, IOException("offline"), null)
        assertEquals("android-cash-01", sockets.last().request.url.queryParameter("sourceId"))
    }

    private fun testClient(sockets: MutableList<FakeConnection>): CallingMachineWsClient {
        return CallingMachineWsClient(
            webSocketFactory = { request, listener ->
                FakeConnection(request, listener).also(sockets::add).socket
            },
            scope = CoroutineScope(Job() + Dispatchers.Unconfined),
            reconnectDelay = { },
        )
    }

    private fun switchingProtocols(request: Request): Response {
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(101)
            .message("Switching Protocols")
            .build()
    }

    private class RecordingListener : CallingMachineWsClient.Listener {
        var connected: Int = 0
        var disconnected: Int = 0
        val errors = mutableListOf<String>()

        override fun onConnected() {
            connected++
        }

        override fun onDisconnected() {
            disconnected++
        }

        override fun onError(message: String) {
            errors += message
        }
    }

    private class FakeConnection(
        val request: Request,
        val listener: WebSocketListener,
    ) {
        val socket: WebSocket = object : WebSocket {
            override fun request(): Request = request

            override fun queueSize(): Long = 0L

            override fun send(text: String): Boolean = true

            override fun send(bytes: ByteString): Boolean = true

            override fun close(code: Int, reason: String?): Boolean = true

            override fun cancel() = Unit
        }
    }
}
