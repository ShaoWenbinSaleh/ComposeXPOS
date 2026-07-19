package com.cofopt.cashregister.network

import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class OrderIdempotencyLedgerTest {
    @Test
    fun replayAfterVisibleOrderDeletionAndProcessRestartReturnsOriginalNumber() {
        val acceptedAt = 1_000L
        val ledger = OrderIdempotencyLedger()
        val visibleOrderIds = mutableListOf("order-1")

        assertIs<OrderIdempotencyRecordResult.Recorded>(
            ledger.record(
                orderId = "order-1",
                payloadFingerprint = FINGERPRINT_A,
                originalCallNumber = 42,
                recordedAtMillis = acceptedAt,
                nowMillis = acceptedAt,
            )
        )

        // Staff deletion/clear owns only the visible collection; it must not erase this snapshot.
        visibleOrderIds.clear()
        val persistedLedger = ledger.snapshot(acceptedAt)
        assertTrue(visibleOrderIds.isEmpty())

        val afterRestart = OrderIdempotencyLedger()
        afterRestart.restore(persistedLedger, acceptedAt + 1)
        val match = assertIs<OrderIdempotencyMatch.Duplicate>(
            afterRestart.match("order-1", FINGERPRINT_A, acceptedAt + 1)
        )

        assertEquals(42, match.entry.originalCallNumber)
    }

    @Test
    fun reusedOrderIdWithDifferentPayloadIsConflict() {
        val ledger = ledgerWithOneEntry()

        assertIs<OrderIdempotencyMatch.Conflict>(
            ledger.match("order-1", FINGERPRINT_B, NOW)
        )
        assertIs<OrderIdempotencyRecordResult.Conflict>(
            ledger.record("order-1", FINGERPRINT_B, 42, NOW, NOW)
        )
    }

    @Test
    fun samePayloadCannotSilentlyChangeOriginalCallNumber() {
        val ledger = ledgerWithOneEntry()

        assertFailsWith<IllegalStateException> {
            ledger.record("order-1", FINGERPRINT_A, 43, NOW, NOW)
        }
    }

    @Test
    fun corruptPersistedEntriesFailClosedEvenWhenExpired() {
        val invalidEntries = listOf(
            entry(orderId = "", fingerprint = FINGERPRINT_A, recordedAt = 1),
            entry(fingerprint = "not-a-sha256", recordedAt = 1),
            entry(fingerprint = FINGERPRINT_A, callNumber = 0, recordedAt = 1),
            entry(fingerprint = FINGERPRINT_A, recordedAt = 0),
        )

        invalidEntries.forEach { invalid ->
            assertFailsWith<IllegalStateException> {
                OrderIdempotencyLedger(retentionMillis = 10).restore(
                    persisted = listOf(invalid),
                    nowMillis = NOW,
                )
            }
        }
    }

    @Test
    fun duplicateIdsInPersistedSnapshotFailClosed() {
        assertFailsWith<IllegalStateException> {
            OrderIdempotencyLedger().restore(
                persisted = listOf(
                    entry(fingerprint = FINGERPRINT_A),
                    entry(fingerprint = FINGERPRINT_A),
                ),
                nowMillis = NOW,
            )
        }
    }

    @Test
    fun capacityKeepsNewestIdentitiesDeterministically() {
        val ledger = OrderIdempotencyLedger(maxEntries = 2, retentionMillis = 10_000)
        ledger.record("old", FINGERPRINT_A, 1, 100, 100)
        ledger.record("middle", FINGERPRINT_B, 2, 200, 200)
        ledger.record("new", FINGERPRINT_C, 3, 300, 300)

        assertIs<OrderIdempotencyMatch.Missing>(ledger.match("old", FINGERPRINT_A, 300))
        assertIs<OrderIdempotencyMatch.Duplicate>(ledger.match("middle", FINGERPRINT_B, 300))
        assertIs<OrderIdempotencyMatch.Duplicate>(ledger.match("new", FINGERPRINT_C, 300))
        assertEquals(2, ledger.size(300))
    }

    @Test
    fun retentionUsesOriginalAcceptanceTimeAndDoesNotExtendOnRetry() {
        val ledger = OrderIdempotencyLedger(retentionMillis = 100)
        ledger.restore(
            persisted = listOf(
                entry(orderId = "expired", fingerprint = FINGERPRINT_A, recordedAt = 899),
                entry(orderId = "fresh", fingerprint = FINGERPRINT_B, recordedAt = 900),
            ),
            nowMillis = 1_000,
        )

        assertIs<OrderIdempotencyMatch.Missing>(
            ledger.match("expired", FINGERPRINT_A, 1_000)
        )
        val duplicate = assertIs<OrderIdempotencyRecordResult.Duplicate>(
            ledger.record("fresh", FINGERPRINT_B, 42, 1_000, 1_000)
        )
        assertEquals(900, duplicate.entry.recordedAtMillis)
        assertIs<OrderIdempotencyMatch.Missing>(
            ledger.match("fresh", FINGERPRINT_B, 1_001)
        )
    }

    @Test
    fun concurrentIdenticalRecordsHaveExactlyOneWinner() {
        val ledger = OrderIdempotencyLedger()
        val workers = 24
        val start = CountDownLatch(1)
        val done = CountDownLatch(workers)
        val results = Collections.synchronizedList(mutableListOf<OrderIdempotencyRecordResult>())

        repeat(workers) {
            thread(name = "ledger-writer-$it") {
                try {
                    start.await()
                    results += ledger.record("order-1", FINGERPRINT_A, 42, NOW, NOW)
                } finally {
                    done.countDown()
                }
            }
        }
        start.countDown()

        assertTrue(done.await(5, TimeUnit.SECONDS))
        assertEquals(1, results.count { it is OrderIdempotencyRecordResult.Recorded })
        assertEquals(workers - 1, results.count { it is OrderIdempotencyRecordResult.Duplicate })
        assertEquals(1, ledger.size(NOW))
    }

    private fun ledgerWithOneEntry(): OrderIdempotencyLedger {
        return OrderIdempotencyLedger().also { ledger ->
            ledger.record("order-1", FINGERPRINT_A, 42, NOW, NOW)
        }
    }

    private fun entry(
        orderId: String = "order-1",
        fingerprint: String,
        callNumber: Int? = 42,
        recordedAt: Long = NOW,
    ) = OrderIdempotencyLedgerEntry(
        orderId = orderId,
        payloadFingerprint = fingerprint,
        originalCallNumber = callNumber,
        recordedAtMillis = recordedAt,
    )

    private companion object {
        const val NOW = 10_000L
        val FINGERPRINT_A = "a".repeat(64)
        val FINGERPRINT_B = "b".repeat(64)
        val FINGERPRINT_C = "c".repeat(64)
    }
}
