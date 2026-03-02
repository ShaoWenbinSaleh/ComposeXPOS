package com.cofopt.orderingmachine.viewmodel

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.cofopt.orderingmachine.OrderMode
import com.cofopt.orderingmachine.network.CashRegisterClient
import com.cofopt.orderingmachine.network.CashRegisterOrderItemPayload
import com.cofopt.orderingmachine.network.CashRegisterOrderPayload
import com.cofopt.orderingmachine.network.DeviceConfig
import com.cofopt.orderingmachine.network.PrinterConfig
import com.cofopt.orderingmachine.network.WecrHttpsClient
import com.cofopt.orderingmachine.ui.PaymentScreen.OrderPrint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal suspend fun MainViewModel.postOrderToCashRegisterIfConfiguredImpl(
    context: Context,
    paymentMethod: String,
    paymentStatus: String,
): Int? {
    if (!CashRegisterClient.isConfigured(context)) {
        android.util.Log.w("MainViewModel", "CashRegister is not configured")
        return null
    }

    return try {
        val now = System.currentTimeMillis()
        val orderId = "OM_${now}_${(1000..9999).random()}"
        val dineIn = orderMode == OrderMode.DINE_IN

        val items = cartItems.map { ci ->
            CashRegisterOrderItemPayload(
                menuItemId = ci.menuItem.id,
                nameEn = ci.menuItem.nameEn,
                nameZh = ci.menuItem.nameZh,
                nameNl = ci.menuItem.nameNl,
                quantity = ci.quantity,
                unitPrice = ci.menuItem.price,
                customizations = ci.customizations,
                customizationLines = customizationLinesForPrintImpl(ci.customizations)
            )
        }

        val payload = CashRegisterOrderPayload(
            orderId = orderId,
            createdAtMillis = now,
            source = "KIOSK",
            deviceName = DeviceConfig.deviceName(context),
            dineIn = dineIn,
            paymentMethod = paymentMethod,
            paymentStatus = paymentStatus,
            total = totalAmount,
            items = items
        )

        android.util.Log.d(
            "MainViewModel",
            "Sending order to CashRegister: orderId=$orderId, paymentMethod=$paymentMethod, total=$totalAmount, items=${items.size}"
        )

        val result = CashRegisterClient.postOrder(context, payload)
        android.util.Log.d("MainViewModel", "CashRegister response: $result")
        result
    } catch (e: Exception) {
        android.util.Log.e("MainViewModel", "Error posting order to CashRegister: ${e.message}", e)
        null
    }
}

internal fun MainViewModel.showPrintErrorImpl() {
    dispatch(MainViewModelAction.SetPrintError(true))
    viewModelScope.launch {
        kotlinx.coroutines.delay(100)
        dispatch(MainViewModelAction.SetPrintError(false))
    }
}

internal fun MainViewModel.printUnpaidOrderImpl(context: Context, callNumber: String) {
    try {
        val orderPrint = OrderPrint()
        val now = System.currentTimeMillis()
        val orderId = "OM_${now}_${(1000..9999).random()}"
        val dineIn = orderMode == OrderMode.DINE_IN

        val items = cartItems.map { ci ->
            CashRegisterOrderItemPayload(
                menuItemId = ci.menuItem.id,
                nameEn = ci.menuItem.nameEn,
                nameZh = ci.menuItem.nameZh,
                nameNl = ci.menuItem.nameNl,
                quantity = ci.quantity,
                unitPrice = ci.menuItem.price,
                customizations = ci.customizations,
                customizationLines = customizationLinesForPrintImpl(ci.customizations)
            )
        }

        val orderPayload = CashRegisterOrderPayload(
            orderId = orderId,
            createdAtMillis = now,
            source = "KIOSK",
            deviceName = DeviceConfig.deviceName(context),
            dineIn = dineIn,
            paymentMethod = "CASH",
            paymentStatus = "UNPAID",
            total = totalAmount,
            items = items
        )

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
    transactionRef: String? = null,
    wecrStatus: WecrHttpsClient.TransactionStatus? = null
) {
    try {
        val orderPrint = OrderPrint()
        val now = System.currentTimeMillis()
        val orderId = "OM_${now}_${(1000..9999).random()}"
        val dineIn = orderMode == OrderMode.DINE_IN

        val items = cartItems.map { ci ->
            CashRegisterOrderItemPayload(
                menuItemId = ci.menuItem.id,
                nameEn = ci.menuItem.nameEn,
                nameZh = ci.menuItem.nameZh,
                nameNl = ci.menuItem.nameNl,
                quantity = ci.quantity,
                unitPrice = ci.menuItem.price,
                customizations = ci.customizations,
                customizationLines = customizationLinesForPrintImpl(ci.customizations)
            )
        }

        val orderPayload = CashRegisterOrderPayload(
            orderId = orderId,
            createdAtMillis = now,
            source = "KIOSK",
            deviceName = DeviceConfig.deviceName(context),
            dineIn = dineIn,
            paymentMethod = "CARD",
            paymentStatus = "PAID",
            total = totalAmount,
            items = items
        )

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
