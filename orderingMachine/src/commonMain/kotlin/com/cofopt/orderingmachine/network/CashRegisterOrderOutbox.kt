package com.cofopt.orderingmachine.network

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** The exact CashRegister endpoint that accepted (or may have accepted) an order. */
@Serializable
internal data class CashRegisterOrderTarget(
    val host: String,
    val port: Int,
)

/**
 * A durable order intent. Held entries belong to an in-flight card payment and
 * must not be delivered until the payment has been confirmed.
 */
@Serializable
internal data class CashRegisterOutboxEntry(
    val order: CashRegisterOrderPayload,
    val target: CashRegisterOrderTarget,
    val deliverable: Boolean,
    val blockedReason: String? = null,
    /** Card lifecycle; non-NONE entries are never handled by the global retry loop. */
    val paymentPhase: CashRegisterPaymentPhase = CashRegisterPaymentPhase.NONE,
    /** Persisted retry rotation prevents an offline prefix of the queue starving later orders. */
    val retryAttemptCount: Int = 0,
    val nextAttemptAtMillis: Long = 0L,
)

@Serializable
internal enum class CashRegisterPaymentPhase {
    NONE,
    PREPARED,
    PAID_COMMIT,
}

internal data class CashRegisterOutboxStage(
    val entries: List<CashRegisterOutboxEntry>,
    val staged: CashRegisterOutboxEntry,
)

internal enum class CashRegisterOutboxDurability {
    CONFIRMED,
    UNCERTAIN,
}

internal data class CashRegisterOutboxStageWrite(
    val entry: CashRegisterOutboxEntry,
    val durability: CashRegisterOutboxDurability,
)

internal enum class CashRegisterStorageWriteResult {
    CONFIRMED,
    UNCERTAIN,
    FAILED,
}

internal fun classifyCashRegisterStorageWrite(
    reportedDurable: Boolean,
    exactReadBack: Boolean,
): CashRegisterStorageWriteResult = when {
    reportedDurable && exactReadBack -> CashRegisterStorageWriteResult.CONFIRMED
    exactReadBack -> CashRegisterStorageWriteResult.UNCERTAIN
    else -> CashRegisterStorageWriteResult.FAILED
}

/** Pure merge logic kept separate so endpoint/identity invariants are testable. */
internal fun stageCashRegisterOutboxEntry(
    current: List<CashRegisterOutboxEntry>,
    order: CashRegisterOrderPayload,
    requestedTarget: CashRegisterOrderTarget,
    deliverable: Boolean,
    paymentPhase: CashRegisterPaymentPhase = CashRegisterPaymentPhase.NONE,
    maxEntries: Int = 100,
): CashRegisterOutboxStage? {
    val existingIndex = current.indexOfFirst { it.order.orderId == order.orderId }
    if (existingIndex >= 0) {
        val existing = current[existingIndex]
        if (existing.order != order) return null
        if (existing.blockedReason != null) return CashRegisterOutboxStage(current, existing)

        // requestedTarget is deliberately ignored for an existing identity.
        // An ordinary submission must never release a card-payment hold.
        val ordinarySubmitAgainstPaymentHold =
            paymentPhase == CashRegisterPaymentPhase.NONE &&
                existing.paymentPhase != CashRegisterPaymentPhase.NONE
        val staged = existing.copy(
            deliverable = if (ordinarySubmitAgainstPaymentHold) {
                existing.deliverable
            } else {
                existing.deliverable || deliverable
            },
            paymentPhase = maxPaymentPhase(existing.paymentPhase, paymentPhase),
        )
        if (staged == existing) return CashRegisterOutboxStage(current, existing)
        val updated = current.toMutableList().also { it[existingIndex] = staged }
        return CashRegisterOutboxStage(updated, staged)
    }

    if (current.size >= maxEntries.coerceAtLeast(0)) return null
    val staged = CashRegisterOutboxEntry(
        order = order,
        target = requestedTarget,
        deliverable = deliverable,
        paymentPhase = paymentPhase,
    )
    return CashRegisterOutboxStage(current + staged, staged)
}

private fun maxPaymentPhase(
    first: CashRegisterPaymentPhase,
    second: CashRegisterPaymentPhase,
): CashRegisterPaymentPhase {
    return if (first.ordinal >= second.ordinal) first else second
}

