package com.cofopt.callingmachine

import android.util.Log
import com.cofopt.shared.network.CALLING_WS_SHARED_KEY
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.net.URLDecoder
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

private const val TAG = "CallingWebSocketServer"

/** Keeps the Android UI and WebSocket callbacks on one coherent CallingState view. */
internal object CallingStateCoordinator {
    val lock = Any()
}

class CallingWebSocketServer(
    private val port: Int,
    private val onClientConnected: ((String) -> Unit)? = null
) {

    private val connectionCount = AtomicInteger(0)
    private val clientRoles = ConcurrentHashMap<WebSocket, ConnectionRole>()
    private val clientSourceIds = ConcurrentHashMap<WebSocket, String>()
    private val sourceSnapshots = LinkedHashMap<String, CallingSourceSnapshot>()
    private val activeSourceSockets = LinkedHashMap<String, WebSocket>()
    private val sourceSequence = AtomicLong(0L)
    private val seenAlertIds = LinkedHashSet<String>()
    private val lifecycleLock = Any()
    private val stateLock = CallingStateCoordinator.lock

    @Volatile
    private var server: WebSocketServer? = null

    fun start(
        timeoutMillis: Int = 5000,
        daemon: Boolean = false
    ) {
        synchronized(lifecycleLock) {
            if (server != null) return
        }

        val startupSignal = CountDownLatch(1)
        val startupCompleted = AtomicBoolean(false)
        val startupError = AtomicReference<Exception?>(null)

        val wsServer = object : WebSocketServer(InetSocketAddress(port)) {
            override fun onStart() {
                startupCompleted.set(true)
                startupSignal.countDown()
                synchronized(stateLock) { CallingState.updateLastCloseInfo(null) }
            }

            override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
                val role = connectionRole(handshake.resourceDescriptor)
                if (role == null) {
                    conn.close(1008, "unauthorized")
                    return
                }
                clientRoles[conn] = role
                if (role == ConnectionRole.SOURCE) {
                    val sourceId = resolveCallingSourceId(
                        resourceDescriptor = handshake.resourceDescriptor,
                        fallbackHost = conn.remoteSocketAddress?.address?.hostAddress,
                    )
                    clientSourceIds[conn] = sourceId
                    val superseded = synchronized(stateLock) {
                        activeSourceSockets.put(sourceId, conn)?.takeIf { it !== conn }
                    }
                    // A reconnect with the same stable source id supersedes the
                    // old socket. Its last snapshot remains authoritative until
                    // the replacement sends a newer full snapshot.
                    superseded?.close(1000, "superseded")
                    val count = connectionCount.incrementAndGet()
                    Log.d(
                        TAG,
                        "WebSocket Event: CONNECTION_OPENED | port=$port | source=$sourceId | connections=$count"
                    )
                    conn.remoteSocketAddress?.address?.hostAddress?.let { host ->
                        if (host.isNotBlank()) onClientConnected?.invoke(host)
                    }
                    synchronized(stateLock) {
                        CallingState.updateConnectionCount(count)
                        CallingState.updateLastCloseInfo(null)
                    }
                } else {
                    // Viewers can join at any time. Send the current full state
                    // immediately instead of waiting for a future source change.
                    // Keep the snapshot capture and enqueue under the same lock
                    // as source updates/broadcasts so a newer broadcast cannot be
                    // followed by this older initial snapshot.
                    runCatching {
                        synchronized(stateLock) {
                            conn.send(CallingState.currentSnapshot().toWireMessage())
                        }
                    }
                        .onFailure { Log.w(TAG, "Failed to send initial viewer snapshot", it) }
                }
            }

            override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) {
                val role = clientRoles.remove(conn)
                if (role == ConnectionRole.SOURCE) {
                    val sourceId = clientSourceIds.remove(conn)
                    val count = connectionCount.updateAndGet { v -> (v - 1).coerceAtLeast(0) }
                    Log.d(
                        TAG,
                        "WebSocket Event: CONNECTION_CLOSED | port=$port | source=${sourceId ?: "-"} | connections=$count"
                    )
                    synchronized(stateLock) {
                        if (sourceId != null && activeSourceSockets[sourceId] === conn) {
                            activeSourceSockets.remove(sourceId)
                        }
                        CallingState.updateLastCloseInfo("code=$code reason=$reason remote=$remote")
                        CallingState.updateConnectionCount(count)
                    }
                }
            }

            override fun onMessage(conn: WebSocket, message: String) {
                if (clientRoles[conn] != ConnectionRole.SOURCE) return
                val sourceId = clientSourceIds[conn] ?: return
                if (synchronized(stateLock) { activeSourceSockets[sourceId] !== conn }) return
                try {
                    val obj = JSONObject(message)
                    val type = obj.optString("type")
                    when (type) {
                        "calling_snapshot" -> {
                            val preparing = obj.optJSONArray("preparing")?.toIntList() ?: emptyList()
                            val ready = obj.optJSONArray("ready")?.toIntList() ?: emptyList()
                            val displayLanguage = obj.optString("displayLanguage")
                            val voiceLanguage = obj.optString("voiceLanguage")
                            var sourceAccepted = true
                            val changed = synchronized(stateLock) {
                                if (!ensureSourceSnapshotCapacity(sourceId)) {
                                    sourceAccepted = false
                                    return@synchronized false
                                }
                                sourceSnapshots[sourceId] = CallingSourceSnapshot(
                                    preparing = preparing,
                                    ready = ready,
                                    displayLanguage = CallingLanguage.fromWireValue(displayLanguage)
                                        ?: CallingLanguage.ZH,
                                    voiceLanguage = CallingLanguage.fromWireValue(voiceLanguage)
                                        ?: CallingLanguage.ZH,
                                    sequence = sourceSequence.incrementAndGet(),
                                )
                                val aggregate = aggregateCallingSourceSnapshots(sourceSnapshots.values)
                                CallingState.updateLanguages(
                                    aggregate.displayLanguage.wireValue,
                                    aggregate.voiceLanguage.wireValue,
                                )
                                val snapshotChanged = CallingState.updateSnapshot(
                                    aggregate.preparing,
                                    aggregate.ready,
                                )
                                broadcastToViewers(aggregate.toWireMessage())
                                snapshotChanged
                            }
                            if (!sourceAccepted) {
                                conn.close(1013, "too_many_sources")
                                return
                            }
                            if (changed) {
                                Log.d(
                                    TAG,
                                    "CallingState Event: SNAPSHOT_RECEIVED | source=$sourceId | preparing=${preparing.size} | ready=${ready.size}"
                                )
                            }
                        }

                        "calling_alert" -> {
                            val number = obj.optInt("number", -1)
                            if (number > 0) {
                                synchronized(stateLock) {
                                    val eventId = obj.optString("eventId")
                                        .trim()
                                        .takeIf { it.length in 1..MAX_EVENT_ID_LENGTH }
                                    if (eventId != null && !rememberAlertId(eventId)) {
                                        return@synchronized
                                    }
                                    Log.d(TAG, "ALERT_RECEIVED source=$sourceId number=$number")
                                    CallingState.alertNumber(number)
                                    broadcastToViewers(message)
                                }
                            }
                        }

                        else -> return
                    }
                } catch (t: Throwable) {
                    val msg = t.message ?: t.javaClass.simpleName
                    Log.e(TAG, "WebSocket message processing error", t)
                    synchronized(stateLock) { CallingState.updateLastCloseInfo("onMessage_error=$msg") }
                }
            }

            override fun onMessage(conn: WebSocket, message: ByteBuffer) {
            }

            override fun onError(conn: WebSocket?, ex: Exception) {
                if (conn == null && !startupCompleted.get()) {
                    startupError.compareAndSet(null, ex)
                    startupSignal.countDown()
                }
                val msg = ex.message ?: ex.javaClass.simpleName
                Log.e(TAG, "WebSocket server error", ex)
                synchronized(stateLock) { CallingState.updateLastCloseInfo("onError=$msg") }
            }
        }

        wsServer.isReuseAddr = true
        wsServer.isTcpNoDelay = true
        wsServer.setConnectionLostTimeout(CONNECTION_LOST_TIMEOUT_SECONDS)
        wsServer.setDaemon(daemon)

        synchronized(lifecycleLock) {
            if (server != null) return
            server = wsServer
        }

        try {
            wsServer.start()
            val signalled = startupSignal.await(timeoutMillis.coerceAtLeast(1).toLong(), TimeUnit.MILLISECONDS)
            val failure = startupError.get()
            if (!signalled) throw SocketTimeoutException("WebSocket server start timed out on port $port")
            if (failure != null) throw failure
            if (!startupCompleted.get()) throw IllegalStateException("WebSocket server failed to start on port $port")
        } catch (t: Throwable) {
            synchronized(lifecycleLock) {
                if (server === wsServer) server = null
            }
            runCatching { wsServer.stop(1000) }
            if (t is InterruptedException) Thread.currentThread().interrupt()
            throw t
        }
    }

    private fun broadcastToViewers(message: String) {
        clientRoles.forEach { (socket, role) ->
            if (role != ConnectionRole.VIEWER || !socket.isOpen) return@forEach
            runCatching { socket.send(message) }
        }
    }

    /** Returns false when an at-least-once alert retry was already delivered. */
    private fun rememberAlertId(eventId: String): Boolean {
        if (!seenAlertIds.add(eventId)) return false
        while (seenAlertIds.size > MAX_SEEN_ALERT_IDS) {
            val oldest = seenAlertIds.iterator()
            if (!oldest.hasNext()) break
            oldest.next()
            oldest.remove()
        }
        return true
    }

    private fun ensureSourceSnapshotCapacity(sourceId: String): Boolean {
        if (sourceSnapshots.containsKey(sourceId) || sourceSnapshots.size < MAX_SOURCE_SNAPSHOTS) {
            return true
        }
        val evictable = sourceSnapshots
            .filterKeys { it !in activeSourceSockets }
            .minByOrNull { it.value.sequence }
            ?.key
            ?: return false
        sourceSnapshots.remove(evictable)
        return true
    }

    fun stop() {
        val s = synchronized(lifecycleLock) {
            val running = server ?: return
            server = null
            running
        }
        try {
            s.stop(1000)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        } finally {
            clientRoles.clear()
            clientSourceIds.clear()
            synchronized(stateLock) { activeSourceSockets.clear() }
            connectionCount.set(0)
            synchronized(stateLock) { CallingState.updateConnectionCount(0) }
        }
    }

    private companion object {
        const val CONNECTION_LOST_TIMEOUT_SECONDS = 45
        const val MAX_EVENT_ID_LENGTH = 128
        const val MAX_SEEN_ALERT_IDS = 512
        const val MAX_SOURCE_SNAPSHOTS = 64
    }
}

