package com.cofopt.cashregister.cmp.platform

import com.cofopt.shared.network.CALLING_WS_SHARED_KEY
import kotlinx.browser.window
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.w3c.dom.WebSocket
import org.w3c.dom.events.Event
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private const val CALLING_TARGET_HOST_KEY = "cashregister.calling.target.host"
private const val CALLING_TARGET_PORT_KEY = "cashregister.calling.target.port"

internal data class CallingMachineWebBridgeStatus(
    val connected: Boolean = false,
    val targetHost: String? = null,
    val targetPort: Int? = null,
    val lastError: String? = null
)

actual object CallingPlatform {
    private val preparingState = MutableStateFlow(emptyList<Int>())
    private val readyState = MutableStateFlow(emptyList<Int>())
    private val statusState = MutableStateFlow(
        CallingMachineWebBridgeStatus(
            targetHost = loadStoredTargetHost(),
            targetPort = loadStoredTargetPort()
        )
    )

    actual val preparing: StateFlow<List<Int>> = preparingState
    actual val ready: StateFlow<List<Int>> = readyState
    internal val bridgeStatus: StateFlow<CallingMachineWebBridgeStatus> = statusState

    private var webSocket: WebSocket? = null
    private var webSocketUrl: String? = null
    private var connected: Boolean = false
    private var queuedSnapshot: String? = null
    private var overrideHost: String? = loadStoredTargetHost()
    private var overridePort: Int? = loadStoredTargetPort()

    actual fun clearPreparing() {
        preparingState.value = emptyList()
        pushSnapshot()
    }

    actual fun clearReady() {
        readyState.value = emptyList()
        pushSnapshot()
    }

    actual fun markReady(number: Int) {
        preparingState.value = preparingState.value.filterNot { it == number }
        if (!readyState.value.contains(number)) {
            readyState.value = readyState.value + number
        }
        pushSnapshot()
    }

    actual fun complete(number: Int) {
        readyState.value = readyState.value.filterNot { it == number }
        pushSnapshot()
    }

    actual fun updateOrderStatusByCallNumber(callNumber: Int, status: String) {
        when (status.trim().uppercase()) {
            "READY" -> markReady(callNumber)
            "COMPLETED" -> complete(callNumber)
            else -> Unit
        }
    }

    actual fun addManualReady(number: Int): ManualCallAddResult {
        if (number !in 1..999) return ManualCallAddResult.OutOfRange
        if (readyState.value.contains(number)) return ManualCallAddResult.Duplicate
        preparingState.value = preparingState.value.filterNot { it == number }
        readyState.value = readyState.value + number
        pushSnapshot()
        return ManualCallAddResult.Added
    }

    actual fun addManualPreparing(number: Int): ManualCallAddResult {
        if (number !in 1..999) return ManualCallAddResult.OutOfRange
        if (preparingState.value.contains(number)) return ManualCallAddResult.Duplicate
        readyState.value = readyState.value.filterNot { it == number }
        preparingState.value = preparingState.value + number
        pushSnapshot()
        return ManualCallAddResult.Added
    }

    actual fun sendAlert(number: Int) {
        if (number <= 0) return
        val payload = buildString {
            append("{\"type\":\"calling_alert\",\"number\":")
            append(number)
            append(",\"ts\":")
            append(nowMillis())
            append('}')
        }
        sendMessage(payload, queueIfDisconnected = false)
    }

    internal fun connectToCallingMachine(host: String, port: Int) {
        val normalizedHost = host.trim()
        if (normalizedHost.isBlank() || port !in 1..65535) {
            statusState.value = statusState.value.copy(
                connected = false,
                targetHost = normalizedHost.ifBlank { null },
                targetPort = if (port > 0) port else null,
                lastError = "invalid_host_or_port"
            )
            return
        }
        overrideHost = normalizedHost
        overridePort = port
        saveTarget(normalizedHost, port)
        disconnect()
        ensureConnected()
        pushSnapshot()
    }

    internal fun disconnectCallingMachine() {
        disconnect()
        statusState.value = statusState.value.copy(connected = false)
    }

    private fun pushSnapshot() {
        val displayLanguage = queryParam("callingDisplayLanguage")
            ?: queryParam("displayLanguage")
            ?: "zh"
        val voiceLanguage = queryParam("callingVoiceLanguage")
            ?: queryParam("voiceLanguage")
            ?: displayLanguage
        val payload = buildString {
            append("{\"type\":\"calling_snapshot\",\"preparing\":")
            append(toJsonArray(preparingState.value))
            append(",\"ready\":")
            append(toJsonArray(readyState.value))
            append(",\"displayLanguage\":\"")
            append(normalizeLanguage(displayLanguage))
            append("\",\"voiceLanguage\":\"")
            append(normalizeLanguage(voiceLanguage))
            append("\",\"ts\":")
            append(nowMillis())
            append('}')
        }
        sendMessage(payload, queueIfDisconnected = true)
    }

    private fun sendMessage(message: String, queueIfDisconnected: Boolean) {
        ensureConnected()
        val ws = webSocket
        if (ws != null && connected) {
            runCatching { ws.send(message) }
                .onFailure {
                    connected = false
                    statusState.value = statusState.value.copy(
                        connected = false,
                        lastError = it.message ?: it::class.simpleName.orEmpty()
                    )
                    if (queueIfDisconnected) queuedSnapshot = message
                }
            return
        }
        if (queueIfDisconnected) {
            queuedSnapshot = message
        }
    }

    private fun ensureConnected() {
        val targetUrl = resolveCallingSourceWsUrl() ?: return
        if (webSocket != null && webSocketUrl == targetUrl) return
        disconnect()

        val socket = WebSocket(targetUrl)
        webSocket = socket
        webSocketUrl = targetUrl
        connected = false

        socket.onopen = { _: Event ->
            connected = true
            statusState.value = statusState.value.copy(
                connected = true,
                targetHost = resolveCurrentTargetHost(),
                targetPort = resolveCurrentTargetPort(),
                lastError = null
            )
            queuedSnapshot?.let { queued ->
                runCatching { socket.send(queued) }
                queuedSnapshot = null
            }
            null
        }
        socket.onclose = { _: Event ->
            connected = false
            statusState.value = statusState.value.copy(connected = false)
            null
        }
        socket.onerror = { _: Event ->
            connected = false
            statusState.value = statusState.value.copy(
                connected = false,
                lastError = "websocket_error"
            )
            null
        }
    }

    private fun disconnect() {
        runCatching { webSocket?.close() }
        webSocket = null
        webSocketUrl = null
        connected = false
    }
}

