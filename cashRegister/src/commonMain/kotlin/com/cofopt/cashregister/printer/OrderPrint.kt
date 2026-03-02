package com.cofopt.cashregister.printer

import com.cofopt.cashregister.network.OrderPayload
import kotlin.math.absoluteValue
import kotlin.math.roundToLong

/**
 * Open-source mock payload generator.
 *
 * Real implementation should output ESC/POS (or vendor format) bytes, including
 * layout, logo, codepage handling, and cut/feed commands.
 */
class OrderPrint {
    val MerchantInfo = "YOUR_STORE_NAME\nYOUR_STORE_ADDRESS\nYOUR_STORE_PHONE"

    enum class PrintType {
        ORDER,
        ORDER_UNPAID,
        RECEIPT,
        KITCHEN
    }

    fun generatePrintPayload(
        order: OrderPayload,
        printType: PrintType,
        callNumber: String? = null,
    ): ByteArray {
        return buildMockPayload(order, printType, callNumber)
    }

    private fun buildMockPayload(
        order: OrderPayload,
        printType: PrintType,
        callNumber: String?,
    ): ByteArray {
        val header = buildString {
            append("[MOCK PRINT PAYLOAD]\n")
            append("type=").append(printType.name).append("\n")
            append("orderId=").append(order.orderId).append("\n")
            append("callNumber=").append(callNumber ?: "-").append("\n")
            append("payment=").append(order.paymentMethod).append('/').append(order.status).append("\n")
            append("total=").append(formatMoney(order.total)).append("\n")
            append("items=\n")
        }

        val items = order.items.joinToString(separator = "\n") { item ->
            "- ${item.quantity} x ${item.nameEn.ifBlank { item.menuItemId }} @ ${formatMoney(item.unitPrice)}"
        }

        return (header + items + "\n").encodeToByteArray()
    }

    private fun formatMoney(value: Double): String {
        val scaled = (value * 100.0).roundToLong()
        val absScaled = scaled.absoluteValue
        val integerPart = absScaled / 100
        val fractionPart = (absScaled % 100).toString().padStart(2, '0')
        val sign = if (scaled < 0) "-" else ""
        return "$sign$integerPart.$fractionPart"
    }
}
