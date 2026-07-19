package com.cofopt.callingmachine

import com.cofopt.shared.network.IosCallingEndpointStore
import com.cofopt.shared.network.IosCallingWebSocketClient
import com.cofopt.shared.network.IosCallingWebSocketRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal class IosCallingMachineBridge {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val json = Json { ignoreUnknownKeys = true }

    private val preparingState = MutableStateFlow(emptyList<Int>())
    private val readyState = MutableStateFlow(emptyList<Int>())
    private val preparingLabelState = MutableStateFlow(CallingLanguage.EN.preparingLabel)
    private val readyLabelState = MutableStateFlow(CallingLanguage.EN.readyLabel)
    private val connectedState = MutableStateFlow(false)
    private val statusTextState = MutableStateFlow("Waiting for source...")
    private val alertNumberState = MutableStateFlow<Int?>(null)
    private val alertNonceState = MutableStateFlow(0)
    private val newPreparingNumbersState = MutableStateFlow(emptySet<Int>())

    val preparing: StateFlow<List<Int>> = preparingState
    val ready: StateFlow<List<Int>> = readyState
    val preparingLabel: StateFlow<String> = preparingLabelState
    val readyLabel: StateFlow<String> = readyLabelState
    val connected: StateFlow<Boolean> = connectedState
    val statusText: StateFlow<String> = statusTextState
    val alertNumber: StateFlow<Int?> = alertNumberState
    val alertNonce: StateFlow<Int> = alertNonceState
    val newPreparingNumbers: StateFlow<Set<Int>> = newPreparingNumbersState

    private val client = IosCallingWebSocketClient(
        role = IosCallingWebSocketRole.VIEWER,
        onMessage = ::handleMessage,
        onStatus = { status ->
            connectedState.value = status.connected
            statusTextState.value = when {
                status.connected -> "Connected: viewer"
                !status.lastError.isNullOrBlank() -> {
                    "Reconnecting (${status.reconnectAttempts}): ${status.lastError}"
                }
                status.reconnectAttempts > 0 -> "Reconnecting (${status.reconnectAttempts})..."
                else -> "Connecting..."
            }
        },
    )

    fun start() {
        val host = IosCallingEndpointStore.loadHost()
        if (host == null) {
            connectedState.value = false
            statusTextState.value = "Set ComposeXPOSCallingHost in app configuration"
            return
        }
        val port = IosCallingEndpointStore.loadPort()
        statusTextState.value = "Connecting to $host:$port..."
        client.connect(host, port)
    }

    fun close() {
        client.close()
        scope.cancel()
    }

    private fun handleMessage(raw: String) {
        runCatching { handleMessageOrThrow(raw) }
    }

    /** A malformed peer frame is ignored without terminating the receive loop. */
    private fun handleMessageOrThrow(raw: String) {
        val obj = json.parseToJsonElement(raw).jsonObject
        when (obj["type"]?.jsonPrimitive?.contentOrNull) {
            "calling_snapshot" -> {
                val previousPreparing = preparingState.value
                val previousReady = readyState.value
                val nextPreparing = obj["preparing"]
                    ?.jsonArray
                    ?.mapNotNull { it.jsonPrimitive.intOrNull }
                    .orEmpty()
                val nextReady = obj["ready"]
                    ?.jsonArray
                    ?.mapNotNull { it.jsonPrimitive.intOrNull }
                    .orEmpty()
                val displayLanguage = CallingLanguage.fromWireValue(
                    obj["displayLanguage"]?.jsonPrimitive?.contentOrNull
                ) ?: CallingLanguage.EN

                preparingState.value = nextPreparing
                readyState.value = nextReady
                preparingLabelState.value = displayLanguage.preparingLabel
                readyLabelState.value = displayLanguage.readyLabel

                val movedToPreparing = nextPreparing
                    .filter { it !in previousPreparing && it in previousReady }
                    .toSet()
                if (movedToPreparing.isNotEmpty()) {
                    newPreparingNumbersState.value += movedToPreparing
                    scope.launch {
                        delay(5_000L)
                        newPreparingNumbersState.value -= movedToPreparing
                    }
                }
            }

            "calling_alert" -> {
                val number = obj["number"]?.jsonPrimitive?.intOrNull ?: return
                if (number > 0) {
                    alertNumberState.value = number
                    alertNonceState.value += 1
                }
            }
        }
    }
}
