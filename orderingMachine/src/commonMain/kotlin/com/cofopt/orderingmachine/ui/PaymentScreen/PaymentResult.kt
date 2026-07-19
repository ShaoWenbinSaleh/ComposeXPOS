package com.cofopt.orderingmachine.ui.PaymentScreen

/**
 * 刷卡支付结果
 */
data class CardPaymentResult(
    val transactionRef: String,
    val keyIndex: Int
)

/**
 * 刷卡支付处理结果
 */
data class CardPaymentProcessResult(
    val success: Boolean,
    val timeout: Boolean,
    val callNumber: Int? = null,
    val errorMessage: String? = null,
    /** True means the terminal result is unknown and another charge is unsafe. */
    val unresolved: Boolean = false,
)

sealed interface CardPaymentCancellationResult {
    data object Cancelled : CardPaymentCancellationResult
    data class Paid(val callNumber: Int?) : CardPaymentCancellationResult
    data class Unresolved(val message: String) : CardPaymentCancellationResult
}

sealed interface CardPaymentRecoveryOutcome {
    data class Paid(val callNumber: Int?) : CardPaymentRecoveryOutcome
    data object NotPaid : CardPaymentRecoveryOutcome
}
