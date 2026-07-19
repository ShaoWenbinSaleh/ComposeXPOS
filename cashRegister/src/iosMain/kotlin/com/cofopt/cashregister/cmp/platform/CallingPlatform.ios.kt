package com.cofopt.cashregister.cmp.platform

import com.cofopt.shared.network.IosCallingEndpointStore
import com.cofopt.shared.network.IosCallingWebSocketClient
import com.cofopt.shared.network.IosCallingWebSocketRole
import com.cofopt.shared.network.IosCallingWebSocketSendResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import platform.Foundation.NSUserDefaults
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

internal data class IosCallingBridgeStatus(
    val connected: Boolean = false,
    val targetHost: String? = null,
    val targetPort: Int? = null,
    val reconnectAttempts: Int = 0,
    val lastError: String? = null,
    val displayLanguage: String = "zh",
    val voiceLanguage: String = "zh",
)

actual object CallingPlatform {
    private val persistedState = IosCallingStateStore.load()
    private val preparingState = MutableStateFlow(persistedState?.preparing.orEmpty())
    private val readyState = MutableStateFlow(persistedState?.ready.orEmpty())
    private var displayLanguage = persistedState?.displayLanguage ?: "zh"
    private var voiceLanguage = persistedState?.voiceLanguage ?: "zh"
    private var sourceStateDurable = persistedState != null
    private var lifecycleStarted = false
    private var alertSequence = 0L
    private val statusState = MutableStateFlow(
        IosCallingBridgeStatus(
            targetHost = IosCallingEndpointStore.loadHost(),
            targetPort = IosCallingEndpointStore.loadPort(),
            displayLanguage = displayLanguage,
            voiceLanguage = voiceLanguage,
        )
    )

    actual val preparing: StateFlow<List<Int>> = preparingState
    actual val ready: StateFlow<List<Int>> = readyState
    internal val bridgeStatus: StateFlow<IosCallingBridgeStatus> = statusState

    private val webSocketClient = IosCallingWebSocketClient(
        role = IosCallingWebSocketRole.SOURCE,
        onStatus = { transportStatus ->
            statusState.value = statusState.value.copy(
                connected = transportStatus.connected,
                reconnectAttempts = transportStatus.reconnectAttempts,
                lastError = transportStatus.lastError,
            )
        },
        // The protocol requires a full snapshot after every successful
        // connection/reconnection; incremental state is never assumed.
        onConnected = { pushSnapshot() },
    )

    internal fun startCallingBridge() {
        if (lifecycleStarted) return
        lifecycleStarted = true
        if (!sourceStateDurable && IosCallingEndpointStore.loadHost() != null) {
            statusState.value = statusState.value.copy(lastError = "calling_source_state_missing")
            return
        }
        connectConfiguredTarget()
    }

    internal fun stopCallingBridge() {
        if (!lifecycleStarted) return
        lifecycleStarted = false
        // Stop network activity with the scene, but retain any at-least-once
        // alerts so a recreated scene can deliver them after reconnecting.
        webSocketClient.disconnect(clearQueuedMessages = false)
        statusState.value = statusState.value.copy(connected = false)
    }

    actual fun clearPreparing() {
        updateCallingState(emptyList(), readyState.value)
    }

    actual fun clearReady() {
        updateCallingState(preparingState.value, emptyList())
    }

    actual fun markReady(number: Int) {
        if (number <= 0) return
        updateCallingState(
            preparing = preparingState.value.filterNot { it == number },
            ready = (readyState.value + number).distinct(),
        )
    }

    actual fun complete(number: Int) {
        updateCallingState(
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
        val saved = updateCallingState(
            preparing = preparingState.value.filterNot { it == number },
            ready = readyState.value + number,
        )
        return if (saved) ManualCallAddResult.Added else ManualCallAddResult.PersistenceFailed
    }

    actual fun addManualPreparing(number: Int): ManualCallAddResult {
        if (number !in 1..999) return ManualCallAddResult.OutOfRange
        if (preparingState.value.contains(number)) return ManualCallAddResult.Duplicate
        val saved = updateCallingState(
            preparing = preparingState.value + number,
            ready = readyState.value.filterNot { it == number },
        )
        return if (saved) ManualCallAddResult.Added else ManualCallAddResult.PersistenceFailed
    }

    actual fun sendAlert(number: Int) {
        if (number <= 0) return
        val payload = buildJsonObject {
            put("type", JsonPrimitive("calling_alert"))
            put("number", JsonPrimitive(number))
            put("ts", JsonPrimitive(nowMillis()))
            put(
                "eventId",
                JsonPrimitive(
                    "${IosCallingEndpointStore.loadOrCreateSourceId()}-${nowMillis()}-${++alertSequence}"
                )
            )
        }
        webSocketClient.send(
            text = payload.toString(),
            queueIfDisconnected = true,
        ) { result ->
            when (result) {
                IosCallingWebSocketSendResult.SENT -> {
                    if (statusState.value.lastError in ALERT_DELIVERY_ERRORS) {
                        statusState.value = statusState.value.copy(lastError = null)
                    }
                }
                IosCallingWebSocketSendResult.QUEUED -> {
                    statusState.value = statusState.value.copy(
                        lastError = "alert_queued_until_reconnect"
                    )
                }
                IosCallingWebSocketSendResult.DROPPED -> {
                    statusState.value = statusState.value.copy(lastError = "alert_delivery_failed")
                }
            }
        }
    }

    internal fun connectToCallingMachine(host: String, port: Int): Boolean {
        if (!persistCurrentValues()) return false
        if (!IosCallingEndpointStore.save(host, port)) return false
        val normalizedHost = IosCallingEndpointStore.loadHost() ?: return false
        val normalizedPort = IosCallingEndpointStore.loadPort()
        statusState.value = statusState.value.copy(
            connected = false,
            targetHost = normalizedHost,
            targetPort = normalizedPort,
            reconnectAttempts = 0,
            lastError = null,
        )
        webSocketClient.connect(normalizedHost, normalizedPort)
        return true
    }

    internal fun disconnectCallingMachine() {
        webSocketClient.disconnect()
        statusState.value = statusState.value.copy(connected = false)
    }

    internal fun updateCallingLanguages(display: String, voice: String): Boolean {
        val nextDisplay = normalizeCallingLanguage(display) ?: return false
        val nextVoice = normalizeCallingLanguage(voice) ?: return false
        if (displayLanguage == nextDisplay && voiceLanguage == nextVoice) return true
        if (
            !IosCallingStateStore.save(
                preparing = preparingState.value,
                ready = readyState.value,
                displayLanguage = nextDisplay,
                voiceLanguage = nextVoice,
            )
        ) {
            statusState.value = statusState.value.copy(lastError = "calling_state_persist_failed")
            return false
        }
        sourceStateDurable = true
        displayLanguage = nextDisplay
        voiceLanguage = nextVoice
        statusState.value = statusState.value.copy(
            displayLanguage = displayLanguage,
            voiceLanguage = voiceLanguage,
        )
        if (lifecycleStarted) connectConfiguredTarget()
        pushSnapshot()
        return true
    }

    private fun connectConfiguredTarget() {
        val host = IosCallingEndpointStore.loadHost() ?: return
        val port = IosCallingEndpointStore.loadPort()
        webSocketClient.connect(host, port)
    }

    private fun pushSnapshot() {
        val payload = buildJsonObject {
            put("type", JsonPrimitive("calling_snapshot"))
            put("preparing", JsonArray(preparingState.value.map(::JsonPrimitive)))
            put("ready", JsonArray(readyState.value.map(::JsonPrimitive)))
            put("displayLanguage", JsonPrimitive(displayLanguage))
            put("voiceLanguage", JsonPrimitive(voiceLanguage))
            put("ts", JsonPrimitive(nowMillis()))
        }
        webSocketClient.send(payload.toString())
    }

    private fun updateCallingState(preparing: List<Int>, ready: List<Int>): Boolean {
        val nextReady = ready.filter { it in 1..999 }.distinct()
        val nextPreparing = preparing
            .filter { it in 1..999 && it !in nextReady }
            .distinct()
        if (
            nextPreparing == preparingState.value &&
            nextReady == readyState.value &&
            sourceStateDurable
        ) return true
        if (
            !IosCallingStateStore.save(
                preparing = nextPreparing,
                ready = nextReady,
                displayLanguage = displayLanguage,
                voiceLanguage = voiceLanguage,
            )
        ) {
            statusState.value = statusState.value.copy(lastError = "calling_state_persist_failed")
            return false
        }
        sourceStateDurable = true
        preparingState.value = nextPreparing
        readyState.value = nextReady
        if (lifecycleStarted) connectConfiguredTarget()
        pushSnapshot()
        return true
    }

    private fun persistCurrentValues(): Boolean {
        val saved = IosCallingStateStore.save(
            preparing = preparingState.value,
            ready = readyState.value,
            displayLanguage = displayLanguage,
            voiceLanguage = voiceLanguage,
        )
        sourceStateDurable = saved
        if (!saved) {
            statusState.value = statusState.value.copy(lastError = "calling_state_persist_failed")
        }
        return saved
    }

    private val ALERT_DELIVERY_ERRORS = setOf(
        "alert_queued_until_reconnect",
        "alert_delivery_failed",
    )
}

private data class IosPersistedCallingState(
    val preparing: List<Int> = emptyList(),
    val ready: List<Int> = emptyList(),
    val displayLanguage: String = "zh",
    val voiceLanguage: String = "zh",
)

private object IosCallingStateStore {
    private const val STATE_KEY = "ComposeXPOSCallingStateV1"
    private val json = Json { ignoreUnknownKeys = true }

    fun load(): IosPersistedCallingState? {
        val raw = NSUserDefaults.standardUserDefaults.stringForKey(STATE_KEY)
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return runCatching {
            val obj = json.parseToJsonElement(raw).jsonObject
            val ready = obj["ready"]?.jsonArray
                ?.mapNotNull { it.jsonPrimitive.intOrNull }
                ?.filter { it in 1..999 }
                ?.distinct()
                .orEmpty()
            val preparing = obj["preparing"]?.jsonArray
                ?.mapNotNull { it.jsonPrimitive.intOrNull }
                ?.filter { it in 1..999 && it !in ready }
                ?.distinct()
                .orEmpty()
            IosPersistedCallingState(
                preparing = preparing,
                ready = ready,
                displayLanguage = normalizeCallingLanguage(
                    obj["displayLanguage"]?.jsonPrimitive?.contentOrNull
                ) ?: "zh",
                voiceLanguage = normalizeCallingLanguage(
                    obj["voiceLanguage"]?.jsonPrimitive?.contentOrNull
                ) ?: "zh",
            )
        }.getOrNull()
    }

    fun save(
        preparing: List<Int>,
        ready: List<Int>,
        displayLanguage: String,
        voiceLanguage: String,
    ): Boolean {
        val payload = buildJsonObject {
            put("preparing", JsonArray(preparing.map(::JsonPrimitive)))
            put("ready", JsonArray(ready.map(::JsonPrimitive)))
            put("displayLanguage", JsonPrimitive(displayLanguage))
            put("voiceLanguage", JsonPrimitive(voiceLanguage))
        }.toString()
        return runCatching {
            NSUserDefaults.standardUserDefaults.setObject(payload, forKey = STATE_KEY)
            NSUserDefaults.standardUserDefaults.synchronize()
        }.getOrDefault(false)
    }
}

private fun normalizeCallingLanguage(raw: String?): String? {
    return raw.orEmpty().trim().lowercase().takeIf { it in setOf("en", "zh", "nl", "ja", "tr") }
}

@OptIn(ExperimentalTime::class)
private fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()