internal fun reconcileOrphanedPaymentCommits(
    current: List<CashRegisterOutboxEntry>,
    protectedOrderId: String?,
): List<CashRegisterOutboxEntry> {
    return current.mapNotNull { entry ->
        if (entry.order.orderId == protectedOrderId || entry.blockedReason != null) {
            return@mapNotNull entry
        }
        when (entry.paymentPhase) {
            CashRegisterPaymentPhase.NONE -> entry
            // Without the journal, startTransaction could not have been called.
            CashRegisterPaymentPhase.PREPARED -> null
            // PAID_COMMIT is set only after a confirmed paid status.
            CashRegisterPaymentPhase.PAID_COMMIT -> entry.copy(
                deliverable = true,
                paymentPhase = CashRegisterPaymentPhase.NONE,
            )
        }
    }
}

internal fun isCashRegisterEntryRetryEligible(
    entry: CashRegisterOutboxEntry,
    nowMillis: Long,
): Boolean {
    return entry.deliverable &&
        entry.blockedReason == null &&
        entry.paymentPhase == CashRegisterPaymentPhase.NONE &&
        entry.nextAttemptAtMillis <= nowMillis
}

internal fun normalCashRegisterSubmissionBlockReason(
    existing: CashRegisterOutboxEntry,
    order: CashRegisterOrderPayload,
): String? {
    if (existing.order != order) return "order_identity_payload_conflict"
    if (existing.paymentPhase != CashRegisterPaymentPhase.NONE) {
        return "order_reserved_for_card_payment"
    }
    return null
}

internal fun isPaidCashRegisterOutboxEntry(entry: CashRegisterOutboxEntry): Boolean {
    return entry.order.paymentStatus.equals("PAID", ignoreCase = true) &&
        (entry.paymentPhase == CashRegisterPaymentPhase.PAID_COMMIT || entry.blockedReason != null)
}

/**
 * Selects oldest eligible entries round-robin by endpoint. This prevents a
 * dead CashRegister from occupying the whole batch when another endpoint has
 * deliverable orders.
 */
internal fun selectCashRegisterRetryBatch(
    entries: List<CashRegisterOutboxEntry>,
    nowMillis: Long,
    maxOrders: Int,
): List<CashRegisterOutboxEntry> {
    val limit = maxOrders.coerceAtLeast(0)
    if (limit == 0) return emptyList()

    val queues = linkedMapOf<CashRegisterOrderTarget, MutableList<CashRegisterOutboxEntry>>()
    entries.asSequence()
        .filter { isCashRegisterEntryRetryEligible(it, nowMillis) }
        .sortedWith(
            compareBy<CashRegisterOutboxEntry> { it.nextAttemptAtMillis }
                .thenBy { it.order.createdAtMillis }
                .thenBy { it.order.orderId },
        )
        .forEach { entry -> queues.getOrPut(entry.target) { mutableListOf() }.add(entry) }

    val result = mutableListOf<CashRegisterOutboxEntry>()
    while (result.size < limit) {
        var added = false
        for (queue in queues.values) {
            if (result.size >= limit) break
            if (queue.isNotEmpty()) {
                result += queue.removeAt(0)
                added = true
            }
        }
        if (!added) break
    }
    return result
}

internal fun claimCashRegisterRetryEntry(
    entry: CashRegisterOutboxEntry,
    nowMillis: Long,
    baseDelayMillis: Long = 1_000L,
    maxDelayMillis: Long = 60_000L,
): CashRegisterOutboxEntry {
    val nextAttemptCount = (entry.retryAttemptCount + 1).coerceAtMost(30)
    var retryDelay = baseDelayMillis.coerceAtLeast(1L).coerceAtMost(maxDelayMillis.coerceAtLeast(1L))
    repeat(entry.retryAttemptCount.coerceIn(0, 30)) {
        retryDelay = (retryDelay * 2L).coerceAtMost(maxDelayMillis.coerceAtLeast(1L))
    }
    val retryAt = if (nowMillis > Long.MAX_VALUE - retryDelay) {
        Long.MAX_VALUE
    } else {
        nowMillis + retryDelay
    }
    return entry.copy(
        retryAttemptCount = nextAttemptCount,
        nextAttemptAtMillis = retryAt,
    )
}

