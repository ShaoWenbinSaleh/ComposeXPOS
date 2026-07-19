package com.cofopt.orderingmachine.viewmodel

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.cofopt.orderingmachine.network.CashRegisterClient
import com.cofopt.orderingmachine.network.CashRegisterOrderPayload
import com.cofopt.orderingmachine.network.CashRegisterOrderSubmissionResult
import com.cofopt.orderingmachine.network.PrinterConfig
import com.cofopt.orderingmachine.network.WecrHttpsClient
import com.cofopt.orderingmachine.ui.PaymentScreen.OrderPrint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal suspend fun MainViewModel.postOrderToCashRegisterIfConfiguredImpl(
    context: Context,
    paymentMethod: String,
    paymentStatus: String,
): CashRegisterOrderSubmissionResult {
    if (!CashRegisterClient.isConfigured(context)) {
        android.util.Log.w("MainViewModel", "CashRegister is not configured")
        return CashRegisterOrderSubmissionResult.Rejected(0, "cashregister_not_configured")
    }

    val payload = activeOrderPayloadForImpl(context, paymentMethod, paymentStatus)

    return try {
        android.util.Log.d(
            "MainViewModel",
            "Sending order to CashRegister: orderId=${payload.orderId}, paymentMethod=$paymentMethod, total=${payload.total}, items=${payload.items.size}"
        )

        val result = CashRegisterClient.submitOrder(context, payload)
        android.util.Log.d("MainViewModel", "CashRegister response: $result")
        result
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (e: Exception) {
        android.util.Log.e("MainViewModel", "Error posting order to CashRegister: ${e.message}", e)
        val durable = try {
            CashRegisterClient.isOrderDurablyRecorded(context, payload)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
        if (durable) {
            CashRegisterOrderSubmissionResult.Pending(
                orderId = payload.orderId,
                message = e.message,
            )
        } else {
            CashRegisterOrderSubmissionResult.Indeterminate(
                orderId = payload.orderId,
                message = e.message ?: "order_submission_durability_unknown",
            )
        }
    }
}

internal fun MainViewModel.activeOrderPayloadForImpl(
    context: Context,
    paymentMethod: String,
    paymentStatus: String,
): CashRegisterOrderPayload = synchronized(activeOrderLock) {
    val normalizedMethod = paymentMethod.trim().uppercase()
    val normalizedStatus = paymentStatus.trim().uppercase()
    activeOrderPayload?.let { existing ->
        if (
            existing.paymentMethod.trim().uppercase() != normalizedMethod ||
            existing.paymentStatus.trim().uppercase() != normalizedStatus
        ) {
            throw IllegalStateException("pending_order_uses_${existing.paymentMethod.lowercase()}_payment")
        }
        return@synchronized existing
    }

    buildCashRegisterOrderPayloadImpl(
        context = context,
        cartItems = cartItems,
        total = totalAmount,
        dineIn = orderMode == com.cofopt.orderingmachine.OrderMode.DINE_IN,
        paymentMethod = normalizedMethod,
        paymentStatus = normalizedStatus,
    ).also { activeOrderPayload = it }
}

internal fun MainViewModel.currentActiveOrderPayloadImpl(): CashRegisterOrderPayload? =
    synchronized(activeOrderLock) { activeOrderPayload }

internal fun MainViewModel.clearActiveOrderPayloadImpl() {
    synchronized(activeOrderLock) { activeOrderPayload = null }
}

internal fun MainViewModel.showPrintErrorImpl() {
    dispatch(MainViewModelAction.SetPrintError(true))
    viewModelScope.launch {
        kotlinx.coroutines.delay(100)
        dispatch(MainViewModelAction.SetPrintError(false))
    }
}

internal fun MainViewModel.printUnpaidOrderImpl(
    context: Context,
    callNumber: String,
    orderPayload: CashRegisterOrderPayload,
) {
    try {
        val orderPrint = OrderPrint()

        val printerMode = PrinterConfig.mode(context)
        val printType = if (printerMode == "SUNMI") {
            OrderPrint.PrintType.ORDER_UNPAID_SUNMI
        } else {
            OrderPrint.PrintType.ORDER_UNPAID
        }
        
        android.util.Log.d("MainViewModel", "printUnpaidOrder: printerMode=$printerMode, printType=$printType, callNumber=$callNumber")
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                orderPrint.printOrder(
                    order = orderPayload,
                    printType = printType,
                    callNumber = callNumber,
                    context = context,
                    callback = object : OrderPrint.PrintCallback {
                        override fun onSuccess() {
                            android.util.Log.d("MainViewModel", "Unpaid order printed successfully")
                        }

                        override fun onError(error: String) {
                            android.util.Log.e("MainViewModel", "Failed to print unpaid order: $error")
                            viewModelScope.launch { showPrintErrorImpl() }
                        }
                    }
                )
            } catch (e: Exception) {
                e.printStackTrace()
                viewModelScope.launch { showPrintErrorImpl() }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        viewModelScope.launch { showPrintErrorImpl() }
    }
}

internal fun MainViewModel.printPaidOrderImpl(
    context: Context,
    callNumber: String,
    orderPayload: CashRegisterOrderPayload,
    transactionRef: String? = null,
    wecrStatus: WecrHttpsClient.TransactionStatus? = null
) {
    try {
        val orderPrint = OrderPrint()

        val printerMode = PrinterConfig.mode(context)
        val printType = if (printerMode == "SUNMI") {
            OrderPrint.PrintType.RECEIPT_SUNMI
        } else {
            OrderPrint.PrintType.RECEIPT
        }
        
        android.util.Log.d("MainViewModel", "printPaidOrder: printerMode=$printerMode, printType=$printType, callNumber=$callNumber")
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                orderPrint.printOrder(
                    order = orderPayload,
                    printType = printType,
                    callNumber = callNumber,
                    context = context,
                    transactionRef = transactionRef,
                    wecrStatus = wecrStatus,
                    callback = object : OrderPrint.PrintCallback {
                        override fun onSuccess() {
                            android.util.Log.d("MainViewModel", "Paid order printed successfully")
                        }

                        override fun onError(error: String) {
                            android.util.Log.e("MainViewModel", "Failed to print paid order: $error")
                            viewModelScope.launch { showPrintErrorImpl() }
                        }
                    }
                )
            } catch (e: Exception) {
                e.printStackTrace()
                viewModelScope.launch { showPrintErrorImpl() }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        viewModelScope.launch { showPrintErrorImpl() }
    }
}