internal enum class ConnectionRole {
    SOURCE,
    VIEWER
}

internal data class CallingSourceSnapshot(
    val preparing: List<Int>,
    val ready: List<Int>,
    val displayLanguage: CallingLanguage,
    val voiceLanguage: CallingLanguage,
    val sequence: Long,
)

/**
 * Combines independent cashier snapshots without letting an empty or stale
 * source erase another cashier's numbers. READY wins if sources disagree.
 */
internal fun aggregateCallingSourceSnapshots(
    snapshots: Collection<CallingSourceSnapshot>,
): CallingStateSnapshot {
    if (snapshots.isEmpty()) {
        return CallingStateSnapshot(
            preparing = emptyList(),
            ready = emptyList(),
            displayLanguage = CallingLanguage.ZH,
            voiceLanguage = CallingLanguage.ZH,
        )
    }

    val ready = LinkedHashSet<Int>()
    val preparing = LinkedHashSet<Int>()
    snapshots.sortedBy { it.sequence }.forEach { snapshot ->
        snapshot.ready.filterTo(ready) { it > 0 }
        snapshot.preparing.filterTo(preparing) { it > 0 }
    }
    preparing.removeAll(ready)
    val latest = snapshots.maxBy { it.sequence }
    return CallingStateSnapshot(
        preparing = preparing.toList(),
        ready = ready.toList(),
        displayLanguage = latest.displayLanguage,
        voiceLanguage = latest.voiceLanguage,
    )
}