/**
 * Durable client-side intent log for orders whose server acknowledgement may
 * have been lost. Version 4 stores one order per key. Unlike a single JSON
 * array in localStorage, two browser tabs staging different orders cannot
 * overwrite and lose each other's entries.
 */
internal object CashRegisterOrderOutbox {
    private const val PREFS_NAME = "cash_register_order_outbox"
    private const val LEGACY_KEY_PENDING_V3 = "pending_orders_v3"
    private const val ENTRY_KEY_PREFIX_V4 = "entry_v4."
    private const val MIGRATION_MARKER_V4 = "migration_complete_v4"
    private const val MAX_PENDING_ORDERS = 100

    @Serializable
    private data class LegacyStoredOrders(
        val version: Int = 3,
        val entries: List<CashRegisterOutboxEntry> = emptyList(),
    )

    @Serializable
    private data class StoredEntry(
        val version: Int = 4,
        val entry: CashRegisterOutboxEntry,
    )

    private data class EntryRead(val entry: CashRegisterOutboxEntry?)

    private val lock = Mutex()
    private val uncertainStorageKeys = mutableSetOf<String>()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Creates an entry or promotes an existing entry while retaining its exact
     * original target. UNCERTAIN means the platform write reported failure but
     * the exact entry can still be read back; callers must return Pending and
     * must not start network/payment side effects.
     */
    suspend fun stage(
        context: OrderingPlatformContext,
        order: CashRegisterOrderPayload,
        requestedTarget: CashRegisterOrderTarget,
        deliverable: Boolean,
        paymentPhase: CashRegisterPaymentPhase = CashRegisterPaymentPhase.NONE,
    ): CashRegisterOutboxStageWrite? = lock.withLock {
        repeat(3) {
            val existingRead = readEntryUnlocked(context, order.orderId) ?: return@withLock null
            val existing = existingRead.entry
            if (existing == null) {
                val all = readAllUnlocked(context) ?: return@withLock null
                if (all.size >= MAX_PENDING_ORDERS) return@withLock null
            }
            val merge = stageCashRegisterOutboxEntry(
                current = listOfNotNull(existing),
                order = order,
                requestedTarget = requestedTarget,
                deliverable = deliverable,
                paymentPhase = paymentPhase,
                maxEntries = MAX_PENDING_ORDERS,
            ) ?: return@withLock null
            val staged = merge.staged
            val storageKey = entryKey(order.orderId)
            if (staged == existing && storageKey !in uncertainStorageKeys) {
                return@withLock CashRegisterOutboxStageWrite(
                    entry = staged,
                    durability = CashRegisterOutboxDurability.CONFIRMED,
                )
            }
            when (persistEntryUnlocked(context, staged)) {
                CashRegisterStorageWriteResult.CONFIRMED -> return@withLock CashRegisterOutboxStageWrite(
                    entry = staged,
                    durability = CashRegisterOutboxDurability.CONFIRMED,
                )

                CashRegisterStorageWriteResult.UNCERTAIN -> return@withLock CashRegisterOutboxStageWrite(
                    entry = staged,
                    durability = CashRegisterOutboxDurability.UNCERTAIN,
                )

                // A second browser tab may have updated this same identity.
                // Re-read and monotonically merge instead of overwriting it.
                CashRegisterStorageWriteResult.FAILED -> Unit
            }
        }
        null
    }

    suspend fun find(
        context: OrderingPlatformContext,
        orderId: String,
    ): CashRegisterOutboxEntry? = lock.withLock {
        readEntryUnlocked(context, orderId)?.entry
    }

    suspend fun isReadable(context: OrderingPlatformContext): Boolean = lock.withLock {
        readAllUnlocked(context) != null
    }

    suspend fun isOrderDurablyRecorded(
        context: OrderingPlatformContext,
        order: CashRegisterOrderPayload,
    ): Boolean = lock.withLock {
        val key = entryKey(order.orderId)
        if (key in uncertainStorageKeys) return@withLock false
        readEntryUnlocked(context, order.orderId)?.entry?.order == order
    }

