package com.cofopt.orderingmachine.viewmodel

import com.cofopt.orderingmachine.CartItem

internal fun getQuantityFromCartImpl(cartItems: Collection<CartItem>, menuItemId: String): Int {
    return cartItems.filter { it.menuItem.id == menuItemId }.sumOf { it.quantity }
}