internal fun callingHandshakeDigest(timestampMillis: Long, sharedKey: String): String {
    val raw = "CALLING_WS_V1|$timestampMillis|$sharedKey"
    val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
    return buildString(digest.size * 2) {
        digest.forEach { b -> append("%02x".format(b)) }
    }
}

internal fun parseQueryMap(resourceDescriptor: String?): Map<String, String> {
    val raw = resourceDescriptor.orEmpty()
    val query = raw.substringAfter('?', "")
    if (query.isBlank()) return emptyMap()
    return query.split('&')
        .mapNotNull { pair ->
            val idx = pair.indexOf('=')
            if (idx <= 0) return@mapNotNull null
            val key = pair.substring(0, idx).decodeQueryComponent()?.trim()
                ?: return@mapNotNull null
            val value = pair.substring(idx + 1).decodeQueryComponent()?.trim()
                ?: return@mapNotNull null
            if (key.isBlank() || value.isBlank()) null else key to value
        }
        .toMap()
}

private fun String.decodeQueryComponent(): String? {
    return runCatching { URLDecoder.decode(this, Charsets.UTF_8.name()) }.getOrNull()
}

private fun CallingWebSocketServer.connectionRole(resourceDescriptor: String?): ConnectionRole? {
    return resolveCallingConnectionRole(
        resourceDescriptor = resourceDescriptor,
        sharedKey = CALLING_WS_SHARED_KEY,
        nowMillis = System.currentTimeMillis(),
    )
}

