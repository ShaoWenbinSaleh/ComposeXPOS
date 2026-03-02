package com.cofopt.orderingmachine.viewmodel

import com.cofopt.orderingmachine.Language
import com.cofopt.orderingmachine.OrderMode
import com.cofopt.orderingmachine.Screen

internal fun MainViewModel.clearLastCallNumberImpl() {
    dispatch(MainViewModelAction.ClearLastCallNumber)
}

internal fun MainViewModel.updateLanguageImpl(lang: Language) {
    dispatch(MainViewModelAction.SetLanguage(lang))
}

internal fun MainViewModel.selectOrderModeImpl(mode: OrderMode) {
    dispatch(MainViewModelAction.SetOrderMode(mode))
    dispatch(MainViewModelAction.Navigate(Screen.ORDERING))
}

internal fun MainViewModel.reorderImpl() {
    dispatch(MainViewModelAction.ClearCart)
    dispatch(MainViewModelAction.Navigate(Screen.MODE_SELECTION))
}

internal fun MainViewModel.navigateToImpl(screen: Screen) {
    dispatch(MainViewModelAction.Navigate(screen))
}

internal fun MainViewModel.resetOrderImpl() {
    dispatch(MainViewModelAction.ResetOrder)
}

internal fun MainViewModel.startPaymentImpl() {
    dispatch(MainViewModelAction.StartPayment)
}
