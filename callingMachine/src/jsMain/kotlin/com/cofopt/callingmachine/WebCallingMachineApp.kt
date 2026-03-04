package com.cofopt.callingmachine

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.cofopt.callingmachine.cmp.CallingMachineApp
import kotlinx.browser.window
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.js.JSON
import org.w3c.dom.WebSocket
import org.w3c.dom.events.Event

private const val CALLING_TARGET_HOST_KEY = "cashregister.calling.target.host"
private const val CALLING_TARGET_PORT_KEY = "cashregister.calling.target.port"

@Composable
actual fun WebCallingMachineApp() {
    var reconnectNonce by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    var preparing by remember { mutableStateOf(emptyList<Int>()) }
    var ready by remember { mutableStateOf(emptyList<Int>()) }
    var preparingLabel by remember { mutableStateOf(CallingLanguage.EN.preparingLabel) }
    var readyLabel by remember { mutableStateOf(CallingLanguage.EN.readyLabel) }
    var isConnected by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("Connecting...") }
    var alertOverlayNumber by remember { mutableStateOf<Int?>(null) }
    var alertOverlayNonce by remember { mutableIntStateOf(0) }
    var newPreparingNumbers by remember { mutableStateOf(emptySet<Int>()) }

    var wsUrl by remember { mutableStateOf(resolveCallingMachineWsUrlOrNull()) }
    val localIpText = remember { resolveLocalIpForWeb() }

    DisposableEffect(wsUrl, reconnectNonce) {
        val targetUrl = wsUrl
        if (targetUrl.isNullOrBlank()) {
            isConnected = false
            statusText = "No Calling source configured. Use ?host=<LAN_IP>&port=9090 or set target in CashRegister Debug."
            return@DisposableEffect onDispose { }
        }

        val socket = WebSocket(targetUrl)
        statusText = "Connecting: $targetUrl"
        isConnected = false

        socket.onopen = { _: Event ->
            isConnected = true
            statusText = "Connected: viewer"
            null
        }

        socket.onmessage = { event: Event ->
            val data = event.asDynamic().data as? String
            if (data != null) {
                val obj = runCatching { JSON.parse<dynamic>(data) }.getOrNull()
                if (obj != null) {
                    when (val type = obj.type as? String ?: "") {
                        "calling_snapshot" -> {
                            val previousPreparing = preparing
                            val previousReady = ready
                            val nextPreparing = dynamicToIntList(obj.preparing)
                            val nextReady = dynamicToIntList(obj.ready)
                            val display =
                                CallingLanguage.fromWireValue(obj.displayLanguage as? String) ?: CallingLanguage.EN

                            preparing = nextPreparing
                            ready = nextReady
                            preparingLabel = display.preparingLabel
                            readyLabel = display.readyLabel

                            val movedToPreparing = nextPreparing.filter { it !in previousPreparing && it in previousReady }
                            if (movedToPreparing.isNotEmpty()) {
                                newPreparingNumbers = newPreparingNumbers + movedToPreparing
                                scope.launch {
                                    delay(5000)
                                    newPreparingNumbers = newPreparingNumbers - movedToPreparing.toSet()
                                }
                            }
                        }

                        "calling_alert" -> {
                            val number = (obj.number as? Number)?.toInt() ?: -1
                            if (number > 0) {
                                alertOverlayNumber = number
                                alertOverlayNonce++
                            }
                        }

                        else -> {
                            if (type.isNotBlank()) statusText = "Ignored message type: $type"
                        }
                    }
                }
            }
            null
        }

        socket.onerror = { _: Event ->
            isConnected = false
            statusText = "WebSocket error, reconnecting..."
            null
        }

        socket.onclose = { event: Event ->
            val closeEvent = event.asDynamic()
            val code = closeEvent.code as? Int
            val reason = closeEvent.reason as? String
            isConnected = false
            statusText = "Disconnected: code=${code ?: -1} reason=${reason.orEmpty()}"
            window.setTimeout({
                reconnectNonce++
            }, 2000)
            null
        }

        onDispose {
            socket.onopen = null
            socket.onmessage = null
            socket.onerror = null
            socket.onclose = null
            runCatching { socket.close() }
        }
    }

    LaunchedEffect(wsUrl) {
        val current = wsUrl
        if (current.isNullOrBlank()) return@LaunchedEffect
        if (current.contains("mode=viewer").not()) {
            statusText = "Tip: add ?mode=viewer to ws endpoint."
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            val next = resolveCallingMachineWsUrlOrNull()
            if (next != wsUrl) {
                wsUrl = next
                reconnectNonce++
            }
            delay(1500)
        }
    }

    CallingMachineApp(
        preparing = preparing,
        ready = ready,
        preparingLabel = preparingLabel,
        readyLabel = readyLabel,
        statusText = statusText,
        isConnected = isConnected,
        localIp = localIpText,
        alertOverlayNumber = alertOverlayNumber,
        alertOverlayNonce = alertOverlayNonce,
        isPreparingNumber = { it in newPreparingNumbers }
    )
}