private fun resolveCallingSourceWsUrl(): String? {
    val currentHost = resolveCurrentTargetHost()
    val currentPort = resolveCurrentTargetPort()
    if (!currentHost.isNullOrBlank() && currentPort in 1..65535) {
        val scheme = if (window.location.protocol == "https:") "wss" else "ws"
        return "$scheme://$currentHost:$currentPort/?mode=source&key=$CALLING_WS_SHARED_KEY"
    }

    val explicit = queryParam("callingWs") ?: queryParam("calling_ws")
    if (!explicit.isNullOrBlank()) {
        return withSourceAuth(explicit)
    }

    val host = queryParam("callingHost")
        ?: queryParam("calling_host")
        ?: return null
    val port = queryParam("callingPort")
        ?.toIntOrNull()
        ?.takeIf { it in 1..65535 }
        ?: queryParam("calling_port")?.toIntOrNull()?.takeIf { it in 1..65535 }
        ?: 9090
    val scheme = if (window.location.protocol == "https:") "wss" else "ws"
    return "$scheme://$host:$port/?mode=source&key=$CALLING_WS_SHARED_KEY"
}

private fun withSourceAuth(rawUrl: String): String {
    val url = rawUrl.trim()
    if (url.isBlank()) return url
    if (url.contains("mode=") && url.contains("key=")) return url
    val separator = if (url.contains("?")) "&" else "?"
    return "$url${separator}mode=source&key=$CALLING_WS_SHARED_KEY"
}

private fun resolveCurrentTargetHost(): String? = loadStoredTargetHost()?.takeIf { it.isNotBlank() }

private fun resolveCurrentTargetPort(): Int? = loadStoredTargetPort()?.takeIf { it in 1..65535 }

private fun saveTarget(host: String, port: Int) {
    runCatching {
        window.localStorage.setItem(CALLING_TARGET_HOST_KEY, host)
        window.localStorage.setItem(CALLING_TARGET_PORT_KEY, port.toString())
    }
}

private fun loadStoredTargetHost(): String? {
    return runCatching {
        window.localStorage.getItem(CALLING_TARGET_HOST_KEY)
    }.getOrNull()?.trim()?.takeIf { it.isNotBlank() }
}

private fun loadStoredTargetPort(): Int? {
    return runCatching {
        window.localStorage.getItem(CALLING_TARGET_PORT_KEY)?.toIntOrNull()
    }.getOrNull()?.takeIf { it in 1..65535 }
}

private fun queryParam(name: String): String? {
    val params = parseQuery(window.location.search.orEmpty())
    return params[name]?.takeIf { it.isNotBlank() }
}

private fun toJsonArray(values: List<Int>): String {
    if (values.isEmpty()) return "[]"
    return values.joinToString(prefix = "[", postfix = "]", separator = ",")
}

private fun normalizeLanguage(raw: String): String {
    return when (raw.trim().lowercase()) {
        "zh", "en", "nl", "ja", "tr" -> raw.trim().lowercase()
        else -> "zh"
    }
}

@OptIn(ExperimentalTime::class)
private fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

private external fun decodeURIComponent(encodedURI: String): String

private fun parseQuery(rawSearch: String): Map<String, String> {
    val raw = rawSearch.removePrefix("?").trim()
    if (raw.isBlank()) return emptyMap()
    return raw.split("&")
        .mapNotNull { pair ->
            val idx = pair.indexOf('=')
            if (idx < 0) return@mapNotNull null
            val key = decodeURIComponent(pair.substring(0, idx).trim())
            val value = decodeURIComponent(pair.substring(idx + 1).trim())
            if (key.isBlank()) null else key to value
        }
        .toMap()
}