internal fun resolveCallingSourceId(
    resourceDescriptor: String?,
    fallbackHost: String?,
): String {
    val explicit = parseQueryMap(resourceDescriptor)["sourceId"]
        ?.trim()
        ?.takeIf { candidate ->
            candidate.length in 1..128 && candidate.all {
                it.isLetterOrDigit() || it == '-' || it == '_' || it == '.' || it == ':'
            }
        }
    if (explicit != null) return explicit
    return "legacy:${fallbackHost?.trim().orEmpty().ifBlank { "unknown" }}"
}

internal fun resolveCallingConnectionRole(
    resourceDescriptor: String?,
    sharedKey: String,
    nowMillis: Long,
): ConnectionRole? {
    val params = parseQueryMap(resourceDescriptor)
    val mode = params["mode"].orEmpty().lowercase()
    if (mode == "viewer") return ConnectionRole.VIEWER
    if (mode != "source") return null

    // OrderingMachine-like shared-key gate for all source connections.
    val key = params["key"].orEmpty()
    if (key != sharedKey) return null

    // Optional signature hardening: if ts/sig are supplied, they must validate.
    val ts = params["ts"]?.toLongOrNull()
    val sig = params["sig"].orEmpty()
    if (ts == null && sig.isBlank()) return ConnectionRole.SOURCE
    if (ts == null || sig.isBlank()) return null
    if (ts < nowMillis - 60_000L || ts > nowMillis + 60_000L) return null
    val expected = callingHandshakeDigest(ts, sharedKey)
    return if (expected.equals(sig, ignoreCase = true)) ConnectionRole.SOURCE else null
}

private fun JSONArray.toIntList(): List<Int> {
    val out = ArrayList<Int>(length().coerceAtMost(999))
    for (i in 0 until length()) {
        val v = optInt(i, Int.MIN_VALUE)
        if (v in 1..999 && v !in out) out.add(v)
        if (out.size >= 999) break
    }
    return out
}

private fun CallingStateSnapshot.toWireMessage(): String {
    return JSONObject().apply {
        put("type", "calling_snapshot")
        put("preparing", JSONArray(preparing))
        put("ready", JSONArray(ready))
        put("displayLanguage", displayLanguage.wireValue)
        put("voiceLanguage", voiceLanguage.wireValue)
        put("ts", System.currentTimeMillis())
    }.toString()
}
