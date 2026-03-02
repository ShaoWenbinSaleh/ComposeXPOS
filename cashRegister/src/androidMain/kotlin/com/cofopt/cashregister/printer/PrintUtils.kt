package com.cofopt.cashregister.printer

import android.content.Context
import android.util.Log
import com.cofopt.cashregister.network.OrderPayload
import com.cofopt.shared.mock.MockFeatureNotice

// Printer types enum
enum class PrinterKind {
    ORDER,
    RECEIPT,
    KITCHEN
}

/**
 * Open-source mock printing entry points.
 *
 * Real implementation should route payloads to the selected printer transport
 * (USB/IP/vendor SDK) and return true only when the job is acknowledged.
 */
object PrintUtils {
    private const val TAG = "PrintUtils"

    fun printOrder(context: Context, order: OrderPayload, callNumber: String? = null): Boolean {
        return mockPrint(context, order, PrinterKind.ORDER, callNumber)
    }

    fun printReceipt(context: Context, order: OrderPayload, callNumber: String? = null): Boolean {
        return mockPrint(context, order, PrinterKind.RECEIPT, callNumber)
    }

    fun printKitchen(context: Context, order: OrderPayload, callNumber: String? = null): Boolean {
        return mockPrint(context, order, PrinterKind.KITCHEN, callNumber)
    }

    private fun mockPrint(
        context: Context,
        order: OrderPayload,
        printerKind: PrinterKind,
        callNumber: String?,
    ): Boolean {
        MockFeatureNotice.showPrint(context, "CashRegister ${printerKind.name}/STANDARD")

        Log.i(
            TAG,
            "MOCK print kind=${printerKind.name} mode=STANDARD orderId=${order.orderId} callNumber=${callNumber ?: "-"} total=${order.total}"
        )

        // Always succeed in open-source mock mode.
        return true
    }
}
