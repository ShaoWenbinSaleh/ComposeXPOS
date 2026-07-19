package com.cofopt.orderingmachine.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CashRegisterOutboxSafetyTest {
    @Test
    fun ambiguousOrderKeepsItsOriginalEndpointWhenConfigurationChanges() {
        val order = order("stable")
        val original = CashRegisterOrderTarget("cash-a.local", 8080)
        val changed = CashRegisterOrderTarget("cash-b.local", 9090)
        val held = stageCashRegisterOutboxEntry(emptyList(), order, original, deliverable = false)!!

        val released = stageCashRegisterOutboxEntry(held.entries, order, changed, deliverable = true)!!

        assertEquals(original, released.staged.target)
        assertTrue(released.staged.deliverable)
    }

    @Test
    fun sameOrderIdWithDifferentPayloadIsRejected() {
        val original = order("same")
        val target = CashRegisterOrderTarget("cash.local", 8080)
        val first = stageCashRegisterOutboxEntry(emptyList(), original, target, deliverable = true)!!

        assertNull(
            stageCashRegisterOutboxEntry(
                first.entries,
                original.copy(total = 99.0),
                target,
                deliverable = true,
            )
        )
    }

    @Test
    fun blockedOrderCannotBeSilentlyUnblockedByBackgroundStage() {
        val order = order("blocked")
        val target = CashRegisterOrderTarget("cash.local", 8080)
        val blocked = CashRegisterOutboxEntry(order, target, deliverable = false, blockedReason = "invalid")

        val result = stageCashRegisterOutboxEntry(listOf(blocked), order, target, deliverable = true)!!

        assertFalse(result.staged.deliverable)
        assertEquals("invalid", result.staged.blockedReason)
    }

    @Test
    fun orphanedPrePaymentReservationIsDiscardedButPaidCommitIsReleased() {
        val target = CashRegisterOrderTarget("cash.local", 8080)
        val prepared = CashRegisterOutboxEntry(
            order = order("prepared"),
            target = target,
            deliverable = false,
            paymentPhase = CashRegisterPaymentPhase.PREPARED,
        )
        val paid = CashRegisterOutboxEntry(
            order = order("paid"),
            target = target,
            deliverable = false,
            paymentPhase = CashRegisterPaymentPhase.PAID_COMMIT,
        )

        val recovered = reconcileOrphanedPaymentCommits(listOf(prepared, paid), protectedOrderId = null)

        assertEquals(listOf("paid"), recovered.map { it.order.orderId })
        assertTrue(recovered.single().deliverable)
        assertEquals(CashRegisterPaymentPhase.NONE, recovered.single().paymentPhase)
    }

    @Test
    fun activeJournalProtectsItsPreparedReservation() {
        val prepared = CashRegisterOutboxEntry(
            order = order("active"),
            target = CashRegisterOrderTarget("cash.local", 8080),
            deliverable = false,
            paymentPhase = CashRegisterPaymentPhase.PREPARED,
        )

        assertEquals(
            listOf(prepared),
            reconcileOrphanedPaymentCommits(listOf(prepared), protectedOrderId = "active"),
        )
    }

    @Test
    fun ordinarySubmissionCannotReleaseOrSendPreparedOrPaidCardOrder() {
        val target = CashRegisterOrderTarget("cash.local", 8080)
        for (phase in listOf(CashRegisterPaymentPhase.PREPARED, CashRegisterPaymentPhase.PAID_COMMIT)) {
            val held = CashRegisterOutboxEntry(
                order = order("held-$phase"),
                target = target,
                deliverable = false,
                paymentPhase = phase,
            )

            val staged = stageCashRegisterOutboxEntry(
                current = listOf(held),
                order = held.order,
                requestedTarget = CashRegisterOrderTarget("other.local", 9090),
                deliverable = true,
            )!!

            assertEquals(held, staged.staged)
            assertEquals("order_reserved_for_card_payment", normalCashRegisterSubmissionBlockReason(held, held.order))
        }
    }

    @Test
    fun retryBatchRoundRobinsEndpointsAndNeverIncludesPaymentHolds() {
        val offline = CashRegisterOrderTarget("offline.local", 8080)
        val healthy = CashRegisterOrderTarget("healthy.local", 8080)
        val entries = buildList {
            repeat(12) { index ->
                add(deliverable("offline-$index", offline, createdAt = index.toLong()))
            }
            add(deliverable("healthy", healthy, createdAt = 100L))
            add(
                deliverable("prepared", healthy, createdAt = 101L).copy(
                    paymentPhase = CashRegisterPaymentPhase.PREPARED,
                ),
            )
            add(
                deliverable("paid", healthy, createdAt = 102L).copy(
                    paymentPhase = CashRegisterPaymentPhase.PAID_COMMIT,
                ),
            )
        }

        val batch = selectCashRegisterRetryBatch(entries, nowMillis = 1_000L, maxOrders = 10)

        assertEquals(10, batch.size)
        assertTrue(batch.any { it.order.orderId == "healthy" })
        assertFalse(batch.any { it.paymentPhase != CashRegisterPaymentPhase.NONE })
    }

    @Test
    fun claimedOfflinePrefixRotatesBehindUntouchedOrdersPersistently() {
        val target = CashRegisterOrderTarget("offline.local", 8080)
        val original = List(15) { index ->
            deliverable("order-$index", target, createdAt = index.toLong())
        }
        val firstBatch = selectCashRegisterRetryBatch(original, nowMillis = 10_000L, maxOrders = 10)
        val claimedIds = firstBatch.map { it.order.orderId }.toSet()
        val afterClaim = original.map { entry ->
            if (entry.order.orderId in claimedIds) claimCashRegisterRetryEntry(entry, 10_000L) else entry
        }

        val immediateSecondBatch = selectCashRegisterRetryBatch(afterClaim, nowMillis = 10_000L, maxOrders = 10)

        assertEquals(5, immediateSecondBatch.size)
        assertTrue(immediateSecondBatch.none { it.order.orderId in claimedIds })
        assertTrue(afterClaim.filter { it.order.orderId in claimedIds }.all { it.nextAttemptAtMillis > 10_000L })
    }

    @Test
    fun failedDurableWriteWithExactReadBackIsUncertainNotRejected() {
        assertEquals(
            CashRegisterStorageWriteResult.UNCERTAIN,
            classifyCashRegisterStorageWrite(reportedDurable = false, exactReadBack = true),
        )
        assertEquals(
            CashRegisterStorageWriteResult.FAILED,
            classifyCashRegisterStorageWrite(reportedDurable = false, exactReadBack = false),
        )
    }

    @Test
    fun blockedUnpaidOrderIsNotMisreportedAsDurablePaidOrder() {
        val target = CashRegisterOrderTarget("cash.local", 8080)
        val unpaidBlocked = CashRegisterOutboxEntry(
            order = order("unpaid").copy(paymentStatus = "UNPAID", status = "UNPAID"),
            target = target,
            deliverable = false,
            blockedReason = "invalid",
        )
        val paidBlocked = unpaidBlocked.copy(
            order = order("paid").copy(paymentStatus = "PAID", status = "PAID"),
        )

        assertFalse(isPaidCashRegisterOutboxEntry(unpaidBlocked))
        assertTrue(isPaidCashRegisterOutboxEntry(paidBlocked))
    }

    @Test
    fun retryMetadataBackoffIsMonotonic() {
        val entry = deliverable(
            id = "retry",
            target = CashRegisterOrderTarget("cash.local", 8080),
            createdAt = 1L,
        )
        val first = claimCashRegisterRetryEntry(entry, nowMillis = 1_000L)
        val second = claimCashRegisterRetryEntry(first, nowMillis = first.nextAttemptAtMillis)

        assertEquals(1, first.retryAttemptCount)
        assertEquals(2, second.retryAttemptCount)
        assertTrue(second.nextAttemptAtMillis - first.nextAttemptAtMillis > first.nextAttemptAtMillis - 1_000L)
    }

    private fun deliverable(
        id: String,
        target: CashRegisterOrderTarget,
        createdAt: Long,
    ) = CashRegisterOutboxEntry(
        order = order(id).copy(createdAtMillis = createdAt),
        target = target,
        deliverable = true,
    )

    private fun order(id: String) = CashRegisterOrderPayload(
        orderId = id,
        createdAtMillis = 1L,
        source = "KIOSK",
        deviceName = "test",
        dineIn = false,
        paymentMethod = "CASH",
        paymentStatus = "UNPAID",
        total = 1.0,
        items = emptyList(),
    )
}
