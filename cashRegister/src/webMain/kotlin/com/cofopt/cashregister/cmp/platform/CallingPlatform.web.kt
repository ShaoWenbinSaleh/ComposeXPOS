@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.cofopt.cashregister.cmp.platform

import com.cofopt.shared.network.CALLING_WS_SHARED_KEY
import kotlinx.browser.window
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.w3c.dom.WebSocket
import org.w3c.dom.events.Event
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.random.Random

private const val CALLING_TARGET_HOST_KEY = "cashregister.calling.target.host"
private const val CALLING_TARGET_PORT_KEY = "cashregister.calling.target.port"
private const val CALLING_SOURCE_ID_KEY = "cashregister.calling.source.id"
private const val CALLING_SOURCE_STATE_KEY = "cashregister.calling.source.state.v1"

internal data class CallingMachineWebBridgeStatus(
    val connected: Boolean = false,
    val targetHost: String? = null,
    val targetPort: Int? = null,
    val lastError: String? = null
)

actual object CallingPlatform {
    private val restoredSourceState = loadStoredSourceState()
    private val preparingState = MutableStateFlow(restoredSourceState?.preparing.orEmpty())
    private val readyState = MutableStateFlow(restoredSourceState?.ready.orEmpty())
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
    private var autoReconnectEnabled: Boolean = true
    private var connectionGeneration: Int = 0
    private var reconnectAttempts: Int = 0
    private var reconnectTimerId: Int? = null
    private var sourceStateDurable: Boolean = restoredSourceState != null

    init {
        val hasConfiguredTarget = resolveCallingSourceWsUrl(overrideHost, overridePort) != null
        if (sourceStateDurable && hasConfiguredTarget) {
            // A reconnect is only safe after restoring the source's last authoritative snapshot.
            // Legacy installations with a target but no source snapshot wait for a local change or
            // an explicit Connect action instead of clearing the remote display with an empty list.
            queuedSnapshot = buildSnapshotPayload()
            ensureConnected()
        } else if (hasConfiguredTarget) {
            statusState.value = statusState.value.copy(lastError = "calling_source_state_missing")
        }
    }

    actual fun clearPreparing() {
        updateSourceState(emptyList(), readyState.value)
    }

    actual fun clearReady() {
        updateSourceState(preparingState.value, emptyList())
    }

    actual fun markReady(number: Int) {
        if (number <= 0) return
        updateSourceState(
            preparing = preparingState.value.filterNot { it == number },
            ready = readyState.value + number,
        )
    }

    actual fun complete(number: Int) {
        updateSourceState(
            preparing = preparingState.value.filterNot { it == number },
            ready = readyState.value.filterNot { it == number },
        )
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
        return if (
            updateSourceState(
                preparing = preparingState.value.filterNot { it == number },
                ready = readyState.value + number,
            )
        ) {
            ManualCallAddResult.Added
        } else {
            ManualCallAddResult.PersistenceFailed
        }
    }

    actual fun addManualPreparing(number: Int): ManualCallAddResult {
        if (number !in 1..999) return ManualCallAddResult.OutOfRange
        if (preparingState.value.contains(number)) return ManualCallAddResult.Duplicate
        return if (
            updateSourceState(
                preparing = preparingState.value + number,
                ready = readyState.value.filterNot { it == number },
            )
        ) {
            ManualCallAddResult.Added
        } else {
            ManualCallAddResult.PersistenceFailed
        }
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
            autoReconnectEnabled = false
            closeSocket()
            statusState.value = statusState.value.copy(
                connected = false,
                targetHost = normalizedHost.ifBlank { null },
                targetPort = port.takeIf { it in 1..65535 },
                lastError = "invalid_host_or_port"
            )
            return
        }
        if (!persistSourceState()) {
            sourceStateDurable = false
            autoReconnectEnabled = false
            closeSocket()
            statusState.value = statusState.value.copy(
                connected = false,
                targetHost = normalizedHost,
                targetPort = port,
                lastError = "calling_source_state_persistence_failed"
            )
            return
        }
        sourceStateDurable = true
        overrideHost = normalizedHost
        overridePort = port
        saveTarget(normalizedHost, port)
        autoReconnectEnabled = true
        closeSocket()
        ensureConnected()
        pushSnapshot()
    }

    internal fun disconnectCallingMachine() {
        autoReconnectEnabled = false
        queuedSnapshot = null
        closeSocket()
        statusState.value = statusState.value.copy(connected = false)
    }

    private fun pushSnapshot(): Boolean {
        val snapshot = buildSnapshotPayload()
        if (!persistSourceState(preparingState.value, readyState.value)) {
            sourceStateDurable = false
            queuedSnapshot = null
            closeSocket()
            statusState.value = statusState.value.copy(
                connected = false,
                lastError = "calling_source_state_persistence_failed"
            )
            return false
        }
        sourceStateDurable = true
        sendMessage(snapshot, queueIfDisconnected = true)
        return true
    }

    private fun updateSourceState(preparing: List<Int>, ready: List<Int>): Boolean {
        val normalized = CallingSourceStateCodec.decode(
            CallingSourceStateCodec.encode(preparing, ready)
        ) ?: return false
        if (
            sourceStateDurable &&
            normalized.preparing == preparingState.value &&
            normalized.ready == readyState.value
        ) return true

        if (!persistSourceState(normalized.preparing, normalized.ready)) {
            sourceStateDurable = false
            queuedSnapshot = null
            closeSocket()
            statusState.value = statusState.value.copy(
                connected = false,
                lastError = "calling_source_state_persistence_failed"
            )
            return false
        }
        sourceStateDurable = true
        preparingState.value = normalized.preparing
        readyState.value = normalized.ready
        sendMessage(buildSnapshotPayload(), queueIfDisconnected = true)
        return true
    }

    private fun buildSnapshotPayload(): String {
        val displayLanguage = queryParam("callingDisplayLanguage")
            ?: queryParam("displayLanguage")
            ?: "zh"
        val voiceLanguage = queryParam("callingVoiceLanguage")
            ?: queryParam("voiceLanguage")
            ?: displayLanguage
        return buildString {
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
    }

    private fun sendMessage(message: String, queueIfDisconnected: Boolean) {
        ensureConnected()
        val ws = webSocket
        if (ws != null && connected) {
            runCatching { ws.send(message) }
                .onFailure {
                    queuedSnapshot = message.takeIf { queueIfDisconnected }
                    statusState.value = statusState.value.copy(
                        connected = false,
                        lastError = it.message ?: it::class.simpleName.orEmpty()
                    )
                    handleSocketLoss(ws)
                }
            return
        }
        if (queueIfDisconnected) {
            queuedSnapshot = message
        }
    }

    private fun ensureConnected() {
        if (!autoReconnectEnabled) return
        if (!sourceStateDurable) {
            statusState.value = statusState.value.copy(
                connected = false,
                lastError = "calling_source_state_missing"
            )
            return
        }
        val targetUrl = resolveCallingSourceWsUrl(overrideHost, overridePort) ?: return
        if (webSocket != null && webSocketUrl == targetUrl) return
        closeSocket()

        val socket = runCatching { WebSocket(targetUrl) }
            .getOrElse { error ->
                statusState.value = statusState.value.copy(
                    connected = false,
                    lastError = error.message ?: "invalid_websocket_url"
                )
                scheduleReconnect()
                return
            }
        val generation = ++connectionGeneration
        webSocket = socket
        webSocketUrl = targetUrl
        connected = false

        socket.onopen = { _: Event ->
            if (isCurrentSocket(socket, generation)) {
                connected = true
                reconnectAttempts = 0
                cancelReconnectTimer()
                statusState.value = statusState.value.copy(
                    connected = true,
                    targetHost = resolveCurrentTargetHost(overrideHost),
                    targetPort = resolveCurrentTargetPort(overridePort),
                    lastError = null
                )
                // CallingMachine may have restarted while local state stayed unchanged.
                // Rebuild its authoritative state on every successful connection.
                val snapshot = queuedSnapshot ?: buildSnapshotPayload()
                runCatching { socket.send(snapshot) }
                    .onSuccess { queuedSnapshot = null }
                    .onFailure {
                        queuedSnapshot = snapshot
                        statusState.value = statusState.value.copy(
                            connected = false,
                            lastError = it.message ?: "websocket_send_failed"
                        )
                        handleSocketLoss(socket)
                    }
            }
            null
        }
        socket.onclose = { _: Event ->
            if (isCurrentSocket(socket, generation)) {
                connected = false
                webSocket = null
                webSocketUrl = null
                statusState.value = statusState.value.copy(connected = false)
                scheduleReconnect()
            }
            null
        }
        socket.onerror = { _: Event ->
            if (isCurrentSocket(socket, generation)) {
                connected = false
                statusState.value = statusState.value.copy(
                    connected = false,
                    lastError = "websocket_error"
                )
                handleSocketLoss(socket)
            }
            null
        }
    }

    private fun handleSocketLoss(socket: WebSocket) {
        if (webSocket !== socket) return
        closeSocket()
        scheduleReconnect()
    }

    private fun closeSocket() {
        connectionGeneration++
        cancelReconnectTimer()
        val socket = webSocket
        webSocket = null
        webSocketUrl = null
        connected = false
        socket?.onopen = null
        socket?.onclose = null
        socket?.onerror = null
        runCatching { socket?.close() }
    }

    private fun isCurrentSocket(socket: WebSocket, generation: Int): Boolean {
        return webSocket === socket && generation == connectionGeneration
    }

    private fun scheduleReconnect() {
        if (!autoReconnectEnabled || reconnectTimerId != null) return
        reconnectAttempts = (reconnectAttempts + 1).coerceAtMost(30)
        val shift = (reconnectAttempts - 1).coerceIn(0, 4)
        val delayMs = (2_000 * (1 shl shift)).coerceAtMost(30_000)
        reconnectTimerId = window.setTimeout(
            handler = {
                reconnectTimerId = null
                if (autoReconnectEnabled && webSocket == null) ensureConnected()
                null
            },
            timeout = delayMs
        )
    }

    private fun cancelReconnectTimer() {
        reconnectTimerId?.let { window.clearTimeout(it) }
        reconnectTimerId = null
    }

    private fun persistSourceState(
        preparing: List<Int> = preparingState.value,
        ready: List<Int> = readyState.value,
    ): Boolean {
        return runCatching {
            val raw = CallingSourceStateCodec.encode(
                preparing = preparing,
                ready = ready,
            )
            window.localStorage.setItem(CALLING_SOURCE_STATE_KEY, raw)
        }.isSuccess
    }
}

