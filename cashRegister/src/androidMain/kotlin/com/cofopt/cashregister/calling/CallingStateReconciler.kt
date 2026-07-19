package com.cofopt.cashregister.calling

import com.cofopt.shared.network.OrderPayload

internal data class CallingReconcileState(
    val preparing: List<Int>,
    val ready: List<Int>,
    val reserved: List<Int>,
    val orderBackedNumbers: Set<Int>,
    /** Order-backed numbers deliberately hidden by an operator clear action. */
    val suppressedNumbers: Set<Int> = emptySet(),
)

/**
 * Makes the order store authoritative only for numbers known to have originated from an order.
 * Untracked manual numbers are kept, while an order-backed number whose order never reached the
 * order store is removed as an interrupted two-store commit.
 */
internal fun reconcileCallingState(
    current: CallingReconcileState,
    orders: List<OrderPayload>,
): CallingReconcileState {
    val ready = LinkedHashSet(current.ready)
    val preparing = LinkedHashSet(current.preparing.filterNot(ready::contains))
    val reserved = LinkedHashSet(
        current.reserved.filterNot { it in ready || it in preparing }
    )

    fun activeNumbers(): Set<Int> = ready + preparing + reserved
    fun clear(number: Int) {
        ready.remove(number)
        preparing.remove(number)
        reserved.remove(number)
    }

    val orderBacked = LinkedHashSet(current.orderBackedNumbers)
    orderBacked.retainAll(activeNumbers())
    val suppressed = LinkedHashSet(current.suppressedNumbers)

    val latestOrderByNumber = orders
        .asSequence()
        .filter { it.callNumber != null }
        .filter { it.source.trim().uppercase() in ORDER_OWNING_SOURCES }
        .sortedWith(
            compareBy<OrderPayload> { it.acceptedAtMillis ?: it.createdAtMillis }
                .thenBy { it.createdAtMillis }
                .thenBy { it.orderId }
        )
        .associateBy { requireNotNull(it.callNumber) }

    // Once the owning order disappears there is nothing left to suppress, and a future order may
    // safely reuse the same call number.
    suppressed.retainAll(latestOrderByNumber.keys)

    // These entries can only be created when the calling snapshot landed but its order snapshot
    // did not. Manual entries have no ownership marker and therefore survive this cleanup.
    (orderBacked - latestOrderByNumber.keys).forEach { orphanedNumber ->
        clear(orphanedNumber)
        orderBacked.remove(orphanedNumber)
    }

    latestOrderByNumber.forEach { (number, order) ->
        when (order.status.trim().uppercase()) {
            "COMPLETED", "CANCELLED", "CANCELED", "REFUNDED" -> {
                if (number in orderBacked) clear(number)
                orderBacked.remove(number)
                suppressed.remove(number)
            }

            "READY" -> {
                if (number in suppressed) {
                    clear(number)
                    orderBacked.remove(number)
                } else {
                    clear(number)
                    ready.add(number)
                    orderBacked.add(number)
                }
            }

            "PAID", "PREPARING" -> {
                if (number in suppressed) {
                    clear(number)
                    orderBacked.remove(number)
                } else {
                    clear(number)
                    preparing.add(number)
                    orderBacked.add(number)
                }
            }

            "UNPAID", "PENDING_PAYMENT" -> {
                if (number in suppressed) {
                    clear(number)
                    orderBacked.remove(number)
                } else {
                    clear(number)
                    reserved.add(number)
                    orderBacked.add(number)
                }
            }

            else -> {
                // A future order status must not erase a number we do not yet understand.
                if (number in activeNumbers()) orderBacked.add(number)
            }
        }
    }

    orderBacked.retainAll(activeNumbers())
    return CallingReconcileState(
        preparing = preparing.toList(),
        ready = ready.toList(),
        reserved = reserved.toList(),
        orderBackedNumbers = orderBacked,
        suppressedNumbers = suppressed,
    )
}

private val ORDER_OWNING_SOURCES = setOf("KIOSK", "CHECKOUT")