    suspend fun isPaidOrderDurablyRecorded(
        context: OrderingPlatformContext,
        orderId: String,
    ): Boolean = lock.withLock {
        val key = entryKey(orderId)
        if (key in uncertainStorageKeys) return@withLock false
        val entry = readEntryUnlocked(context, orderId)?.entry ?: return@withLock false
        isPaidCashRegisterOutboxEntry(entry)
    }

    suspend fun markBlocked(
        context: OrderingPlatformContext,
        orderId: String,
        reason: String,
    ): Boolean = lock.withLock {
        val existing = readEntryUnlocked(context, orderId)?.entry ?: return@withLock false
        val blocked = existing.copy(deliverable = false, blockedReason = reason)
        val key = entryKey(orderId)
        (blocked == existing && key !in uncertainStorageKeys) ||
            persistEntryUnlocked(context, blocked) == CashRegisterStorageWriteResult.CONFIRMED
    }

    suspend fun releasePaymentCommit(
        context: OrderingPlatformContext,
        orderId: String,
    ): Boolean = lock.withLock {
        val existing = readEntryUnlocked(context, orderId)?.entry ?: return@withLock false
        if (
            existing.blockedReason != null ||
            existing.paymentPhase != CashRegisterPaymentPhase.PAID_COMMIT
        ) return@withLock false
        val released = existing.copy(
            deliverable = true,
            paymentPhase = CashRegisterPaymentPhase.NONE,
            retryAttemptCount = 0,
            nextAttemptAtMillis = 0L,
        )
        val key = entryKey(orderId)
        (released == existing && key !in uncertainStorageKeys) ||
            persistEntryUnlocked(context, released) == CashRegisterStorageWriteResult.CONFIRMED
    }

    suspend fun markPaymentPaid(
        context: OrderingPlatformContext,
        orderId: String,
    ): Boolean = lock.withLock {
        val existing = readEntryUnlocked(context, orderId)?.entry ?: return@withLock false
        if (existing.blockedReason != null) return@withLock false
        val paid = existing.copy(
            deliverable = false,
            paymentPhase = CashRegisterPaymentPhase.PAID_COMMIT,
        )
        val key = entryKey(orderId)
        (paid == existing && key !in uncertainStorageKeys) ||
            persistEntryUnlocked(context, paid) == CashRegisterStorageWriteResult.CONFIRMED
    }

    suspend fun releaseOrphanedPaymentCommits(
        context: OrderingPlatformContext,
        protectedOrderId: String?,
    ): Boolean = lock.withLock {
        val current = readAllUnlocked(context) ?: return@withLock false
        val expected = reconcileOrphanedPaymentCommits(current, protectedOrderId)
            .associateBy { it.order.orderId }
        for (entry in current) {
            val replacement = expected[entry.order.orderId]
            val succeeded = if (replacement == null) {
                removeEntryUnlocked(context, entry.order.orderId) == CashRegisterStorageWriteResult.CONFIRMED
            } else if (replacement != entry) {
                persistEntryUnlocked(context, replacement) == CashRegisterStorageWriteResult.CONFIRMED
            } else {
                val key = entryKey(entry.order.orderId)
                key !in uncertainStorageKeys ||
                    persistEntryUnlocked(context, entry) == CashRegisterStorageWriteResult.CONFIRMED
            }
            if (!succeeded) return@withLock false
        }
        true
    }

    suspend fun remove(context: OrderingPlatformContext, orderId: String): Boolean = lock.withLock {
        val existing = readEntryUnlocked(context, orderId) ?: return@withLock false
        if (existing.entry == null && entryKey(orderId) !in uncertainStorageKeys) return@withLock true
        removeEntryUnlocked(context, orderId) == CashRegisterStorageWriteResult.CONFIRMED
    }

    /** Removes only a never-released card-payment reservation. */
    suspend fun discardHeld(context: OrderingPlatformContext, orderId: String): Boolean = lock.withLock {
        val read = readEntryUnlocked(context, orderId) ?: return@withLock false
        val existing = read.entry
        if (existing == null) {
            if (entryKey(orderId) !in uncertainStorageKeys) return@withLock true
            return@withLock removeEntryUnlocked(context, orderId) == CashRegisterStorageWriteResult.CONFIRMED
        }
        if (
            existing.deliverable ||
            existing.blockedReason != null ||
            existing.paymentPhase != CashRegisterPaymentPhase.PREPARED
        ) return@withLock false
        removeEntryUnlocked(context, orderId) == CashRegisterStorageWriteResult.CONFIRMED
    }

