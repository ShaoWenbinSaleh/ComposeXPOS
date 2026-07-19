package com.cofopt.cashregister.cmp.platform

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Durable state owned by a Cash Register acting as a CallingMachine source. */
@Serializable
internal data class PersistedCallingSourceState(
    val version: Int = CURRENT_CALLING_SOURCE_STATE_VERSION,
    val preparing: List<Int> = emptyList(),
    val ready: List<Int> = emptyList(),
)

internal object CallingSourceStateCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(preparing: List<Int>, ready: List<Int>): String {
        return json.encodeToString(normalize(preparing, ready))
    }

    fun decode(raw: String?): PersistedCallingSourceState? {
        if (raw.isNullOrBlank()) return null
        val decoded = runCatching {
            json.decodeFromString<PersistedCallingSourceState>(raw)
        }.getOrNull() ?: return null
        if (decoded.version != CURRENT_CALLING_SOURCE_STATE_VERSION) return null
        return normalize(decoded.preparing, decoded.ready)
    }

    private fun normalize(
        preparing: List<Int>,
        ready: List<Int>,
    ): PersistedCallingSourceState {
        val normalizedReady = ready.filter { it in 1..999 }.distinct()
        val normalizedPreparing = preparing
            .filter { it in 1..999 && it !in normalizedReady }
            .distinct()
        return PersistedCallingSourceState(
            preparing = normalizedPreparing,
            ready = normalizedReady,
        )
    }
}

private const val CURRENT_CALLING_SOURCE_STATE_VERSION = 1
