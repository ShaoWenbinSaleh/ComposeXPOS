package com.cofopt.cashregister.network.wecr

import android.content.Context
import com.cofopt.shared.mock.MockFeatureNotice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale

sealed class CardPaymentOutcome {
    data class Success(
        val transactionRef: String,
        val keyIndex: Int,
        val status: WecrHttpsClient.TransactionStatus,
    ) : CardPaymentOutcome()

    data class Failed(val message: String) : CardPaymentOutcome()

    data class RequestFailed(val message: String) : CardPaymentOutcome()
    data object PosTriggerFailed : CardPaymentOutcome()
    data object Timeout : CardPaymentOutcome()
    data object Cancelled : CardPaymentOutcome()
}

/**
 * Open-source mock payment flow.
 *
 * Real implementation should:
 * 1) call provider StartTransaction API,
 * 2) trigger physical POS device,
 * 3) poll status until success/failure/timeout,
 * 4) cancel on user abort,
 * 5) persist audit logs.
 */
object WecrCardPayment {
    suspend fun pay(
        context: Context,
        amount: Double,
        cancelRequested: () -> Boolean = { false },
    ): CardPaymentOutcome = withContext(Dispatchers.IO) {
        MockFeatureNotice.showPayment(context, "CashRegister")

        if (cancelRequested()) {
            return@withContext CardPaymentOutcome.Cancelled
        }

        delay(350)

        if (cancelRequested()) {
            return@withContext CardPaymentOutcome.Cancelled
        }

        val transactionRef = "MOCK_TXN_${System.currentTimeMillis()}"
        val status = WecrHttpsClient.TransactionStatus(
            keyIndex = "0",
            transactionResult = "0",
            status = "00",
            message = "MOCK: payment accepted",
            amount = String.format(Locale.US, "%.2f", amount),
            brand = "MOCK_CARD",
            ticket = "MOCK_TICKET_${System.currentTimeMillis()}",
            extras = mapOf("integration_mode" to "mock")
        )

        CardPaymentOutcome.Success(
            transactionRef = transactionRef,
            keyIndex = 0,
            status = status,
        )
    }
}