    /**
     * Persists retry leases before returning entries to the network layer. An
     * immediate second run therefore rotates to untouched orders, even after a
     * crash or with multiple retry loops in one process.
     */
    suspend fun claimDeliverable(
        context: OrderingPlatformContext,
        maxOrders: Int,
        nowMillis: Long,
    ): List<CashRegisterOutboxEntry> = lock.withLock {
        val current = readAllUnlocked(context) ?: return@withLock emptyList()
        val selected = selectCashRegisterRetryBatch(current, nowMillis, maxOrders)
        val claimed = mutableListOf<CashRegisterOutboxEntry>()
        for (candidate in selected) {
            // Refresh the exact key so a claim made by another browser tab is
            // observed before this tab sends the same identity.
            val latest = readEntryUnlocked(context, candidate.order.orderId)?.entry ?: continue
            if (!isCashRegisterEntryRetryEligible(latest, nowMillis)) continue
            val leased = claimCashRegisterRetryEntry(latest, nowMillis)
            if (persistEntryUnlocked(context, leased) == CashRegisterStorageWriteResult.CONFIRMED) {
                claimed += leased
            }
        }
        claimed
    }

    /**
     * Corrupt storage is never interpreted as an empty queue. Returning null
     * makes every mutating operation fail closed, preserving the raw value for
     * diagnosis/recovery instead of silently overwriting pending orders.
     */
    private fun readAllUnlocked(context: OrderingPlatformContext): List<CashRegisterOutboxEntry>? {
        if (!ensureV4StorageUnlocked(context)) return null
        val stored = OrderingPlatformPrefs.getStringsWithPrefix(
            context,
            PREFS_NAME,
            ENTRY_KEY_PREFIX_V4,
        ) ?: return null
        val entries = mutableListOf<CashRegisterOutboxEntry>()
        val ids = mutableSetOf<String>()
        for ((key, raw) in stored) {
            val entry = decodeEntry(raw) ?: return null
            if (key != entryKey(entry.order.orderId) || !ids.add(entry.order.orderId)) return null
            entries += entry
        }
        return entries.sortedWith(
            compareBy<CashRegisterOutboxEntry> { it.order.createdAtMillis }
                .thenBy { it.order.orderId },
        )
    }

    /** null means corrupt/unavailable; EntryRead(null) means cleanly absent. */
    private fun readEntryUnlocked(
        context: OrderingPlatformContext,
        orderId: String,
    ): EntryRead? {
        if (!ensureV4StorageUnlocked(context)) return null
        val raw = OrderingPlatformPrefs.getString(context, PREFS_NAME, entryKey(orderId), "")
        if (raw.isBlank()) return EntryRead(null)
        val entry = decodeEntry(raw) ?: return null
        if (entry.order.orderId != orderId) return null
        return EntryRead(entry)
    }

    private fun decodeEntry(raw: String): CashRegisterOutboxEntry? {
        return runCatching { json.decodeFromString<StoredEntry>(raw) }
            .getOrNull()
            ?.takeIf { it.version == 4 }
            ?.entry
    }

    private fun persistEntryUnlocked(
        context: OrderingPlatformContext,
        entry: CashRegisterOutboxEntry,
    ): CashRegisterStorageWriteResult {
        val key = entryKey(entry.order.orderId)
        val raw = json.encodeToString(StoredEntry(entry = entry))
        val reportedDurable = OrderingPlatformPrefs.putStringDurable(context, PREFS_NAME, key, raw)
        val exactReadBack = OrderingPlatformPrefs.getString(context, PREFS_NAME, key, "") == raw
        return when (classifyCashRegisterStorageWrite(reportedDurable, exactReadBack)) {
            CashRegisterStorageWriteResult.CONFIRMED -> {
                uncertainStorageKeys.remove(key)
                CashRegisterStorageWriteResult.CONFIRMED
            }

            CashRegisterStorageWriteResult.UNCERTAIN -> {
                // SharedPreferences.commit()/NSUserDefaults.synchronize() may
                // report failure after updating process memory. Freeze this
                // exact identity, but do not perform any network side effect.
                uncertainStorageKeys.add(key)
                CashRegisterStorageWriteResult.UNCERTAIN
            }

            CashRegisterStorageWriteResult.FAILED -> CashRegisterStorageWriteResult.FAILED
        }
    }