private fun resolveCallingMachineWsUrlOrNull(): String? {
    val params = parseQuery(window.location.search.orEmpty())
    val fromQuery = params["ws"].orEmpty()
    if (fromQuery.startsWith("ws://") || fromQuery.startsWith("wss://")) {
        return ensureViewerMode(fromQuery)
    }

    val queryHost = params["host"].orEmpty().trim()
    val queryPort = params["port"]?.toIntOrNull()?.takeIf { it in 1..65535 }
    if (queryHost.isNotBlank()) {
        val scheme = if (window.location.protocol == "https:") "wss" else "ws"
        val port = queryPort ?: 9090
        return "$scheme://$queryHost:$port/?mode=viewer"
    }

    val savedHost = runCatching {
        window.localStorage.getItem(CALLING_TARGET_HOST_KEY)
    }.getOrNull().orEmpty().trim()
    val savedPort = runCatching {
        window.localStorage.getItem(CALLING_TARGET_PORT_KEY)?.toIntOrNull()
    }.getOrNull()?.takeIf { it in 1..65535 }
    if (savedHost.isNotBlank() && savedPort != null) {
        val scheme = if (window.location.protocol == "https:") "wss" else "ws"
        return "$scheme://$savedHost:$savedPort/?mode=viewer"
    }

    val host = window.location.hostname.orEmpty().trim()
    if (!isLikelyLocalLanHost(host)) return null
    val scheme = if (window.location.protocol == "https:") "wss" else "ws"
    return "$scheme://$host:9090/?mode=viewer"
}

private fun ensureViewerMode(raw: String): String {
    val url = raw.trim()
    if (url.isBlank()) return url
    if (url.contains("mode=viewer")) return url
    val separator = if (url.contains("?")) "&" else "?"
    return "${url}${separator}mode=viewer"
}

private fun resolveLocalIpForWeb(): String {
    val host = window.location.hostname.orEmpty().trim()
    if (!isLikelyLocalLanHost(host)) return "-"
    return if (host.isBlank()) "-" else host
}

private fun isLikelyLocalLanHost(host: String): Boolean {
    val clean = host.trim().lowercase()
    if (clean.isBlank()) return false
    if (clean == "localhost" || clean == "127.0.0.1" || clean == "::1") return true
    if (!isIpv4Address(clean)) return false
    val parts = clean.split('.')
    val a = parts[0].toIntOrNull() ?: return false
    val b = parts[1].toIntOrNull() ?: return false
    return when {
        a == 10 -> true
        a == 192 && b == 168 -> true
        a == 172 && b in 16..31 -> true
        else -> false
    }
}

private fun isIpv4Address(host: String): Boolean {
    val parts = host.trim().split('.')
    if (parts.size != 4) return false
    return parts.all { part ->
        val value = part.toIntOrNull() ?: return@all false
        value in 0..255
    }
}

@Suppress("UnsafeCastFromDynamic")
private fun dynamicToIntList(value: dynamic): List<Int> {
    val length = (value?.length as? Int) ?: return emptyList()
    val out = ArrayList<Int>(length)
    for (i in 0 until length) {
        val n = value[i] as? Number ?: continue
        out.add(n.toInt())
    }
    return out
}

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
