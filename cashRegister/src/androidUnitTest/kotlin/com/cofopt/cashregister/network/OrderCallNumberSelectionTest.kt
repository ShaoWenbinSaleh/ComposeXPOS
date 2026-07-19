package com.cofopt.cashregister.network

import com.cofopt.shared.network.OrderPayload
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OrderCallNumberSelectionTest {
    @Test
    fun selectsLatestActiveOrderWithoutResurrectingHistoricalOrder() {
        val selected = selectActiveOrderForCallNumber(
            orders = listOf(
                order(id = "old", number = 9, status = "COMPLETED", acceptedAt = 100),
                order(id = "current", number = 9, status = "PAID", acceptedAt = 200),
                order(id = "other", number = 8, status = "PAID", acceptedAt = 300),
            ),
            callNumber = 9,
        )

        assertEquals("current", selected?.orderId)
    }

    @Test
    fun doesNotSelectTerminalOrderWhenNumberHasNoCurrentOwner() {
        val selected = selectActiveOrderForCallNumber(
            orders = listOf(order(id = "done", number = 4, status = "COMPLETED", acceptedAt = 100)),
            callNumber = 4,
        )

        assertNull(selected)
    }

    @Test
    fun selectsNewestWhenRecoveryLeftMultipleActiveCandidates() {
        val selected = selectActiveOrderForCallNumber(
            orders = listOf(
                order(id = "first", number = 3, status = "READY", acceptedAt = 100),
                order(id = "second", number = 3, status = "PAID", acceptedAt = 200),
            ),
            callNumber = 3,
        )

        assertEquals("second", selected?.orderId)
    }

    @Test
    fun releasesOnlyNumbersThatLoseTheirLastActiveOrderOwner() {
        val removed = listOf(
            order(id = "removed-only-owner", number = 3, status = "PAID", acceptedAt = 10),
            order(id = "removed-shared", number = 4, status = "READY", acceptedAt = 20),
            order(id = "removed-terminal", number = 5, status = "COMPLETED", acceptedAt = 30),
        )
        val remaining = listOf(
            order(id = "remaining-shared", number = 4, status = "PREPARING", acceptedAt = 40),
        )

        assertEquals(
            setOf(3),
            orderBackedCallNumbersToRelease(removed, remaining),
        )
    }

    private fun order(
        id: String,
        number: Int,
        status: String,
        acceptedAt: Long,
    ) = OrderPayload(
        orderId = id,
        createdAtMillis = acceptedAt,
        source = "KIOSK",
        callNumber = number,
        dineIn = false,
        paymentMethod = "CARD",
        status = status,
        total = 1.0,
        items = emptyList(),
        acceptedAtMillis = acceptedAt,
    )
}
