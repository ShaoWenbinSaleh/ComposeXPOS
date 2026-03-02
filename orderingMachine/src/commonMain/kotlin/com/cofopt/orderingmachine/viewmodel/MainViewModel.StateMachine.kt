package com.cofopt.orderingmachine.viewmodel

import com.cofopt.orderingmachine.CartItem
import com.cofopt.orderingmachine.Language
import com.cofopt.orderingmachine.MenuItem
import com.cofopt.orderingmachine.OrderMode
import com.cofopt.orderingmachine.PaymentMethod
import com.cofopt.orderingmachine.Screen

internal data class MainViewModelUiState(
    val language: Language = Language.default,
    val currentScreen: Screen = Screen.MODE_SELECTION,
    val orderMode: OrderMode? = null,
    val paymentMethod: PaymentMethod? = null,
    val paymentError: String? = null,
    val printError: Boolean = false,
    val cashRegisterCreateFailed: Boolean = false,
    val cashRegisterCreateFailedWasPaid: Boolean = false,
    val lastCallNumber: Int? = null,
    val cardPaymentFailed: Boolean = false,
    val currentTransactionRef: String? = null,
    val currentKeyIndex: Int? = null,
    val posTriggerFailed: Boolean = false,
    val posIpAddress: String = "",
    val cartItems: List<CartItem> = emptyList(),
    val menu: List<MenuItem> = emptyList(),
)

internal sealed interface MainViewModelAction {
    data class SetLanguage(val language: Language) : MainViewModelAction
    data class SetOrderMode(val mode: OrderMode?) : MainViewModelAction
    data class SetPaymentMethod(val method: PaymentMethod?) : MainViewModelAction
    data class SetPaymentError(val error: String?) : MainViewModelAction
    data class SetPrintError(val visible: Boolean) : MainViewModelAction
    data class SetCashRegisterCreateFailed(val failed: Boolean) : MainViewModelAction
    data class SetCashRegisterCreateFailedWasPaid(val wasPaid: Boolean) : MainViewModelAction
    data class SetLastCallNumber(val number: Int?) : MainViewModelAction
    data class SetCardPaymentFailed(val failed: Boolean) : MainViewModelAction
    data class SetCurrentTransactionInfo(val transactionRef: String?, val keyIndex: Int?) : MainViewModelAction
    data class SetPosTriggerFailed(val failed: Boolean) : MainViewModelAction
    data class SetPosIpAddress(val ip: String) : MainViewModelAction
    data class SetMenu(val menu: List<MenuItem>) : MainViewModelAction
    data object ClearCart : MainViewModelAction
    data class AddCartItem(val item: MenuItem, val customizations: Map<String, String>) : MainViewModelAction
    data class UpdateCartItemQuantity(val uuid: String, val quantity: Int) : MainViewModelAction
    data class DecrementByMenuItem(val item: MenuItem) : MainViewModelAction
    data object ClearLastCallNumber : MainViewModelAction
    data object ResetOrder : MainViewModelAction
    data object StartPayment : MainViewModelAction
    data class Navigate(val screen: Screen) : MainViewModelAction
}

internal data class MainViewModelReduceResult(
    val state: MainViewModelUiState,
    val syncPricesOnHome: Boolean = false,
)

