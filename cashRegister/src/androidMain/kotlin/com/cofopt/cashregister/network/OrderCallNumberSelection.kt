package com.cofopt.cashregister.network

import com.cofopt.shared.network.OrderPayload

internal fun selectActiveOrderForCallNumber(
    orders: List<OrderPayload>,
    callNumber: Int,
): OrderPayload? {
    return orders
        .asSequence()
        .filter { it.callNumber == callNumber }
        .filterNot { it.status.trim().uppercase() in TERMINAL_ORDER_STATUSES }
        .maxWithOrNull(
            compareBy<OrderPayload> { it.acceptedAtMillis ?: it.createdAtMillis }
                .thenBy { it.createdAtMillis }
                .thenBy { it.orderId }
        )
}

/**
 * Returns calling numbers that lose their last active order owner after an order deletion.
 * This also repairs pre-migration calling snapshots that do not yet carry ownership markers.
 */
internal fun orderBackedCallNumbersToRelease(
    removedOrders: List<OrderPayload>,
    remainingOrders: List<OrderPayload>,
): Set<Int> {
    val remainingOwnedNumbers = remainingOrders
        .asSequence()
        .filter(OrderPayload::ownsActiveCallingNumber)
        .mapNotNull(OrderPayload::callNumber)
        .toHashSet()
    return removedOrders
        .asSequence()
        .filter(OrderPayload::ownsActiveCallingNumber)
        .mapNotNull(OrderPayload::callNumber)
        .filterNot(remainingOwnedNumbers::contains)
        .toSet()
}

private fun OrderPayload.ownsActiveCallingNumber(): Boolean {
    return source.trim().uppercase() in ORDER_OWNING_SOURCES &&
        status.trim().uppercase() !in TERMINAL_ORDER_STATUSES
}

private val TERMINAL_ORDER_STATUSES = setOf(
    "COMPLETED",
    "CANCELLED",
    "CANCELED",
    "REFUNDED",
)

private val ORDER_OWNING_SOURCES = setOf("KIOSK", "CHECKOUT")
