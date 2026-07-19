package com.cofopt.cashregister.network

/**
 * Merges order snapshots while treating [OrderPayload.orderId] as the stable identity.
 *
 * Entries from [primary] win over entries with the same id in [secondary]. The first
 * occurrence also wins when either input already contains duplicates. This makes the
 * helper suitable for merging live in-memory state over an older persisted snapshot.
 */
internal fun mergeOrdersByIdPreferFirst(
    primary: List<OrderPayload>,
    secondary: List<OrderPayload>,
): List<OrderPayload> {
    val byId = linkedMapOf<String, OrderPayload>()
    (primary.asSequence() + secondary.asSequence()).forEach { order ->
        if (order.orderId !in byId) {
            byId[order.orderId] = order
        }
    }
    return byId.values.sortedByDescending { it.storageTimestampMillis() }
}

/**
 * Cash-register history follows server receipt time for LAN orders. This prevents a kiosk with
 * a bad clock from making a successfully accepted order disappear into an old/future day.
 */
internal fun OrderPayload.storageTimestampMillis(): Long {
    return acceptedAtMillis?.takeIf { it > 0L } ?: createdAtMillis
}

/** Protocol rules that must hold before a new kiosk order consumes a call number. */
internal fun newOrderProtocolValidationError(
    source: String,
    paymentMethod: String,
    incomingStatus: String,
    callNumber: Int?,
): String? {
    if (source != "KIOSK") return null
    if (callNumber != null) return "kiosk_call_number_must_be_unassigned"
    if (paymentMethod !in setOf("CASH", "CARD")) return "kiosk_payment_method_invalid"
    if (paymentMethod == "CARD" && incomingStatus != "PAID") return "kiosk_card_status_invalid"
    if (paymentMethod == "CASH" && incomingStatus !in setOf("UNPAID", "PAID")) {
        return "kiosk_cash_status_invalid"
    }
    return null
}

/**
 * Compares the immutable portion of a retried submission with the stored order.
 * Status and the assigned call number may legitimately change after acceptance.
 */
internal fun isSameOrderSubmission(
    existing: OrderPayload,
    retry: OrderPayload,
    retryHadExplicitCallNumber: Boolean,
): Boolean {
    val storedFingerprint = existing.submissionFingerprint
    val retryFingerprint = retry.submissionFingerprint
    if (storedFingerprint != null && retryFingerprint != null) {
        return storedFingerprint == retryFingerprint
    }

    return existing.orderId == retry.orderId &&
        existing.createdAtMillis == retry.createdAtMillis &&
        existing.source == retry.source &&
        existing.deviceName == retry.deviceName &&
        existing.dineIn == retry.dineIn &&
        existing.total == retry.total &&
        existing.items == retry.items &&
        (!retryHadExplicitCallNumber || retry.callNumber == existing.callNumber)
}
