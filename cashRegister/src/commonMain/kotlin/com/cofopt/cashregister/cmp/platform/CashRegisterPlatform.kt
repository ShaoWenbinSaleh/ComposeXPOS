package com.cofopt.cashregister.cmp.platform

import com.cofopt.cashregister.menu.DishState
import com.cofopt.cashregister.network.OrderPayload
import kotlinx.coroutines.flow.StateFlow

expect object CashRegisterPlatform {
    val dishes: StateFlow<List<DishState>>
    val orders: StateFlow<List<OrderPayload>>
    val todayOrders: StateFlow<List<OrderPayload>>
    val archivedOrders: StateFlow<List<OrderPayload>>

    fun addOrder(order: OrderPayload)
    fun removeOrder(orderId: String)
    fun clearOrders()
    fun updateOrderPaymentMethod(orderId: String, paymentMethod: String)
    fun updateOrderPaymentStatus(orderId: String, status: String)

    fun updateDishPrice(id: String, priceEur: Double)
    fun updateDishDiscountedPrice(id: String, discountedPrice: Double)
    fun updateDishSoldOut(id: String, soldOut: Boolean)
    fun upsertDish(dish: DishState)
    fun deleteDish(id: String)
    fun setDishImageBase64(id: String, imageBase64: String?)

    fun playAlertSound()
    fun printReceipt(order: OrderPayload)
    fun printOrder(order: OrderPayload)
    fun printKitchen(order: OrderPayload)
}
