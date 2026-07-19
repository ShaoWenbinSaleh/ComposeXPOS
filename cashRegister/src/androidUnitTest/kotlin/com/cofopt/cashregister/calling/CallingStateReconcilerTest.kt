package com.cofopt.cashregister.calling

import com.cofopt.shared.network.OrderPayload
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CallingStateReconcilerTest {
    @Test
    fun removesInterruptedOrderCommitButPreservesManualNumbers() {
        val result = reconcileCallingState(
            current = CallingReconcileState(
                preparing = listOf(10, 11),
                ready = listOf(12),
                reserved = emptyList(),
                orderBackedNumbers = setOf(10, 12),
            ),
            orders = emptyList(),
        )

        assertEquals(listOf(11), result.preparing)
        assertTrue(result.ready.isEmpty())
        assertTrue(result.orderBackedNumbers.isEmpty())
    }

    @Test
    fun derivesEveryOrderBackedBucketFromLatestOrderState() {
        val result = reconcileCallingState(
            current = CallingReconcileState(
                preparing = listOf(1),
                ready = listOf(2),
                reserved = listOf(3),
                orderBackedNumbers = setOf(1, 2, 3),
            ),
            orders = listOf(
                order(number = 1, status = "READY", acceptedAtMillis = 1),
                order(number = 2, status = "PREPARING", acceptedAtMillis = 2),
                order(number = 3, status = "UNPAID", acceptedAtMillis = 3),
                order(number = 4, status = "PAID", acceptedAtMillis = 4),
            ),
        )

        assertEquals(listOf(2, 4), result.preparing)
        assertEquals(listOf(1), result.ready)
        assertEquals(listOf(3), result.reserved)
        assertEquals(setOf(1, 2, 3, 4), result.orderBackedNumbers)
    }

    @Test
    fun latestOrderWinsWhenNumberWasRecycled() {
        val result = reconcileCallingState(
            current = CallingReconcileState(
                preparing = emptyList(),
                ready = listOf(7),
                reserved = emptyList(),
                orderBackedNumbers = setOf(7),
            ),
            orders = listOf(
                order(number = 7, status = "READY", acceptedAtMillis = 10, id = "old"),
                order(number = 7, status = "COMPLETED", acceptedAtMillis = 20, id = "new"),
            ),
        )

        assertFalse(7 in result.preparing)
        assertFalse(7 in result.ready)
        assertFalse(7 in result.reserved)
        assertFalse(7 in result.orderBackedNumbers)
    }

    @Test
    fun terminalOrderDoesNotEraseUntrackedManualNumber() {
        val result = reconcileCallingState(
            current = CallingReconcileState(
                preparing = emptyList(),
                ready = listOf(8),
                reserved = emptyList(),
                orderBackedNumbers = emptySet(),
            ),
            orders = listOf(order(number = 8, status = "COMPLETED", acceptedAtMillis = 1)),
        )

        assertEquals(listOf(8), result.ready)
        assertTrue(result.orderBackedNumbers.isEmpty())
    }

    @Test
    fun deliberatelyClearedOrderBackedNumberIsNotRestored() {
        val result = reconcileCallingState(
            current = CallingReconcileState(
                preparing = emptyList(),
                ready = emptyList(),
                reserved = emptyList(),
                orderBackedNumbers = emptySet(),
                suppressedNumbers = setOf(15),
            ),
            orders = listOf(order(number = 15, status = "PAID", acceptedAtMillis = 10)),
        )

        assertTrue(result.preparing.isEmpty())
        assertTrue(result.ready.isEmpty())
        assertTrue(result.reserved.isEmpty())
        assertTrue(result.orderBackedNumbers.isEmpty())
        assertEquals(setOf(15), result.suppressedNumbers)
    }

    @Test
    fun terminalOrRemovedOrderReleasesSuppressionForNumberReuse() {
        val terminal = reconcileCallingState(
            current = CallingReconcileState(
                preparing = emptyList(),
                ready = emptyList(),
                reserved = emptyList(),
                orderBackedNumbers = emptySet(),
                suppressedNumbers = setOf(16),
            ),
            orders = listOf(order(number = 16, status = "COMPLETED", acceptedAtMillis = 10)),
        )
        val removed = reconcileCallingState(
            current = terminal.copy(suppressedNumbers = setOf(17)),
            orders = emptyList(),
        )

        assertTrue(terminal.suppressedNumbers.isEmpty())
        assertTrue(removed.suppressedNumbers.isEmpty())
    }

    @Test
    fun oldButStillActiveOrderCanRestoreCallingState() {
        val result = reconcileCallingState(
            current = CallingReconcileState(
                preparing = emptyList(),
                ready = emptyList(),
                reserved = emptyList(),
                orderBackedNumbers = emptySet(),
            ),
            // Reconciliation deliberately has no "today" filter. MainActivity must pass archived
            // active orders as well as today's orders.
            orders = listOf(order(number = 18, status = "READY", acceptedAtMillis = 1)),
        )

        assertEquals(listOf(18), result.ready)
        assertEquals(setOf(18), result.orderBackedNumbers)
    }

    private fun order(
        number: Int,
        status: String,
        acceptedAtMillis: Long,
        id: String = "$number-$status",
    ) = OrderPayload(
        orderId = id,
        createdAtMillis = acceptedAtMillis,
        source = "KIOSK",
        callNumber = number,
        dineIn = false,
        paymentMethod = "CARD",
        status = status,
        total = 1.0,
        items = emptyList(),
        acceptedAtMillis = acceptedAtMillis,
    )
}
