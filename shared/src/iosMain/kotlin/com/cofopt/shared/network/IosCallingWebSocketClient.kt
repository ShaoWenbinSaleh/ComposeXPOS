package com.cofopt.shared.network

import kotlinx.cinterop.BetaInteropApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.NSNumber
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSURLComponents
import platform.Foundation.NSURLQueryItem
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionWebSocketCloseCodeNormalClosure
import platform.Foundation.NSURLSessionWebSocketMessage
import platform.Foundation.NSURLSessionWebSocketTask
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDefaults
import platform.Foundation.create

enum class IosCallingWebSocketRole(val wireValue: String) {
    SOURCE("source"),
    VIEWER("viewer"),
}

data class IosCallingWebSocketStatus(
    val connected: Boolean = false,
    val reconnectAttempts: Int = 0,
    val lastError: String? = null,
)

enum class IosCallingWebSocketSendResult {
    SENT,
    QUEUED,
    DROPPED,
}

private data class IosCallingQueuedMessage(
    val id: Long,
    val text: String,
    val onResult: (IosCallingWebSocketSendResult) -> Unit,
)

private data class ParsedIosCallingEndpoint(
    val scheme: String,
    val host: String,
    val port: Int,
) {
    val displayHost: String
        get() = if (scheme == "ws") host.toUrlHost() else "$scheme://${host.toUrlHost()}"
}

/**
 * iOS calling-protocol transport backed by NSURLSessionWebSocketTask.
 * All mutable connection state is confined to Dispatchers.Main so URLSession
 * callbacks, reconnect timers, and UI actions cannot race stale sockets.
 */
