package com.cofopt.orderingmachine.viewmodel

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.cofopt.orderingmachine.Language
import com.cofopt.orderingmachine.MenuItem
import com.cofopt.orderingmachine.OrderMode
import com.cofopt.orderingmachine.PaymentMethod
import com.cofopt.orderingmachine.Screen
import com.cofopt.orderingmachine.network.WecrConfig
import com.cofopt.orderingmachine.ui.PaymentScreen.CardPaymentResult
import com.cofopt.orderingmachine.ui.PaymentScreen.CardPaymentProcessResult
import kotlinx.coroutines.Job
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import android.app.Application

class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        fun Factory(application: Application): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MainViewModel(application) as T
            }
        }
        
        // For testing: set CashRegister config
        fun setCashRegisterConfig(context: Context, host: String, port: Int) {
            com.cofopt.orderingmachine.network.CashRegisterConfig.save(context, host, port)
            android.util.Log.d("MainViewModel", "Set CashRegister config: host=$host, port=$port")
        }
    }

    fun setPaymentMethodForFlow(method: PaymentMethod) = setPaymentMethodForFlowImpl(method)

    suspend fun processCounterPayment(context: Context): Int = processCounterPaymentImpl(context)

    suspend fun processCounterPaymentOffline(context: Context): Int = processCounterPaymentOfflineImpl(context)

    suspend fun debugSimulateCardPaid(context: Context): Int = debugSimulateCardPaidImpl(context)

    internal var uiState by mutableStateOf(MainViewModelUiState(posIpAddress = WecrConfig.POS_IP))
        private set

    // --- State ---
    var language: Language
        get() = uiState.language
        internal set(value) {
            dispatch(MainViewModelAction.SetLanguage(value))
        }

    var currentScreen: Screen
        get() = uiState.currentScreen
        internal set(value) {
            dispatch(MainViewModelAction.Navigate(value))
        }

    var orderMode: OrderMode?
        get() = uiState.orderMode
        internal set(value) {
            dispatch(MainViewModelAction.SetOrderMode(value))
        }

    var paymentMethod: PaymentMethod?
        get() = uiState.paymentMethod
        internal set(value) {
            dispatch(MainViewModelAction.SetPaymentMethod(value))
        }

    var paymentError: String?
        get() = uiState.paymentError
        internal set(value) {
            dispatch(MainViewModelAction.SetPaymentError(value))
        }

    val printError: Boolean
        get() = uiState.printError

    var cashRegisterCreateFailed: Boolean
        get() = uiState.cashRegisterCreateFailed
        internal set(value) {
            dispatch(MainViewModelAction.SetCashRegisterCreateFailed(value))
        }

    var cashRegisterCreateFailedWasPaid: Boolean
        get() = uiState.cashRegisterCreateFailedWasPaid
        internal set(value) {
            dispatch(MainViewModelAction.SetCashRegisterCreateFailedWasPaid(value))
        }

    var lastCallNumber: Int?
        get() = uiState.lastCallNumber
        internal set(value) {
            dispatch(MainViewModelAction.SetLastCallNumber(value))
        }

    var cardPaymentFailed: Boolean
        get() = uiState.cardPaymentFailed
        internal set(value) {
            dispatch(MainViewModelAction.SetCardPaymentFailed(value))
        }

    // Store current transaction info for cancellation
    var currentTransactionRef: String?
        get() = uiState.currentTransactionRef
        internal set(value) {
            dispatch(
                MainViewModelAction.SetCurrentTransactionInfo(
                    transactionRef = value,
                    keyIndex = uiState.currentKeyIndex
                )
            )
        }

    var currentKeyIndex: Int?
        get() = uiState.currentKeyIndex
        internal set(value) {
            dispatch(
                MainViewModelAction.SetCurrentTransactionInfo(
                    transactionRef = uiState.currentTransactionRef,
                    keyIndex = value
                )
            )
        }

    // Store POS trigger status
    var posTriggerFailed: Boolean
        get() = uiState.posTriggerFailed
        internal set(value) {
            dispatch(MainViewModelAction.SetPosTriggerFailed(value))
        }

    internal var paymentJob: Job? = null

    var posIpAddress: String
        get() = uiState.posIpAddress
        internal set(value) {
            dispatch(MainViewModelAction.SetPosIpAddress(value))
        }

    val cartItems: List<com.cofopt.orderingmachine.CartItem>
        get() = uiState.cartItems

    // Helper to get total quantity of a specific menu item (ignoring customizations) for the menu grid
    fun getQuantity(menuItemId: String): Int = getQuantityImpl(menuItemId)

    var menu: List<MenuItem>
        get() = uiState.menu
        internal set(value) {
            dispatch(MainViewModelAction.SetMenu(value))
        }

    val totalAmount: Double
        get() = uiState.cartItems.sumOf { it.menuItem.price * it.quantity }

    // --- Actions ---

    fun loadMenu(context: Context) = loadMenuImpl(context)

    fun refreshMenuFromCashRegister(context: Context) = refreshMenuFromCashRegisterImpl(context)

    fun syncDishesFromCashRegister(context: Context) = syncDishesFromCashRegisterImpl(context)

    fun syncPricesOnHomeScreen() = syncPricesOnHomeScreenImpl()

    fun clearLastCallNumber() = clearLastCallNumberImpl()

    fun updateLanguage(lang: Language) = updateLanguageImpl(lang)

    fun selectOrderMode(mode: OrderMode) = selectOrderModeImpl(mode)

    fun markCardPaymentFailed(failed: Boolean) = markCardPaymentFailedImpl(failed)

    fun showPrintError() = showPrintErrorImpl()

    /**
     * 开始刷卡支付 - 返回交易信息
     */
    suspend fun beginCardPayment(context: Context): CardPaymentResult? = beginCardPaymentImpl(context)
    
    /**
     * 处理刷卡支付 - 返回支付结果
     */
    suspend fun processCardPayment(context: Context): CardPaymentProcessResult = processCardPaymentImpl(context)
    
    suspend fun triggerPosMachine(context: Context) = triggerPosMachineImpl(context)

    fun setCurrentTransactionInfo(transactionRef: String?, keyIndex: Int?) = setCurrentTransactionInfoImpl(transactionRef, keyIndex)

    fun cancelCurrentTransaction(context: Context) = cancelCurrentTransactionImpl(context)

    fun handlePaymentTimeout() = handlePaymentTimeoutImpl()

    fun clearPaymentError() = clearPaymentErrorImpl()

    fun startPayment() = startPaymentImpl()

    fun reorder() = reorderImpl()

    fun navigateTo(screen: Screen) = navigateToImpl(screen)

    fun addToCart(item: MenuItem, customizations: Map<String, String> = emptyMap()) =
        addToCartImpl(item, customizations)

    fun updateCartItemQuantity(uuid: String, quantity: Int) = updateCartItemQuantityImpl(uuid, quantity)

    fun decrementQuantity(item: MenuItem) = decrementQuantityImpl(item)

    fun clearCart() = clearCartImpl()

    private fun resetOrder() = resetOrderImpl()

    internal fun dispatch(action: MainViewModelAction): MainViewModelReduceResult {
        val result = reduceMainViewModelState(uiState, action)
        uiState = result.state
        if (result.syncPricesOnHome) {
            syncPricesOnHomeScreen()
        }
        return result
    }
}