internal fun reduceMainViewModelState(
    state: MainViewModelUiState,
    action: MainViewModelAction
): MainViewModelReduceResult {
    return when (action) {
        is MainViewModelAction.SetLanguage -> MainViewModelReduceResult(state = state.copy(language = action.language))
        is MainViewModelAction.SetOrderMode -> MainViewModelReduceResult(state = state.copy(orderMode = action.mode))
        is MainViewModelAction.SetPaymentMethod -> MainViewModelReduceResult(state = state.copy(paymentMethod = action.method))
        is MainViewModelAction.SetPaymentError -> MainViewModelReduceResult(state = state.copy(paymentError = action.error))
        is MainViewModelAction.SetPrintError -> MainViewModelReduceResult(state = state.copy(printError = action.visible))
        is MainViewModelAction.SetCashRegisterCreateFailed -> MainViewModelReduceResult(
            state = state.copy(cashRegisterCreateFailed = action.failed)
        )
        is MainViewModelAction.SetCashRegisterCreateFailedWasPaid -> MainViewModelReduceResult(
            state = state.copy(cashRegisterCreateFailedWasPaid = action.wasPaid)
        )
        is MainViewModelAction.SetLastCallNumber -> MainViewModelReduceResult(state = state.copy(lastCallNumber = action.number))
        is MainViewModelAction.SetCardPaymentFailed -> MainViewModelReduceResult(state = state.copy(cardPaymentFailed = action.failed))
        is MainViewModelAction.SetCurrentTransactionInfo -> MainViewModelReduceResult(
            state = state.copy(currentTransactionRef = action.transactionRef, currentKeyIndex = action.keyIndex)
        )
        is MainViewModelAction.SetPosTriggerFailed -> MainViewModelReduceResult(state = state.copy(posTriggerFailed = action.failed))
        is MainViewModelAction.SetPosIpAddress -> MainViewModelReduceResult(state = state.copy(posIpAddress = action.ip))
        is MainViewModelAction.SetMenu -> MainViewModelReduceResult(state = state.copy(menu = action.menu))
        MainViewModelAction.ClearCart -> MainViewModelReduceResult(state = state.copy(cartItems = emptyList()))
        is MainViewModelAction.AddCartItem -> MainViewModelReduceResult(
            state = state.copy(cartItems = addCartItem(state.cartItems, action.item, action.customizations))
        )
        is MainViewModelAction.UpdateCartItemQuantity -> MainViewModelReduceResult(
            state = state.copy(cartItems = updateCartItemQuantity(state.cartItems, action.uuid, action.quantity))
        )
        is MainViewModelAction.DecrementByMenuItem -> MainViewModelReduceResult(
            state = state.copy(cartItems = decrementByMenuItem(state.cartItems, action.item))
        )
        MainViewModelAction.ClearLastCallNumber -> MainViewModelReduceResult(
            state = state.copy(
                lastCallNumber = null,
                cashRegisterCreateFailed = false,
                cashRegisterCreateFailedWasPaid = false
            )
        )
        MainViewModelAction.ResetOrder -> MainViewModelReduceResult(
            state = state.copy(
                orderMode = null,
                paymentMethod = null,
                paymentError = null,
                cardPaymentFailed = false,
                cashRegisterCreateFailed = false,
                cashRegisterCreateFailedWasPaid = false,
                currentTransactionRef = null,
                currentKeyIndex = null,
                posTriggerFailed = false,
                cartItems = emptyList(),
            )
        )
        MainViewModelAction.StartPayment -> MainViewModelReduceResult(
            state = state.copy(
                paymentError = null,
                currentScreen = Screen.PAYMENT_SELECTION
            )
        )
        is MainViewModelAction.Navigate -> {
            val nextState = state.copy(currentScreen = action.screen)
            if (!shouldResetOrderForScreen(action.screen)) {
                return MainViewModelReduceResult(state = nextState)
            }
            val reset = reduceMainViewModelState(nextState, MainViewModelAction.ResetOrder)
            reset.copy(syncPricesOnHome = true)
        }
    }
}

private fun addCartItem(items: List<CartItem>, item: MenuItem, customizations: Map<String, String>): List<CartItem> {
    val existing = items.find { it.menuItem.id == item.id && it.customizations == customizations }
    if (existing == null) {
        return items + CartItem(menuItem = item, quantity = 1, customizations = customizations)
    }
    return updateCartItemQuantity(items, existing.uuid, existing.quantity + 1)
}

private fun updateCartItemQuantity(items: List<CartItem>, uuid: String, quantity: Int): List<CartItem> {
    if (quantity <= 0) return items.filterNot { it.uuid == uuid }
    var found = false
    val updated = items.map { current ->
        if (current.uuid != uuid) return@map current
        found = true
        current.copy(quantity = quantity)
    }
    return if (found) updated else items
}

private fun decrementByMenuItem(items: List<CartItem>, item: MenuItem): List<CartItem> {
    val target = items.indexOfLast { it.menuItem.id == item.id }
    if (target < 0) return items
    val current = items[target]
    return updateCartItemQuantity(items, current.uuid, current.quantity - 1)
}
