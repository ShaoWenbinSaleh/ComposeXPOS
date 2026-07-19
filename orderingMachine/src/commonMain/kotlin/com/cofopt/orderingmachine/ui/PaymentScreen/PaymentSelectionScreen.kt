package com.cofopt.orderingmachine.ui.PaymentScreen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.cofopt.orderingmachine.CartItem
import com.cofopt.orderingmachine.Language
import com.cofopt.orderingmachine.PaymentMethod
import com.cofopt.orderingmachine.network.WecrConfig
import com.cofopt.orderingmachine.network.rememberOrderingPlatformContext

@Composable
fun PaymentSelectionScreen(
    language: Language,
    dineIn: Boolean,
    cartItems: List<CartItem>,
    total: Double,
    paymentError: String? = null,
    printError: Boolean = false,
    selectionEnabled: Boolean = true,
    onSelect: (PaymentMethod, Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val context = rememberOrderingPlatformContext()
    val enableCardPayment = WecrConfig.enableCardPayment(context)
    val debugCardSmEnabled = WecrConfig.debugCardSmEnabled(context)
    // There is no real WECR transport in the open-source build. The card
    // option is therefore available only when the operator explicitly enables
    // the debug state machine as well as the payment option.
    val cardPaymentAvailable = enableCardPayment && debugCardSmEnabled

    var debugPosRequestSuccess by remember { mutableStateOf(WecrConfig.debugPosRequestSuccess(context)) }
    var debugPosTriggerSuccess by remember { mutableStateOf(WecrConfig.debugPosTriggerSuccess(context)) }

    SharedPaymentSelectionScreen(
        language = language,
        dineIn = dineIn,
        cartItems = cartItems,
        total = total,
        paymentError = paymentError,
        printError = printError,
        selectionEnabled = selectionEnabled,
        cardPaymentEnabled = cardPaymentAvailable,
        isCardSystemConnected = cardPaymentAvailable,
        isCheckingCardSystem = false,
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
