package com.cofopt.cashregister.network

import android.util.Log
import com.cofopt.shared.network.CALLING_WS_SHARED_KEY
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.HttpUrl
import okhttp3.Response
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class CallingMachineWsClient internal constructor(
    private val webSocketFactory: (Request, WebSocketListener) -> WebSocket,
    private val scope: CoroutineScope,
    private val reconnectDelay: suspend (Long) -> Unit,
) {
    constructor(client: OkHttpClient = OkHttpClient()) : this(
        webSocketFactory = { request, listener -> client.newWebSocket(request, listener) },
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        reconnectDelay = { delay(it) },
    )

    interface Listener {
        fun onConnected()
        fun onDisconnected()
        fun onError(message: String)
    }

    @Volatile
    private var webSocket: WebSocket? = null
    private val connected = AtomicBoolean(false)
    private val reconnectAttempts = AtomicInteger(0)
    private val connectionGeneration = AtomicInteger(0)
    private var reconnectJob: Job? = null
    @Volatile
    private var shouldReconnect = false
    @Volatile
    private var lastHost: String? = null
    @Volatile
    private var lastPort: Int? = null
    @Volatile
    private var lastListener: Listener? = null
    @Volatile
    private var lastSourceId: String? = null
    private val reconnectScheduled = AtomicBoolean(false)
    
    companion object {
        private const val TAG = "CallingMachineWsClient"
        private const val BASE_RECONNECT_DELAY_MS = 2000L
        private const val MAX_RECONNECT_DELAY_MS = 30000L
    }

    fun connect(
        host: String,
        port: Int,
        listener: Listener? = null,
        sourceId: String? = null,
    ) {
        disconnect()

        val cleanHost = normalizeCallingMachineHost(host)
        if (cleanHost == null || port !in 1..65535) {
            val message = "invalid_host_or_port"
            Log.w(TAG, "Cannot connect: $message")
            listener?.onError(message)
            listener?.onDisconnected()
            return
        }

        // Save connection parameters for auto-reconnect
        lastHost = cleanHost
        lastPort = port
        lastListener = listener
        lastSourceId = sourceId?.takeIf(::isValidCallingSourceId)
        shouldReconnect = true
        reconnectAttempts.set(0)
        reconnectScheduled.set(false)

        doConnect(cleanHost, port, listener, lastSourceId)
    }

    private fun doConnect(host: String, port: Int, listener: Listener?, sourceId: String?) {
        if (!shouldReconnect) return
        val generation = connectionGeneration.incrementAndGet()
        val ts = System.currentTimeMillis()
        val sig = callingHandshakeDigest(ts)
        val hostForUrl = if (host.contains(':')) "[$host]" else host
        val endpoint = "ws://$hostForUrl:$port"
        Log.d(TAG, "Connecting to $endpoint (attempt ${reconnectAttempts.get() + 1})")

        val request = runCatching {
            val url = HttpUrl.Builder()
                .scheme("http")
                .host(host)
                .port(port)
                .addPathSegment("")
                .addQueryParameter("mode", "source")
                .addQueryParameter("key", CALLING_WS_SHARED_KEY)
                .addQueryParameter("ts", ts.toString())
                .addQueryParameter("sig", sig)
                .apply {
                    if (sourceId != null) addQueryParameter("sourceId", sourceId)
                }
                .build()
            Request.Builder().url(url).build()
        }
            .getOrElse { error ->
                handleSetupFailure(generation, endpoint, error, listener)
                return
            }
        val terminalCallbackDelivered = AtomicBoolean(false)
        val socketListener = object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    if (!isCurrent(generation)) {
                        webSocket.close(1000, "superseded")
                        return
                    }
                    connected.set(true)
                    reconnectAttempts.set(0)
                    reconnectScheduled.set(false)
                    Log.d(TAG, "Connected to $endpoint")
                    listener?.onConnected()
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    if (!isCurrent(generation)) return
                    Log.w(TAG, "$endpoint | closing | code=$code | reason=$reason")
                    // Complete the close handshake. State and reconnect are
                    // handled once in onClosed (or onFailure).
                    runCatching { webSocket.close(1000, null) }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (!isCurrent(generation) || !terminalCallbackDelivered.compareAndSet(false, true)) return
                    val parts = ArrayList<String>(6)
                    parts.add(endpoint)
                    parts.add(t::class.java.simpleName.ifBlank { "Throwable" })
                    val msg = t.message
                    if (!msg.isNullOrBlank()) {
                        parts.add(msg)
                    }
                    val cause = t.cause
                    if (cause != null) {
                        val causeMsg = cause.message
                        if (causeMsg.isNullOrBlank()) {
                            parts.add("cause=${cause::class.java.simpleName}")
                        } else {
                            parts.add("cause=${cause::class.java.simpleName}: $causeMsg")
                        }
                    }
                    if (response != null) {
                        parts.add("HTTP ${response.code}")
                        val rmsg = response.message
                        if (rmsg.isNotBlank()) {
                            parts.add(rmsg)
                        }
                    }
                    val errorMsg = parts.joinToString(" | ")
                    Log.w(TAG, "Connection failed: $errorMsg")
                    handleTerminal(generation, listener, errorMsg, "failure")
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    if (!isCurrent(generation) || !terminalCallbackDelivered.compareAndSet(false, true)) return
                    val errorMessage = if (code != 1000) {
                        "$endpoint | closed | code=$code | reason=$reason"
                    } else {
                        null
                    }
                    if (code != 1000) {
                        Log.w(TAG, errorMessage.orEmpty())
                    } else {
                        Log.d(TAG, "Connection closed normally")
                    }
                    handleTerminal(generation, listener, errorMessage, "closed")
                }
            }

        val socket = runCatching { webSocketFactory(request, socketListener) }
            .getOrElse { error ->
                if (terminalCallbackDelivered.compareAndSet(false, true)) {
                    handleSetupFailure(generation, endpoint, error, listener)
                }
                return
            }
        if (isCurrent(generation) && !terminalCallbackDelivered.get()) {
            webSocket = socket
        } else {
            socket.cancel()
        }
    }

    private fun handleSetupFailure(
        generation: Int,
        endpoint: String,
        error: Throwable,
        listener: Listener?,
    ) {
        if (!isCurrent(generation)) return
        val message = listOfNotNull(
            endpoint,
            error::class.java.simpleName.ifBlank { "Throwable" },
            error.message?.takeIf { it.isNotBlank() },
        ).joinToString(" | ")
        Log.w(TAG, "Connection setup failed: $message")
        handleTerminal(generation, listener, message, "setup_failure")
    }

    private fun handleTerminal(
        generation: Int,
        listener: Listener?,
        errorMessage: String?,
        reason: String,
    ) {
        if (!isCurrent(generation)) return
        connected.set(false)
        webSocket = null
        if (!errorMessage.isNullOrBlank()) listener?.onError(errorMessage)
        listener?.onDisconnected()
        scheduleReconnect(reason, generation)
    }

    private fun isCurrent(generation: Int): Boolean {
        return shouldReconnect && generation == connectionGeneration.get()
    }

    private fun scheduleReconnect(reason: String, generation: Int) {
        if (!shouldReconnect || generation != connectionGeneration.get()) {
            Log.d(TAG, "Auto-reconnect disabled, not scheduling reconnect")
            return
        }
        if (!reconnectScheduled.compareAndSet(false, true)) {
            Log.d(TAG, "Reconnect already scheduled, skip ($reason)")
            return
        }
        
        val attempts = reconnectAttempts.updateAndGet { current ->
            if (current == Int.MAX_VALUE) current else current + 1
        }
        val host = lastHost
        val port = lastPort
        val listener = lastListener
        val sourceId = lastSourceId
        
        if (host == null || port == null) {
            Log.w(TAG, "No saved connection parameters, cannot reconnect")
            reconnectScheduled.set(false)
            return
        }
        
        // Retry for the lifetime of the configured target. A fixed retry limit
        // makes unattended POS devices stay offline forever after a longer LAN
        // outage. The delay is capped to avoid a hot loop.
        val shift = (attempts - 1).coerceIn(0, 4)
        val delayMs = (BASE_RECONNECT_DELAY_MS * (1L shl shift)).coerceAtMost(MAX_RECONNECT_DELAY_MS)
        Log.d(TAG, "Scheduling reconnect attempt $attempts in ${delayMs}ms")

        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            reconnectDelay(delayMs)
            if (shouldReconnect && !connected.get() && generation == connectionGeneration.get()) {
                reconnectScheduled.set(false)
                Log.d(TAG, "Attempting reconnect $attempts to $host:$port")
                doConnect(host, port, listener, sourceId)
            } else {
                reconnectScheduled.set(false)
            }
        }
    }

    fun send(text: String): Boolean {
        val ws = webSocket ?: return false
        return connected.get() && ws.send(text)
    }

    fun disconnect() {
        Log.d(TAG, "Disconnecting and stopping auto-reconnect")
        shouldReconnect = false
        connectionGeneration.incrementAndGet()
        reconnectScheduled.set(false)
        reconnectJob?.cancel()
        reconnectJob = null
        connected.set(false)
        val socket = webSocket
        webSocket = null
        try {
            socket?.close(1000, "bye")
        } catch (_: Exception) {
        }
    }

    fun isConnected(): Boolean = connected.get()
    
    fun getReconnectAttempts(): Int = reconnectAttempts.get()

    private fun callingHandshakeDigest(timestampMillis: Long): String {
        val raw = "CALLING_WS_V1|$timestampMillis|$CALLING_WS_SHARED_KEY"
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return buildString(digest.size * 2) {
            digest.forEach { b -> append("%02x".format(b)) }
        }
    }
}

