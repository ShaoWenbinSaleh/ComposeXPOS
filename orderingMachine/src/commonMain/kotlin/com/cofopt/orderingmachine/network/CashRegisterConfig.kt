package com.cofopt.orderingmachine.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** An immutable host/port pair read from one serialized preference value. */
internal data class CashRegisterConfigSnapshot(
    val host: String,
    val port: Int,
)

internal fun canonicalCashRegisterConfigSnapshot(
    host: String,
    port: Int,
): CashRegisterConfigSnapshot? {
    val canonicalHost = normalizeCashRegisterHost(host, port) ?: return null
    return CashRegisterConfigSnapshot(host = canonicalHost, port = port)
}

object CashRegisterConfig {
    private const val PREFS_NAME = "cash_register_config"
    private const val KEY_ENDPOINT_V2 = "endpoint_v2"
    private const val KEY_MIGRATED_ENDPOINT_V2 = "endpoint_migrated_v2"
    private const val LEGACY_KEY_HOST = "host"
    private const val LEGACY_KEY_PORT = "port"

    @Serializable
    private data class StoredEndpoint(
        val version: Int = 2,
        val host: String,
        val port: Int,
    )

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Returns one atomic endpoint snapshot. A corrupt v2 value fails closed and
     * is never replaced from legacy split keys.
     */
    internal fun endpoint(context: OrderingPlatformContext): CashRegisterConfigSnapshot? {
        val raw = OrderingPlatformPrefs.getString(context, PREFS_NAME, KEY_ENDPOINT_V2, "")
        if (raw.isNotBlank()) return decodeEndpoint(raw)

        val alreadyMigratedRaw = OrderingPlatformPrefs.getString(
            context,
            PREFS_NAME,
            KEY_MIGRATED_ENDPOINT_V2,
            "",
        )
        if (alreadyMigratedRaw.isNotBlank()) return decodeEndpoint(alreadyMigratedRaw)

        // One-time migration for installations that predate the atomic value.
        val legacyHost = OrderingPlatformPrefs.getString(
            context,
            PREFS_NAME,
            LEGACY_KEY_HOST,
            "",
        )
        if (legacyHost.isBlank()) return null
        val legacyPort = OrderingPlatformPrefs.getInt(
            context,
            PREFS_NAME,
            LEGACY_KEY_PORT,
            8080,
        )
        val migrated = canonicalCashRegisterConfigSnapshot(legacyHost, legacyPort) ?: return null

        // Prefer a concurrent explicit save that completed while the legacy pair was read.
        val concurrentRaw = OrderingPlatformPrefs.getString(context, PREFS_NAME, KEY_ENDPOINT_V2, "")
        if (concurrentRaw.isNotBlank()) return decodeEndpoint(concurrentRaw)

        val migratedRaw = encodeEndpoint(migrated)
        // Migration and explicit saves use separate complete-pair keys. An
        // explicit save always wins, so a racing migration can never overwrite
        // the user's new endpoint.
        OrderingPlatformPrefs.putStringDurable(
            context,
            PREFS_NAME,
            KEY_MIGRATED_ENDPOINT_V2,
            migratedRaw,
        )
        val savedAfterMigration = OrderingPlatformPrefs.getString(
            context,
            PREFS_NAME,
            KEY_ENDPOINT_V2,
            "",
        )
        if (savedAfterMigration.isNotBlank()) return decodeEndpoint(savedAfterMigration)
        return OrderingPlatformPrefs.getString(context, PREFS_NAME, KEY_MIGRATED_ENDPOINT_V2, "")
            .takeIf { it.isNotBlank() }
            ?.let(::decodeEndpoint)
            ?: migrated
    }

    fun host(context: OrderingPlatformContext): String = endpoint(context)?.host.orEmpty()

    fun port(context: OrderingPlatformContext): Int = endpoint(context)?.port ?: 8080

    /** Atomically replaces the complete endpoint; invalid pairs are never stored. */
    fun save(context: OrderingPlatformContext, host: String, port: Int): Boolean {
        val endpoint = canonicalCashRegisterConfigSnapshot(host, port) ?: return false
        val raw = encodeEndpoint(endpoint)
        val reportedDurable = OrderingPlatformPrefs.putStringDurable(
            context,
            PREFS_NAME,
            KEY_ENDPOINT_V2,
            raw,
        )
        return reportedDurable &&
            OrderingPlatformPrefs.getString(context, PREFS_NAME, KEY_ENDPOINT_V2, "") == raw
    }

    private fun encodeEndpoint(endpoint: CashRegisterConfigSnapshot): String = json.encodeToString(
        StoredEndpoint(host = endpoint.host, port = endpoint.port),
    )

    private fun decodeEndpoint(raw: String): CashRegisterConfigSnapshot? {
        val stored = runCatching { json.decodeFromString<StoredEndpoint>(raw) }
            .getOrNull()
            ?.takeIf { it.version == 2 }
            ?: return null
        return canonicalCashRegisterConfigSnapshot(stored.host, stored.port)
    }
}