    private fun removeEntryUnlocked(
        context: OrderingPlatformContext,
        orderId: String,
    ): CashRegisterStorageWriteResult {
        val key = entryKey(orderId)
        val reportedDurable = OrderingPlatformPrefs.removeStringDurable(context, PREFS_NAME, key)
        val absent = OrderingPlatformPrefs.getString(context, PREFS_NAME, key, "").isBlank()
        return when (classifyCashRegisterStorageWrite(reportedDurable, absent)) {
            CashRegisterStorageWriteResult.CONFIRMED -> {
                uncertainStorageKeys.remove(key)
                CashRegisterStorageWriteResult.CONFIRMED
            }

            CashRegisterStorageWriteResult.UNCERTAIN -> {
                uncertainStorageKeys.add(key)
                CashRegisterStorageWriteResult.UNCERTAIN
            }

            CashRegisterStorageWriteResult.FAILED -> CashRegisterStorageWriteResult.FAILED
        }
    }

    private fun ensureV4StorageUnlocked(context: OrderingPlatformContext): Boolean {
        val markerKey = MIGRATION_MARKER_V4
        val marker = OrderingPlatformPrefs.getString(context, PREFS_NAME, markerKey, "")
        if (marker == "true" && markerKey !in uncertainStorageKeys) return true
        if (marker == "true") {
            return persistRawUnlocked(context, markerKey, "true") == CashRegisterStorageWriteResult.CONFIRMED
        }

        val legacyRaw = OrderingPlatformPrefs.getString(
            context,
            PREFS_NAME,
            LEGACY_KEY_PENDING_V3,
            "",
        )
        val legacyEntries = if (legacyRaw.isBlank()) {
            emptyList()
        } else {
            runCatching { json.decodeFromString<LegacyStoredOrders>(legacyRaw) }
                .getOrNull()
                ?.takeIf { it.version == 3 }
                ?.entries
                ?: return false
        }
        if (
            legacyEntries.size > MAX_PENDING_ORDERS ||
            legacyEntries.map { it.order.orderId }.toSet().size != legacyEntries.size
        ) return false

        for (entry in legacyEntries) {
            if (persistEntryUnlocked(context, entry) != CashRegisterStorageWriteResult.CONFIRMED) return false
        }
        if (persistRawUnlocked(context, markerKey, "true") != CashRegisterStorageWriteResult.CONFIRMED) return false

        // The marker is committed first, so a failed cleanup can never cause a
        // deleted v4 order to be resurrected from the aggregate v3 value.
        OrderingPlatformPrefs.removeStringDurable(context, PREFS_NAME, LEGACY_KEY_PENDING_V3)
        return true
    }

    private fun persistRawUnlocked(
        context: OrderingPlatformContext,
        key: String,
        raw: String,
    ): CashRegisterStorageWriteResult {
        val reportedDurable = OrderingPlatformPrefs.putStringDurable(context, PREFS_NAME, key, raw)
        val exactReadBack = OrderingPlatformPrefs.getString(context, PREFS_NAME, key, "") == raw
        return when (classifyCashRegisterStorageWrite(reportedDurable, exactReadBack)) {
            CashRegisterStorageWriteResult.CONFIRMED -> {
                uncertainStorageKeys.remove(key)
                CashRegisterStorageWriteResult.CONFIRMED
            }

            CashRegisterStorageWriteResult.UNCERTAIN -> {
                uncertainStorageKeys.add(key)
                CashRegisterStorageWriteResult.UNCERTAIN
            }

            CashRegisterStorageWriteResult.FAILED -> CashRegisterStorageWriteResult.FAILED
        }
    }

    private fun entryKey(orderId: String): String = ENTRY_KEY_PREFIX_V4 + orderId
}
