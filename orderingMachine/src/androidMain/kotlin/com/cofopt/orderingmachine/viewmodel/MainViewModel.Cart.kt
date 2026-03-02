package com.cofopt.orderingmachine.viewmodel

import com.cofopt.orderingmachine.MenuItem

internal fun MainViewModel.getQuantityImpl(menuItemId: String): Int {
    return getQuantityFromCartImpl(cartItems, menuItemId)
}

internal fun MainViewModel.addToCartImpl(item: MenuItem, customizations: Map<String, String>) {
    dispatch(MainViewModelAction.AddCartItem(item = item, customizations = customizations))
}

internal fun MainViewModel.updateCartItemQuantityImpl(uuid: String, quantity: Int) {
    dispatch(MainViewModelAction.UpdateCartItemQuantity(uuid = uuid, quantity = quantity))
}

internal fun MainViewModel.decrementQuantityImpl(item: MenuItem) {
    dispatch(MainViewModelAction.DecrementByMenuItem(item))
}

internal fun MainViewModel.clearCartImpl() {
    dispatch(MainViewModelAction.ClearCart)
}