class IosCallingWebSocketClient(
    private val role: IosCallingWebSocketRole,
    private val onMessage: (String) -> Unit = {},
    private val onStatus: (IosCallingWebSocketStatus) -> Unit = {},
    private val onConnected: () -> Unit = {},
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var task: NSURLSessionWebSocketTask? = null
    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null
    private var generation: Int = 0
    private var shouldReconnect: Boolean = false
    private var connected: Boolean = false
    private var reconnectAttempts: Int = 0
    private var targetEndpoint: ParsedIosCallingEndpoint? = null
    private val queuedMessages = ArrayList<IosCallingQueuedMessage>()
    private val inFlightMessageIds = LinkedHashSet<Long>()
    private var nextMessageId: Long = 0L

    fun connect(host: String, port: Int) {
        scope.launch {
            val endpoint = parseIosCallingEndpoint(host, port)
            if (endpoint == null) {
                closeCurrentSocket(disableReconnect = true)
                emitStatus(error = "invalid_host_or_port")
                return@launch
            }

            val sameTarget = targetEndpoint == endpoint
            targetEndpoint = endpoint
            shouldReconnect = true
            if (sameTarget && task != null) return@launch

            reconnectAttempts = 0
            closeCurrentSocket(disableReconnect = false)
            openSocket(endpoint)
        }
    }

    fun send(
        text: String,
        queueIfDisconnected: Boolean = false,
        onResult: (IosCallingWebSocketSendResult) -> Unit = {},
    ) {
        if (text.isBlank()) {
            onResult(IosCallingWebSocketSendResult.DROPPED)
            return
        }
        scope.launch {
            val activeTask = task
            val activeGeneration = generation
            if (queueIfDisconnected) {
                if (!shouldReconnect) {
                    onResult(IosCallingWebSocketSendResult.DROPPED)
                    return@launch
                }
                if (queuedMessages.size >= MAX_QUEUED_MESSAGES) {
                    emitStatus(error = "send_queue_full")
                    onResult(IosCallingWebSocketSendResult.DROPPED)
                    return@launch
                }
                val queued = IosCallingQueuedMessage(
                    id = ++nextMessageId,
                    text = text,
                    onResult = onResult,
                )
                queuedMessages += queued
                if (!connected || activeTask == null) {
                    onResult(IosCallingWebSocketSendResult.QUEUED)
                    return@launch
                }
                sendQueuedMessage(queued, activeTask, activeGeneration)
                return@launch
            }

            if (!connected || activeTask == null) {
                onResult(IosCallingWebSocketSendResult.DROPPED)
                return@launch
            }
            sendDirectMessage(text, activeTask, activeGeneration, onResult)
        }
    }

    fun disconnect(clearQueuedMessages: Boolean = true) {
        scope.launch {
            closeCurrentSocket(disableReconnect = true)
            if (clearQueuedMessages) clearQueuedMessages()
            reconnectAttempts = 0
            emitStatus(error = null)
        }
    }

    fun close() {
        shouldReconnect = false
        generation++
        reconnectJob?.cancel()
        heartbeatJob?.cancel()
        task?.cancelWithCloseCode(NSURLSessionWebSocketCloseCodeNormalClosure, null)
        task = null
        connected = false
        clearQueuedMessages()
        scope.cancel()
    }

    private fun openSocket(endpoint: ParsedIosCallingEndpoint) {
        if (!shouldReconnect) return
        val url = callingWebSocketUrl(endpoint, role)
        if (url == null) {
            emitStatus(error = "invalid_websocket_url")
            scheduleReconnect(generation)
            return
        }

        val activeGeneration = ++generation
        val socket = NSURLSession.sharedSession.webSocketTaskWithURL(url)
        task = socket
        connected = false
        emitStatus(error = null)
        socket.resume()

        receiveNext(socket, activeGeneration)
        socket.sendPingWithPongReceiveHandler { error ->
            scope.launch {
                if (!isCurrent(socket, activeGeneration)) return@launch
                if (error != null) {
                    handleFailure(socket, activeGeneration, error.localizedDescription)
                    return@launch
                }

                connected = true
                reconnectAttempts = 0
                emitStatus(error = null)
                onConnected()
                startHeartbeat(socket, activeGeneration)
                // onConnected normally queues the authoritative full snapshot;
                // flush events on the next Main turn so state precedes alerts.
                scope.launch { flushQueuedMessages(socket, activeGeneration) }
            }
        }
    }

    private fun receiveNext(socket: NSURLSessionWebSocketTask, activeGeneration: Int) {
        socket.receiveMessageWithCompletionHandler { message, error ->
            scope.launch {
                if (!isCurrent(socket, activeGeneration)) return@launch
                if (error != null) {
                    handleFailure(socket, activeGeneration, error.localizedDescription)
                    return@launch
                }

                val text = message?.string
                    ?: message?.data?.decodeUtf8OrNull()
                if (!text.isNullOrBlank()) onMessage(text)
                if (isCurrent(socket, activeGeneration)) receiveNext(socket, activeGeneration)
            }
        }
    }

    private fun startHeartbeat(socket: NSURLSessionWebSocketTask, activeGeneration: Int) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive && isCurrent(socket, activeGeneration)) {
                delay(HEARTBEAT_INTERVAL_MS)
                if (!isCurrent(socket, activeGeneration)) break
                socket.sendPingWithPongReceiveHandler { error ->
                    if (error == null) return@sendPingWithPongReceiveHandler
                    scope.launch {
                        if (isCurrent(socket, activeGeneration)) {
                            handleFailure(socket, activeGeneration, error.localizedDescription)
                        }
                    }
                }
            }
        }
    }

    private fun handleFailure(
        socket: NSURLSessionWebSocketTask,
        activeGeneration: Int,
        message: String,
    ) {
        if (!isCurrent(socket, activeGeneration)) return
        heartbeatJob?.cancel()
        heartbeatJob = null
        task = null
        connected = false
        inFlightMessageIds.clear()
        socket.cancel()
        emitStatus(error = message.ifBlank { "websocket_error" })
        scheduleReconnect(activeGeneration)
    }

    private fun scheduleReconnect(activeGeneration: Int) {
        if (!shouldReconnect || activeGeneration != generation || reconnectJob?.isActive == true) return
        reconnectAttempts = (reconnectAttempts + 1).coerceAtMost(Int.MAX_VALUE)
        val shift = (reconnectAttempts - 1).coerceIn(0, 4)
        val delayMillis = (BASE_RECONNECT_DELAY_MS * (1L shl shift))
            .coerceAtMost(MAX_RECONNECT_DELAY_MS)
        emitStatus(error = null, preserveError = true)

        reconnectJob = scope.launch {
            delay(delayMillis)
            if (!shouldReconnect || activeGeneration != generation || task != null) return@launch
            val endpoint = targetEndpoint ?: return@launch
            reconnectJob = null
            openSocket(endpoint)
        }
    }

    private fun sendDirectMessage(
        text: String,
        socket: NSURLSessionWebSocketTask,
        activeGeneration: Int,
        onResult: (IosCallingWebSocketSendResult) -> Unit,
    ) {
        socket.sendMessage(NSURLSessionWebSocketMessage(text)) { error ->
            scope.launch {
                if (error == null) {
                    onResult(IosCallingWebSocketSendResult.SENT)
                    return@launch
                }
                onResult(IosCallingWebSocketSendResult.DROPPED)
                if (isCurrent(socket, activeGeneration)) {
                    handleFailure(socket, activeGeneration, error.localizedDescription)
                }
            }
        }
    }

    private fun sendQueuedMessage(
        message: IosCallingQueuedMessage,
        socket: NSURLSessionWebSocketTask,
        activeGeneration: Int,
    ) {
        if (!inFlightMessageIds.add(message.id)) return
        socket.sendMessage(NSURLSessionWebSocketMessage(message.text)) { error ->
            scope.launch {
                inFlightMessageIds.remove(message.id)
                if (error == null) {
                    if (queuedMessages.removeAll { it.id == message.id }) {
                        message.onResult(IosCallingWebSocketSendResult.SENT)
                    }
                    return@launch
                }

                if (queuedMessages.any { it.id == message.id }) {
                    message.onResult(IosCallingWebSocketSendResult.QUEUED)
                }
                if (isCurrent(socket, activeGeneration)) {
                    handleFailure(socket, activeGeneration, error.localizedDescription)
                }
            }
        }
    }

    private fun flushQueuedMessages(
        socket: NSURLSessionWebSocketTask,
        activeGeneration: Int,
    ) {
        if (!connected || !isCurrent(socket, activeGeneration)) return
        queuedMessages.toList().forEach { message ->
            sendQueuedMessage(message, socket, activeGeneration)
        }
    }

    private fun clearQueuedMessages() {
        val pending = queuedMessages.toList()
        queuedMessages.clear()
        inFlightMessageIds.clear()
        pending.forEach { it.onResult(IosCallingWebSocketSendResult.DROPPED) }
    }

    private fun closeCurrentSocket(disableReconnect: Boolean) {
        if (disableReconnect) shouldReconnect = false
        generation++
        reconnectJob?.cancel()
        reconnectJob = null
        heartbeatJob?.cancel()
        heartbeatJob = null
        val socket = task
        task = null
        connected = false
        inFlightMessageIds.clear()
        socket?.cancelWithCloseCode(NSURLSessionWebSocketCloseCodeNormalClosure, null)
    }

    private fun isCurrent(socket: NSURLSessionWebSocketTask, activeGeneration: Int): Boolean {
        return task === socket && generation == activeGeneration && shouldReconnect
    }

    private fun emitStatus(error: String?, preserveError: Boolean = false) {
        val previousError = if (preserveError) lastEmittedError else null
        lastEmittedError = error ?: previousError
        onStatus(
            IosCallingWebSocketStatus(
                connected = connected,
                reconnectAttempts = reconnectAttempts,
                lastError = lastEmittedError,
            )
        )
    }

    private var lastEmittedError: String? = null

    private companion object {
        const val HEARTBEAT_INTERVAL_MS = 20_000L
        const val BASE_RECONNECT_DELAY_MS = 2_000L
        const val MAX_RECONNECT_DELAY_MS = 30_000L
        const val MAX_QUEUED_MESSAGES = 100
    }
}

