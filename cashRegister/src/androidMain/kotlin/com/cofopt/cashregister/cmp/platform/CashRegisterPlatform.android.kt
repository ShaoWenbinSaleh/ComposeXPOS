package com.cofopt.cashregister.cmp.platform

import com.cofopt.cashregister.menu.DishState
import com.cofopt.cashregister.menu.DishesRepository
import com.cofopt.cashregister.network.OrderPayload
import com.cofopt.cashregister.network.OrdersRepository
import kotlinx.coroutines.flow.StateFlow

actual object CashRegisterPlatform {
    actual val dishes: StateFlow<List<DishState>> = DishesRepository.dishes
    actual val orders: StateFlow<List<OrderPayload>> = OrdersRepository.orders
    actual val todayOrders: StateFlow<List<OrderPayload>> = OrdersRepository.todayOrders
    actual val archivedOrders: StateFlow<List<OrderPayload>> = OrdersRepository.archivedOrders

    actual fun addOrder(order: OrderPayload) {
        OrdersRepository.add(order)
    }

    actual fun removeOrder(orderId: String) {
        OrdersRepository.remove(orderId)
    }

    actual fun clearOrders() {
        OrdersRepository.clear()
    }

    actual fun updateOrderPaymentMethod(orderId: String, paymentMethod: String) {
        OrdersRepository.updatePaymentMethod(orderId, paymentMethod)
    }

    actual fun updateOrderPaymentStatus(orderId: String, status: String) {
        OrdersRepository.updatePaymentStatus(orderId, status)
    }

    actual fun updateDishPrice(id: String, priceEur: Double) {
        DishesRepository.updatePriceEur(id, priceEur)
    }

    actual fun updateDishDiscountedPrice(id: String, discountedPrice: Double) {
        DishesRepository.updateDiscountedPrice(id, discountedPrice)
    }

    actual fun updateDishSoldOut(id: String, soldOut: Boolean) {
        DishesRepository.updateSoldOut(id, soldOut)
    }

    actual fun upsertDish(dish: DishState) {
        DishesRepository.upsertDish(dish)
    }

    actual fun deleteDish(id: String) {
        DishesRepository.deleteDish(id)
    }

    actual fun setDishImageBase64(id: String, imageBase64: String?) {
        DishesRepository.setDishImageBase64(id, imageBase64)
    }

    actual fun playAlertSound() {
        // Optional platform side effect. Screen remains fully functional without audio.
    }

    actual fun printReceipt(order: OrderPayload) {
        OrdersRepository.printReceipt(order)
    }

    actual fun printOrder(order: OrderPayload) {
        OrdersRepository.printOrder(order)
    }

    actual fun printKitchen(order: OrderPayload) {
        OrdersRepository.printKitchen(order)
    }
}
