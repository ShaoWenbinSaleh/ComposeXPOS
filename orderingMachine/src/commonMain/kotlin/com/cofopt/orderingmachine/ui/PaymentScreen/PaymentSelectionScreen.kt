package com.cofopt.orderingmachine.ui.PaymentScreen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.cofopt.orderingmachine.CartItem
import com.cofopt.orderingmachine.Language
import com.cofopt.orderingmachine.PaymentMethod
import com.cofopt.orderingmachine.network.CashRegisterClient
import com.cofopt.orderingmachine.network.WecrConfig
import com.cofopt.orderingmachine.network.rememberOrderingPlatformContext
import kotlinx.coroutines.delay

@Composable
fun PaymentSelectionScreen(
    language: Language,
    dineIn: Boolean,
    cartItems: List<CartItem>,
    total: Double,
    paymentError: String? = null,
    printError: Boolean = false,
    onSelect: (PaymentMethod, Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val context = rememberOrderingPlatformContext()
    val enableCardPayment = WecrConfig.enableCardPayment(context)
    val debugCardSmEnabled = WecrConfig.debugCardSmEnabled(context)

    var debugPosRequestSuccess by remember { mutableStateOf(WecrConfig.debugPosRequestSuccess(context)) }
    var debugPosTriggerSuccess by remember { mutableStateOf(WecrConfig.debugPosTriggerSuccess(context)) }

    var isCashRegisterConnected by remember { mutableStateOf<Boolean?>(null) }
    var isCheckingConnection by remember { mutableStateOf(false) }

    LaunchedEffect(enableCardPayment, debugCardSmEnabled) {
        if (!enableCardPayment) return@LaunchedEffect
        if (debugCardSmEnabled) {
            isCashRegisterConnected = true
            isCheckingConnection = false
            return@LaunchedEffect
        }

        while (true) {
            isCheckingConnection = true
            val connected = CashRegisterClient.testConnection(context)
            isCashRegisterConnected = connected
            isCheckingConnection = false
            delay(if (connected) 5000 else 1500)
        }
    }

    SharedPaymentSelectionScreen(
        language = language,
        dineIn = dineIn,
        cartItems = cartItems,
        total = total,
        paymentError = paymentError,
        printError = printError,
        cardPaymentEnabled = enableCardPayment,
        isCardSystemConnected = isCashRegisterConnected,
        isCheckingCardSystem = isCheckingConnection,
        debugCardSmEnabled = debugCardSmEnabled,
        debugPosRequestSuccess = debugPosRequestSuccess,
        debugPosTriggerSuccess = debugPosTriggerSuccess,
        onDebugPosRequestSuccessChanged = { enabled ->
            debugPosRequestSuccess = enabled
            WecrConfig.setDebugPosRequestSuccess(context, enabled)
        },
        onDebugPosTriggerSuccessChanged = { enabled ->
            debugPosTriggerSuccess = enabled
            WecrConfig.setDebugPosTriggerSuccess(context, enabled)
        },
        onSelect = onSelect,
        onBack = onBack,
    )
}
