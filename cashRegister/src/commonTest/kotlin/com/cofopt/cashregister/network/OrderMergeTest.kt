package com.cofopt.cashregister.network

import kotlin.test.Test
import kotlin.test.assertEquals

class OrderMergeTest {
    @Test
    fun primarySnapshotWinsAndDuplicateIdsAreRemoved() {
        val live = order(id = "same", status = "PAID", createdAtMillis = 20)
        val duplicateLive = order(id = "same", status = "UNPAID", createdAtMillis = 10)
        val persisted = order(id = "same", status = "UNPAID", createdAtMillis = 5)

        val merged = mergeOrdersByIdPreferFirst(
            primary = listOf(live, duplicateLive),
            secondary = listOf(persisted),
        )

        assertEquals(listOf(live), merged)
    }

    @Test
    fun resultIsSortedNewestFirst() {
        val oldest = order(id = "old", createdAtMillis = 100)
        val newest = order(id = "new", createdAtMillis = 3)
        val middle = order(id = "middle", createdAtMillis = 2)
            .copy(acceptedAtMillis = 200)

        val merged = mergeOrdersByIdPreferFirst(
            primary = listOf(oldest),
            secondary = listOf(newest, middle),
        )

        assertEquals(listOf("middle", "old", "new"), merged.map { it.orderId })
    }

    @Test
    fun retryAllowsServerManagedStateChangesButRejectsPayloadCollisions() {
        val stored = order(id = "same", status = "COMPLETED", createdAtMillis = 20)
            .copy(callNumber = 7, paymentStatus = "PAID")
        val retry = order(id = "same", status = "PAID", createdAtMillis = 20)
            .copy(callNumber = 7, paymentStatus = "PAID")

        assertEquals(
            true,
            isSameOrderSubmission(stored, retry, retryHadExplicitCallNumber = true),
        )
        assertEquals(
            false,
            isSameOrderSubmission(
                stored,
                retry.copy(total = 99.0),
                retryHadExplicitCallNumber = true,
            ),
        )
        assertEquals(
            true,
            isSameOrderSubmission(
                stored,
                retry.copy(callNumber = 999),
                retryHadExplicitCallNumber = false,
            ),
        )
    }

    @Test
    fun storedFingerprintSurvivesLocalPaymentMethodChanges() {
        val stored = order(id = "same", status = "PAID", createdAtMillis = 20)
            .copy(paymentMethod = "CARD", submissionFingerprint = "original-request")
        val originalRetry = order(id = "same", status = "UNPAID", createdAtMillis = 20)
            .copy(paymentMethod = "CASH", submissionFingerprint = "original-request")

        assertEquals(
            true,
            isSameOrderSubmission(stored, originalRetry, retryHadExplicitCallNumber = false),
        )
        assertEquals(
            false,
            isSameOrderSubmission(
                stored,
                originalRetry.copy(submissionFingerprint = "different-request"),
                retryHadExplicitCallNumber = false,
            ),
        )
    }

    @Test
    fun receiptTimeOverridesBrokenDeviceClockForStorage() {
        val order = order(id = "clock-skew", createdAtMillis = 1)
            .copy(acceptedAtMillis = 1234)

        assertEquals(1234, order.storageTimestampMillis())
        assertEquals(1, order.copy(acceptedAtMillis = null).storageTimestampMillis())
    }

    @Test
    fun newKioskOrdersCannotBypassServerCallNumberAllocation() {
        assertEquals(
            "kiosk_call_number_must_be_unassigned",
            newOrderProtocolValidationError("KIOSK", "CARD", "PAID", callNumber = 12),
        )
        assertEquals(
            "kiosk_payment_method_invalid",
            newOrderProtocolValidationError("KIOSK", "TEST", "PAID", callNumber = null),
        )
        assertEquals(
            "kiosk_card_status_invalid",
            newOrderProtocolValidationError("KIOSK", "CARD", "UNPAID", callNumber = null),
        )
        assertEquals(
            null,
            newOrderProtocolValidationError("KIOSK", "CASH", "UNPAID", callNumber = null),
        )
    }

    private fun order(
        id: String,
        status: String = "UNPAID",
        createdAtMillis: Long,
    ): OrderPayload = OrderPayload(
        orderId = id,
        createdAtMillis = createdAtMillis,
        dineIn = true,
        paymentMethod = "CASH",
        status = status,
        total = 1.0,
        items = listOf(
            OrderItemPayload(
                menuItemId = "item",
                nameEn = "Item",
                nameZh = "",
                nameNl = "",
                quantity = 1,
                unitPrice = 1.0,
            )
        ),
    )
}
