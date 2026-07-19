package com.cofopt.cashregister.network

import kotlinx.serialization.Serializable

/**
 * Durable identity of a LAN order after it has been assigned a call number.
 *
 * This is deliberately independent from the operator-facing order lists: removing an order from
 * history must not make a delayed kiosk outbox retry look like a brand-new order.
 */
@Serializable
internal data class OrderIdempotencyLedgerEntry(
    val orderId: String,
    val payloadFingerprint: String,
    val originalCallNumber: Int? = null,
    val recordedAtMillis: Long,
)

internal sealed interface OrderIdempotencyMatch {
    data object Missing : OrderIdempotencyMatch
    data class Duplicate(val entry: OrderIdempotencyLedgerEntry) : OrderIdempotencyMatch
    data object Conflict : OrderIdempotencyMatch
}

internal sealed interface OrderIdempotencyRecordResult {
    data class Recorded(val entry: OrderIdempotencyLedgerEntry) : OrderIdempotencyRecordResult
    data class Duplicate(val entry: OrderIdempotencyLedgerEntry) : OrderIdempotencyRecordResult
    data object Conflict : OrderIdempotencyRecordResult
}

/**
 * Thread-safe in-memory index whose snapshot is persisted atomically with [OrdersRepository].
 *
 * Retention and capacity are both bounded. Expiration is based on the original server acceptance
 * time, not retry time, so an abusive retry cannot keep an identity forever.
 */
internal class OrderIdempotencyLedger(
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val retentionMillis: Long = DEFAULT_RETENTION_MILLIS,
) {
    private val entriesByOrderId = LinkedHashMap<String, OrderIdempotencyLedgerEntry>()

    init {
        require(maxEntries > 0) { "maxEntries must be positive" }
        require(retentionMillis > 0L) { "retentionMillis must be positive" }
    }

    /** Restores a process snapshot. Invalid semantic data fails closed instead of being skipped. */
    @Synchronized
    fun restore(
        persisted: List<OrderIdempotencyLedgerEntry>,
        nowMillis: Long,
    ): Boolean {
        val restored = LinkedHashMap<String, OrderIdempotencyLedgerEntry>()
        persisted.forEach { entry ->
            validateEntry(entry)
            if (restored.put(entry.orderId, entry) != null) {
                throw IllegalStateException("order_idempotency_ledger_duplicate_id")
            }
        }

        entriesByOrderId.clear()
        entriesByOrderId.putAll(restored)
        prune(nowMillis)
        return snapshot(nowMillis) != persisted
    }

    @Synchronized
    fun match(
        orderId: String,
        payloadFingerprint: String,
        nowMillis: Long,
    ): OrderIdempotencyMatch {
        validateLookup(orderId, payloadFingerprint)
        val entry = entriesByOrderId[orderId] ?: return OrderIdempotencyMatch.Missing
        if (isExpired(entry, nowMillis)) return OrderIdempotencyMatch.Missing
        return if (entry.payloadFingerprint == payloadFingerprint) {
            OrderIdempotencyMatch.Duplicate(entry)
        } else {
            OrderIdempotencyMatch.Conflict
        }
    }

    @Synchronized
    fun record(
        orderId: String,
        payloadFingerprint: String,
        originalCallNumber: Int?,
        recordedAtMillis: Long,
        nowMillis: Long,
    ): OrderIdempotencyRecordResult {
        val candidate = OrderIdempotencyLedgerEntry(
            orderId = orderId,
            payloadFingerprint = payloadFingerprint,
            originalCallNumber = originalCallNumber,
            recordedAtMillis = recordedAtMillis,
        )
        validateEntry(candidate)
        prune(nowMillis)

        val existing = entriesByOrderId[orderId]
        if (existing != null) {
            if (existing.payloadFingerprint != payloadFingerprint) {
                return OrderIdempotencyRecordResult.Conflict
            }
            if (existing.originalCallNumber != originalCallNumber) {
                throw IllegalStateException("order_idempotency_call_number_mismatch")
            }
            return OrderIdempotencyRecordResult.Duplicate(existing)
        }

        entriesByOrderId[orderId] = candidate
        trimToCapacity()
        return OrderIdempotencyRecordResult.Recorded(candidate)
    }

    @Synchronized
    fun snapshot(nowMillis: Long): List<OrderIdempotencyLedgerEntry> {
        prune(nowMillis)
        return entriesByOrderId.values
            .sortedWith(compareBy<OrderIdempotencyLedgerEntry> { it.recordedAtMillis }.thenBy { it.orderId })
    }

    @Synchronized
    fun size(nowMillis: Long): Int {
        prune(nowMillis)
        return entriesByOrderId.size
    }

    private fun prune(nowMillis: Long) {
        val cutoff = nowMillis - retentionMillis
        entriesByOrderId.entries.removeAll { (_, entry) -> entry.recordedAtMillis < cutoff }
        trimToCapacity()
    }

    private fun trimToCapacity() {
        if (entriesByOrderId.size <= maxEntries) return
        val keepIds = entriesByOrderId.values
            .sortedWith(
                compareByDescending<OrderIdempotencyLedgerEntry> { it.recordedAtMillis }
                    .thenByDescending { it.orderId }
            )
            .take(maxEntries)
            .mapTo(HashSet(maxEntries)) { it.orderId }
        entriesByOrderId.entries.removeAll { (orderId, _) -> orderId !in keepIds }
    }

    private fun isExpired(entry: OrderIdempotencyLedgerEntry, nowMillis: Long): Boolean {
        return entry.recordedAtMillis < nowMillis - retentionMillis
    }

    private fun validateLookup(orderId: String, payloadFingerprint: String) {
        if (orderId.isBlank() || orderId.length > MAX_ORDER_ID_LENGTH) {
            throw IllegalArgumentException("order_idempotency_order_id_invalid")
        }
        if (!payloadFingerprint.matches(SHA_256_HEX)) {
            throw IllegalArgumentException("order_idempotency_fingerprint_invalid")
        }
    }

    private fun validateEntry(entry: OrderIdempotencyLedgerEntry) {
        try {
            validateLookup(entry.orderId, entry.payloadFingerprint)
        } catch (error: IllegalArgumentException) {
            throw IllegalStateException(error.message ?: "order_idempotency_entry_invalid", error)
        }
        if (entry.originalCallNumber != null && entry.originalCallNumber <= 0) {
            throw IllegalStateException("order_idempotency_call_number_invalid")
        }
        if (entry.recordedAtMillis <= 0L) {
            throw IllegalStateException("order_idempotency_recorded_at_invalid")
        }
    }

    companion object {
        internal const val DEFAULT_MAX_ENTRIES = 20_000
        internal const val DEFAULT_RETENTION_MILLIS = 180L * 24L * 60L * 60L * 1_000L
        private const val MAX_ORDER_ID_LENGTH = 128
        private val SHA_256_HEX = Regex("^[0-9a-f]{64}$")
    }
}
