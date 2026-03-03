package com.cofopt.callingmachine

import android.util.Log
import com.cofopt.shared.network.CALLING_WS_SHARED_KEY
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "CallingWebSocketServer"

class CallingWebSocketServer(
    private val port: Int,
    private val onClientConnected: ((String) -> Unit)? = null
) {

    private val connectionCount = AtomicInteger(0)
    private val clientRoles = ConcurrentHashMap<WebSocket, ConnectionRole>()

    @Volatile
    private var server: WebSocketServer? = null

    fun start(
        @Suppress("UNUSED_PARAMETER") timeoutMillis: Int = 5000,
        @Suppress("UNUSED_PARAMETER") daemon: Boolean = false
    ) {
        if (server != null) return

        val wsServer = object : WebSocketServer(InetSocketAddress(port)) {
            override fun onStart() {
                CallingState.updateLastCloseInfo(null)
            }

            override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
                val role = connectionRole(handshake.resourceDescriptor)
                if (role == null) {
                    conn.close(1008, "unauthorized")
                    return
                }
                clientRoles[conn] = role
                if (role == ConnectionRole.SOURCE) {
                    val count = connectionCount.incrementAndGet()
                    Log.d(TAG, "WebSocket Event: CONNECTION_OPENED | port=$port | connections=$count")
                    conn.remoteSocketAddress?.address?.hostAddress?.let { host ->
                        if (host.isNotBlank()) onClientConnected?.invoke(host)
                    }
                    CallingState.updateConnectionCount(count)
                    CallingState.updateLastCloseInfo(null)
                }
            }

            override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) {
                Log.d(TAG, "WebSocket Event: CONNECTION_CLOSED | port=$port | connections=${connectionCount.get()}")
                CallingState.updateLastCloseInfo("code=$code reason=$reason remote=$remote")
                val role = clientRoles.remove(conn)
                if (role == ConnectionRole.SOURCE) {
                    val count = connectionCount.updateAndGet { v -> (v - 1).coerceAtLeast(0) }
                    CallingState.updateConnectionCount(count)
                }
            }

            override fun onMessage(conn: WebSocket, message: String) {
                if (clientRoles[conn] != ConnectionRole.SOURCE) return
                try {
                    val obj = JSONObject(message)
                    val type = obj.optString("type")
                    when (type) {
                        "calling_snapshot" -> {
                            val preparing = obj.optJSONArray("preparing")?.toIntList() ?: emptyList()
                            val ready = obj.optJSONArray("ready")?.toIntList() ?: emptyList()
                            val displayLanguage = obj.optString("displayLanguage")
                            val voiceLanguage = obj.optString("voiceLanguage")
                            CallingState.updateLanguages(displayLanguage, voiceLanguage)
                            val changed = CallingState.updateSnapshot(preparing, ready)
                            if (changed) {
                                Log.d(TAG, "CallingState Event: SNAPSHOT_RECEIVED | preparing=${preparing.size} | ready=${ready.size}")
                            }
                            broadcastToViewers(message)
                        }

                        "calling_alert" -> {
                            val number = obj.optInt("number", -1)
                            if (number > 0) {
                                Log.d(TAG, "ALERT_RECEIVED number=$number")
                                CallingState.alertNumber(number)
                                broadcastToViewers(message)
                            }
                        }

                        else -> return
                    }
                } catch (t: Throwable) {
                    val msg = t.message ?: t.javaClass.simpleName
                    Log.e(TAG, "WebSocket message processing error", t)
                    CallingState.updateLastCloseInfo("onMessage_error=$msg")
                }
            }

            override fun onMessage(conn: WebSocket, message: ByteBuffer) {
            }

            override fun onError(conn: WebSocket?, ex: Exception) {
                val msg = ex.message ?: ex.javaClass.simpleName
                Log.e(TAG, "WebSocket server error", ex)
                CallingState.updateLastCloseInfo("onError=$msg")
            }
        }

        server = wsServer
        wsServer.start()
    }

    private fun broadcastToViewers(message: String) {
        clientRoles.forEach { (socket, role) ->
            if (role != ConnectionRole.VIEWER || !socket.isOpen) return@forEach
            runCatching { socket.send(message) }
        }
    }

    fun stop() {
        val s = server ?: return
        server = null
        try {
            s.stop(1000)
        } catch (_: Exception) {
        }
    }
}

private enum class ConnectionRole {
    SOURCE,
    VIEWER
}

private fun callingHandshakeDigest(timestampMillis: Long, sharedKey: String): String {
    val raw = "CALLING_WS_V1|$timestampMillis|$sharedKey"
    val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
    return buildString(digest.size * 2) {
        digest.forEach { b -> append("%02x".format(b)) }
    }
}

private fun parseQueryMap(resourceDescriptor: String?): Map<String, String> {
    val raw = resourceDescriptor.orEmpty()
    val query = raw.substringAfter('?', "")
    if (query.isBlank()) return emptyMap()
    return query.split('&')
        .mapNotNull { pair ->
            val idx = pair.indexOf('=')
            if (idx <= 0) return@mapNotNull null
            val key = pair.substring(0, idx).trim()
            val value = pair.substring(idx + 1).trim()
            if (key.isBlank() || value.isBlank()) null else key to value
        }
        .toMap()
}

private fun CallingWebSocketServer.connectionRole(resourceDescriptor: String?): ConnectionRole? {
    val params = parseQueryMap(resourceDescriptor)
    val mode = params["mode"].orEmpty().lowercase()
    if (mode == "viewer") return ConnectionRole.VIEWER
    if (mode != "source") return null

    // OrderingMachine-like shared-key gate for all source connections.
    val key = params["key"].orEmpty()
    if (key != CALLING_WS_SHARED_KEY) return null

    // Optional signature hardening: if ts/sig are supplied, they must validate.
    val ts = params["ts"]?.toLongOrNull()
    val sig = params["sig"].orEmpty()
    if (ts == null && sig.isBlank()) return ConnectionRole.SOURCE
    if (ts == null || sig.isBlank()) return null
    val now = System.currentTimeMillis()
    if (kotlin.math.abs(now - ts) > 60_000L) return null
    val expected = callingHandshakeDigest(ts, CALLING_WS_SHARED_KEY)
    return if (expected.equals(sig, ignoreCase = true)) ConnectionRole.SOURCE else null
}

private fun JSONArray.toIntList(): List<Int> {
    val out = ArrayList<Int>(length())
    for (i in 0 until length()) {
        val v = optInt(i, Int.MIN_VALUE)
        if (v != Int.MIN_VALUE) out.add(v)
    }
    return out
}