object IosCallingEndpointStore {
    private const val HOST_DEFAULTS_KEY = "ComposeXPOSCallingHost"
    private const val PORT_DEFAULTS_KEY = "ComposeXPOSCallingPort"
    private const val SOURCE_ID_DEFAULTS_KEY = "ComposeXPOSCallingSourceId"

    fun loadHost(): String? {
        val defaultsValue = NSUserDefaults.standardUserDefaults
            .stringForKey(HOST_DEFAULTS_KEY)
            ?.trim()
            .orEmpty()
        if (defaultsValue.isNotBlank()) {
            normalizeIosCallingHost(defaultsValue)?.let { return it }
        }

        val plistValue = NSBundle.mainBundle
            .objectForInfoDictionaryKey(HOST_DEFAULTS_KEY)
            ?.toString()
            ?.trim()
            .orEmpty()
        return normalizeIosCallingHost(plistValue)
    }

    fun loadPort(): Int {
        val defaultsPort = NSUserDefaults.standardUserDefaults.integerForKey(PORT_DEFAULTS_KEY).toInt()
        if (defaultsPort in 1..65535) return defaultsPort

        val plistValue = NSBundle.mainBundle.objectForInfoDictionaryKey(PORT_DEFAULTS_KEY)
        val plistPort = when (plistValue) {
            is NSNumber -> plistValue.intValue
            else -> plistValue?.toString()?.toIntOrNull()
        }
        return plistPort?.takeIf { it in 1..65535 } ?: DEFAULT_CALLING_PORT
    }

