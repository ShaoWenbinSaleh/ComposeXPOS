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
    val errorMessage: String? = null
)
