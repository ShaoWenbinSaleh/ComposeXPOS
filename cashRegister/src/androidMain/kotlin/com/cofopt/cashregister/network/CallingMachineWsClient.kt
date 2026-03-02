package com.cofopt.cashregister.network

import android.util.Log
import com.cofopt.shared.network.CALLING_WS_SHARED_KEY
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class CallingMachineWsClient(
    private val client: OkHttpClient = OkHttpClient()
) {
    interface Listener {
        fun onConnected()
        fun onDisconnected()
        fun onError(message: String)
    }

    private var webSocket: WebSocket? = null
    private val connected = AtomicBoolean(false)
    private val reconnectAttempts = AtomicInteger(0)
    private var reconnectJob: Job? = null
    private var shouldReconnect = false
    private var lastHost: String? = null
    private var lastPort: Int? = null
    private var lastListener: Listener? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private val reconnectScheduled = AtomicBoolean(false)
    
    companion object {
        private const val TAG = "CallingMachineWsClient"
        private const val MAX_RECONNECT_ATTEMPTS = 10
        private const val BASE_RECONNECT_DELAY_MS = 2000L
        private const val MAX_RECONNECT_DELAY_MS = 30000L
    }

    fun connect(host: String, port: Int, listener: Listener? = null) {
        disconnect()
        
        // Save connection parameters for auto-reconnect
        lastHost = host
        lastPort = port
        lastListener = listener
        shouldReconnect = true
        reconnectAttempts.set(0)
        reconnectScheduled.set(false)

        doConnect(host, port, listener)
    }
    
    private fun doConnect(host: String, port: Int, listener: Listener?) {
        var cleanHost = host.trim()
            .removePrefix("ws://")
            .removePrefix("http://")
            .removePrefix("https://")
            .removeSuffix("/")
            .removePrefix("[")
            .removeSuffix("]")

        val hasMultipleColons = cleanHost.count { it == ':' } > 1
        if (!hasMultipleColons) {
            val lastColon = cleanHost.lastIndexOf(':')
            if (lastColon > 0) {
                val maybePort = cleanHost.substring(lastColon + 1).toIntOrNull()
                if (maybePort != null) {
                    cleanHost = cleanHost.substring(0, lastColon)
                }
            }
        }
        cleanHost = cleanHost.substringBefore('%')
        val hostForUrl = if (cleanHost.contains(':')) "[$cleanHost]" else cleanHost

        val ts = System.currentTimeMillis()
        val sig = callingHandshakeDigest(ts)
        val url = "ws://$hostForUrl:$port/?ts=$ts&sig=$sig"
        Log.d(TAG, "Connecting to $url (attempt ${reconnectAttempts.get() + 1})")
        
        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    connected.set(true)
                    reconnectAttempts.set(0)
                    reconnectScheduled.set(false)
                    Log.d(TAG, "Connected to $url")
                    listener?.onConnected()
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    connected.set(false)
                    Log.w(TAG, "$url | closing | code=$code | reason=$reason")
                    if (code != 1000) {
                        listener?.onError("$url | closing | code=$code | reason=$reason")
                    }
                    listener?.onDisconnected()
                    scheduleReconnect("closing")
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    connected.set(false)
                    val parts = ArrayList<String>(6)
                    parts.add(url)
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
                    listener?.onError(errorMsg)
                    listener?.onDisconnected()
                    scheduleReconnect("failure")
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    connected.set(false)
                    if (code != 1000) {
                        Log.w(TAG, "$url | closed | code=$code | reason=$reason")
                        listener?.onError("$url | closed | code=$code | reason=$reason")
                    } else {
                        Log.d(TAG, "Connection closed normally")
                    }
                    listener?.onDisconnected()
                    scheduleReconnect("closed")
                }
            }
        )
    }

    private fun scheduleReconnect(reason: String) {
        if (!shouldReconnect) {
            Log.d(TAG, "Auto-reconnect disabled, not scheduling reconnect")
            return
        }
        if (!reconnectScheduled.compareAndSet(false, true)) {
            Log.d(TAG, "Reconnect already scheduled, skip ($reason)")
            return
        }
        
        val attempts = reconnectAttempts.incrementAndGet()
        if (attempts > MAX_RECONNECT_ATTEMPTS) {
            Log.w(TAG, "Max reconnect attempts ($MAX_RECONNECT_ATTEMPTS) reached, giving up")
            shouldReconnect = false
            reconnectScheduled.set(false)
            return
        }
        
        val host = lastHost
        val port = lastPort
        val listener = lastListener
        
        if (host == null || port == null) {
            Log.w(TAG, "No saved connection parameters, cannot reconnect")
            reconnectScheduled.set(false)
            return
        }
        
        // Exponential backoff: 2s, 4s, 8s, 16s, 30s (max)
        val delayMs = (BASE_RECONNECT_DELAY_MS * (1 shl (attempts - 1))).coerceAtMost(MAX_RECONNECT_DELAY_MS)
        Log.d(TAG, "Scheduling reconnect attempt $attempts in ${delayMs}ms")
        
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(delayMs)
            if (shouldReconnect && !connected.get()) {
                reconnectScheduled.set(false)
                Log.d(TAG, "Attempting reconnect $attempts to $host:$port")
                doConnect(host, port, listener)
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
        reconnectScheduled.set(false)
        reconnectJob?.cancel()
        reconnectJob = null
        connected.set(false)
        try {
            webSocket?.close(1000, "bye")
        } catch (_: Exception) {
        }
        webSocket = null
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