    fun save(host: String, port: Int): Boolean {
        val endpoint = parseIosCallingEndpoint(host, port) ?: return false
        val defaults = NSUserDefaults.standardUserDefaults
        return runCatching {
            defaults.setObject(endpoint.displayHost, forKey = HOST_DEFAULTS_KEY)
            defaults.setInteger(endpoint.port.toLong(), forKey = PORT_DEFAULTS_KEY)
            defaults.synchronize()
        }.getOrDefault(false)
    }

    fun loadOrCreateSourceId(): String {
        val defaults = NSUserDefaults.standardUserDefaults
        val existing = defaults.stringForKey(SOURCE_ID_DEFAULTS_KEY)
            ?.trim()
            ?.takeIf(::isValidIosCallingSourceId)
        if (existing != null) return existing
        val created = "ios-${NSUUID().UUIDString.lowercase()}"
        defaults.setObject(created, forKey = SOURCE_ID_DEFAULTS_KEY)
        defaults.synchronize()
        return created
    }

    private const val DEFAULT_CALLING_PORT = 9090
}

fun normalizeIosCallingHost(rawHost: String?): String? {
    return parseIosCallingEndpoint(rawHost, 9090)?.displayHost
}

private fun callingWebSocketUrl(
    endpoint: ParsedIosCallingEndpoint,
    role: IosCallingWebSocketRole,
) = NSURLComponents().apply {
    scheme = endpoint.scheme
    this.host = endpoint.host
    this.port = NSNumber(int = endpoint.port)
    path = "/"
    queryItems = buildList {
        add(NSURLQueryItem(name = "mode", value = role.wireValue))
        if (role == IosCallingWebSocketRole.SOURCE) {
            add(NSURLQueryItem(name = "key", value = CALLING_WS_SHARED_KEY))
            add(
                NSURLQueryItem(
                    name = "sourceId",
                    value = IosCallingEndpointStore.loadOrCreateSourceId(),
                )
            )
        }
    }
}.URL

private fun parseIosCallingEndpoint(rawHost: String?, fallbackPort: Int): ParsedIosCallingEndpoint? {
    if (fallbackPort !in 1..65535) return null
    var authority = rawHost.orEmpty().trim()
    if (authority.isBlank()) return null

    val lowercase = authority.lowercase()
    val inputScheme = listOf("wss://", "ws://", "https://", "http://")
        .firstOrNull { lowercase.startsWith(it) }
    val scheme = when (inputScheme) {
        "wss://", "https://" -> "wss"
        else -> "ws"
    }
    if (inputScheme != null) authority = authority.substring(inputScheme.length)
    authority = authority.trim().trimEnd('/')
    if (authority.isBlank() || authority.any { it.isWhitespace() || it == '/' || it == '?' || it == '#' || it == '@' }) {
        return null
    }

    var embeddedPort: Int? = null
    val host = if (authority.startsWith('[')) {
        val closingBracket = authority.indexOf(']')
        if (closingBracket <= 1) return null
        val suffix = authority.substring(closingBracket + 1)
        if (suffix.isNotBlank()) {
            if (!suffix.startsWith(':')) return null
            embeddedPort = suffix.drop(1).toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
        }
        authority.substring(1, closingBracket)
    } else if (authority.count { it == ':' } == 1) {
        val colon = authority.lastIndexOf(':')
        val possiblePort = authority.substring(colon + 1).toIntOrNull()
        if (possiblePort != null) {
            embeddedPort = possiblePort.takeIf { it in 1..65535 } ?: return null
            authority.substring(0, colon)
        } else {
            return null
        }
    } else {
        authority
    }.trim()

    if (host.isBlank()) return null
    return ParsedIosCallingEndpoint(
        scheme = scheme,
        host = host,
        port = embeddedPort ?: fallbackPort,
    )
}

private fun String.toUrlHost(): String {
    return if (contains(':')) "[$this]" else this
}

private fun isValidIosCallingSourceId(value: String): Boolean {
    return value.length in 1..128 && value.all {
        it.isLetterOrDigit() || it == '-' || it == '_' || it == '.' || it == ':'
    }
}

@OptIn(BetaInteropApi::class)
private fun NSData.decodeUtf8OrNull(): String? {
    return NSString.create(
        data = this,
        encoding = NSUTF8StringEncoding,
    )?.toString()
}
