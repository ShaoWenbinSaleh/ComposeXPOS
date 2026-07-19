package com.cofopt.shared.payment.wecr

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class WecrHttpsClientTest {
    private fun client() = WecrHttpsClient(
        apiUrl = "https://example.invalid",
        login = "mock",
        sid = "mock",
        privateKeyPem = "",
    )

    @Test
    fun unknownReferenceIsNeverReportedAsPaid() = runBlocking {
        val status = client().getTransactionStatus("0", "NEVER_STARTED_REFERENCE")

        assertEquals("91", status?.status)
        assertEquals("1", status?.transactionResult)
    }

    @Test
    fun startedReferenceIsIdempotentAndCancellationRemovesIt() = runBlocking {
        val reference = "TEST_IDEMPOTENT_REFERENCE"
        val first = client().startTransaction(
            amount = 12.34,
            merchantRef = "ORDER_TEST",
            transactionRef = reference,
        )
        val second = client().startTransaction(
            amount = 12.34,
            merchantRef = "ORDER_TEST",
            transactionRef = reference,
        )

        assertEquals(reference, first?.transactionRef)
        assertEquals(reference, second?.transactionRef)
        assertEquals("00", client().getTransactionStatus("0", reference)?.status)
        assertEquals("00", client().cancelTransaction(0, reference)?.status)
        assertEquals("91", client().getTransactionStatus("0", reference)?.status)
    }
}
