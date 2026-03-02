package com.cofopt.orderingmachine.ui.PaymentScreen

import com.cofopt.orderingmachine.formatEuroAmount
import com.cofopt.orderingmachine.network.CashRegisterOrderPayload
import com.cofopt.orderingmachine.network.OrderingPlatformContext
import com.cofopt.orderingmachine.network.WecrHttpsClient

/**
 * Open-source mock printer implementation.
 *
 * Real implementation should:
 * 1) build printer payload (ESC/POS or vendor SDK format),
 * 2) send through configured transport,
 * 3) report callback success/failure by device response.
 */
class OrderPrint {
    val MerchantInfo = "YOUR_STORE_NAME\nYOUR_STORE_ADDRESS\nYOUR_STORE_PHONE"

    enum class PrintType {
        ORDER,
        ORDER_UNPAID,
        RECEIPT,
        KITCHEN,
        ORDER_UNPAID_SUNMI,
        RECEIPT_SUNMI,
    }

    interface PrintCallback {
        fun onSuccess()
        fun onError(error: String)
    }

    fun printOrder(
        order: CashRegisterOrderPayload,
        printType: PrintType,
        callNumber: String? = null,
        context: OrderingPlatformContext? = null,
        transactionRef: String? = null,
        wecrStatus: WecrHttpsClient.TransactionStatus? = null,
        callback: PrintCallback? = null,
    ) {
        runCatching {
            if (context != null) {
                showOrderPrintMockNotice(context, "OrderingMachine ${printType.name}")
            }
            println(
                "OrderPrint MOCK type=${printType.name} orderId=${order.orderId} callNumber=${callNumber ?: "-"} tx=${transactionRef ?: "-"}"
            )
            callback?.onSuccess()
        }.onFailure {
            callback?.onError(it.message ?: "Unknown mock print error")
        }
    }

    fun generatePrintPayload(
        order: CashRegisterOrderPayload,
        printType: PrintType,
        callNumber: String? = null,
        context: OrderingPlatformContext? = null,
    ): ByteArray {
        val text = buildString {
            append("[MOCK ORDERING PRINT PAYLOAD]\n")
            append("type=").append(printType.name).append("\n")
            append("orderId=").append(order.orderId).append("\n")
            append("callNumber=").append(callNumber ?: "-").append("\n")
            append("payment=").append(order.paymentMethod).append('/').append(order.paymentStatus).append("\n")
            append("total=").append(formatEuroAmount(order.total)).append("\n")
            append("items=\n")
            order.items.forEach {
                append("- ")
                    .append(it.quantity)
                    .append(" x ")
                    .append(it.nameEn.ifBlank { it.menuItemId })
                    .append(" @ ")
                    .append(formatEuroAmount(it.unitPrice))
                    .append("\n")
            }
        }
        return text.encodeToByteArray()
    }
}