private fun isValidCallingSourceId(value: String): Boolean {
    return value.length in 1..128 && value.all {
        it.isLetterOrDigit() || it == '-' || it == '_' || it == '.' || it == ':'
    }
}

internal fun normalizeCallingMachineHost(rawHost: String?): String? {
    var host = rawHost.orEmpty().trim()
    if (host.isBlank()) return null

    val lowercase = host.lowercase()
    val scheme = listOf("ws://", "wss://", "http://", "https://")
        .firstOrNull { lowercase.startsWith(it) }
    if (scheme != null) host = host.substring(scheme.length)
    host = host.trim().trimEnd('/')

    if (host.startsWith('[')) {
        val closingBracket = host.indexOf(']')
        if (closingBracket <= 1) return null
        val suffix = host.substring(closingBracket + 1)
        if (suffix.isNotBlank() && (!suffix.startsWith(':') || suffix.drop(1).toIntOrNull() == null)) {
            return null
        }
        host = host.substring(1, closingBracket)
    } else {
        when (host.count { it == ':' }) {
            0 -> Unit
            1 -> {
                val colon = host.lastIndexOf(':')
                if (host.substring(colon + 1).toIntOrNull() == null) return null
                host = host.substring(0, colon)
            }
            else -> Unit // Unbracketed IPv6 literal.
        }
    }

    host = host.substringBefore('%').trim()
    if (host.isBlank() || host.any { it.isWhitespace() || it == '/' || it == '?' || it == '#' || it == '@' }) {
        return null
    }
    return host
}