private fun resolveCallingSourceWsUrl(overrideHost: String?, overridePort: Int?): String? {
    val currentHost = resolveCurrentTargetHost(overrideHost)
    val currentPort = resolveCurrentTargetPort(overridePort)
    if (!currentHost.isNullOrBlank() && currentPort in 1..65535) {
        val scheme = if (window.location.protocol == "https:") "wss" else "ws"
        return "$scheme://${currentHost.toWebSocketHost()}:$currentPort/?mode=source&key=${encodeURIComponent(CALLING_WS_SHARED_KEY)}&sourceId=${encodeURIComponent(loadOrCreateSourceId())}"
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
    return "$scheme://${host.toWebSocketHost()}:$port/?mode=source&key=${encodeURIComponent(CALLING_WS_SHARED_KEY)}&sourceId=${encodeURIComponent(loadOrCreateSourceId())}"
}

private fun withSourceAuth(rawUrl: String): String {
    var url = rawUrl.trim()
    if (url.isBlank()) return url
    val encodedKey = encodeURIComponent(CALLING_WS_SHARED_KEY)
    val modeRegex = Regex("([?&])mode=[^&#]*", RegexOption.IGNORE_CASE)
    url = if (modeRegex.containsMatchIn(url)) {
        url.replace(modeRegex, "\$1mode=source")
    } else {
        url.appendQueryParameter("mode=source")
    }
    val keyRegex = Regex("([?&])key=[^&#]*", RegexOption.IGNORE_CASE)
    url = if (keyRegex.containsMatchIn(url)) {
        url.replace(keyRegex, "\$1key=$encodedKey")
    } else {
        url.appendQueryParameter("key=$encodedKey")
    }
    val encodedSourceId = encodeURIComponent(loadOrCreateSourceId())
    val sourceIdRegex = Regex("([?&])sourceId=[^&#]*", RegexOption.IGNORE_CASE)
    return if (sourceIdRegex.containsMatchIn(url)) {
        url.replace(sourceIdRegex, "\$1sourceId=$encodedSourceId")
    } else {
        url.appendQueryParameter("sourceId=$encodedSourceId")
    }
}

private fun loadOrCreateSourceId(): String {
    val existing = runCatching { window.localStorage.getItem(CALLING_SOURCE_ID_KEY) }
        .getOrNull()
        ?.trim()
        ?.takeIf { it.length in 1..128 }
    if (existing != null) return existing
    val created = "web-${nowMillis()}-${Random.nextLong().toULong().toString(16)}"
    runCatching { window.localStorage.setItem(CALLING_SOURCE_ID_KEY, created) }
    return created
}

private fun String.appendQueryParameter(parameter: String): String {
    val fragmentIndex = indexOf('#')
    val base = if (fragmentIndex >= 0) substring(0, fragmentIndex) else this
    val fragment = if (fragmentIndex >= 0) substring(fragmentIndex) else ""
    val separator = if (base.contains('?')) "&" else "?"
    return "$base$separator$parameter$fragment"
}

private fun String.toWebSocketHost(): String {
    val host = trim().removePrefix("[").removeSuffix("]")
    return if (host.contains(':')) "[$host]" else host
}

private fun resolveCurrentTargetHost(overrideHost: String?): String? {
    return overrideHost?.trim()?.takeIf { it.isNotBlank() }
        ?: loadStoredTargetHost()?.takeIf { it.isNotBlank() }
}

private fun resolveCurrentTargetPort(overridePort: Int?): Int? {
    return overridePort?.takeIf { it in 1..65535 }
        ?: loadStoredTargetPort()?.takeIf { it in 1..65535 }
}

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

private fun loadStoredSourceState(): PersistedCallingSourceState? {
    return runCatching {
        CallingSourceStateCodec.decode(window.localStorage.getItem(CALLING_SOURCE_STATE_KEY))
    }.getOrNull()
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
private external fun encodeURIComponent(uriComponent: String): String

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
