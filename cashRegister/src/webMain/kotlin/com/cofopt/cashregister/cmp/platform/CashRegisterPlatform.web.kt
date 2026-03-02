package com.cofopt.cashregister.cmp.platform

import com.cofopt.cashregister.cmp.utils.nowMillis
import com.cofopt.cashregister.cmp.utils.startOfTodayMillis
import com.cofopt.cashregister.menu.DishState
import com.cofopt.cashregister.menu.MenuSeed
import com.cofopt.cashregister.network.OrderItemPayload
import com.cofopt.cashregister.network.OrderPayload
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

actual object CashRegisterPlatform {
    private val seedDishes: List<DishState> = MenuSeed.dishes
    private val dishesState = MutableStateFlow(seedDishes)

    private val demoMain = seedDishes.firstOrNull()
    private val demoDrink = seedDishes.firstOrNull { !it.kitchenPrint } ?: demoMain

    private val allOrdersState = MutableStateFlow(
        if (demoMain == null) {
            emptyList()
        } else {
            listOf(
                OrderPayload(
                    orderId = "WEB-001",
                    createdAtMillis = nowMillis(),
                    source = "KIOSK",
                    deviceName = "WEB-KIOSK",
                    callNumber = 21,
                    dineIn = true,
                    paymentMethod = "CARD",
                    status = "UNPAID",
                    total = (demoMain.priceEur + (demoDrink?.priceEur ?: 0.0)),
                    items = listOfNotNull(
                        OrderItemPayload(
                            menuItemId = demoMain.id,
                            nameEn = demoMain.nameEn,
                            nameZh = demoMain.nameZh,
                            nameNl = demoMain.nameNl,
                            quantity = 1,
                            unitPrice = demoMain.priceEur
                        ),
                        demoDrink?.let {
                            OrderItemPayload(
                                menuItemId = it.id,
                                nameEn = it.nameEn,
                                nameZh = it.nameZh,
                                nameNl = it.nameNl,
                                quantity = 1,
                                unitPrice = it.priceEur
                            )
                        }
                    )
                )
            )
        }
    )

    private val todayOrdersState = MutableStateFlow<List<OrderPayload>>(emptyList())
    private val archivedOrdersState = MutableStateFlow<List<OrderPayload>>(emptyList())

    init {
        refreshSplitStates()
    }

    actual val dishes: StateFlow<List<DishState>> = dishesState
    actual val orders: StateFlow<List<OrderPayload>> = allOrdersState
    actual val todayOrders: StateFlow<List<OrderPayload>> = todayOrdersState
    actual val archivedOrders: StateFlow<List<OrderPayload>> = archivedOrdersState

    actual fun addOrder(order: OrderPayload) {
        allOrdersState.value = (listOf(order) + allOrdersState.value).distinctBy { it.orderId }
        refreshSplitStates()
    }

    actual fun removeOrder(orderId: String) {
        allOrdersState.value = allOrdersState.value.filterNot { it.orderId == orderId }
        refreshSplitStates()
    }

    actual fun clearOrders() {
        allOrdersState.value = emptyList()
        refreshSplitStates()
    }

    actual fun updateOrderPaymentMethod(orderId: String, paymentMethod: String) {
        allOrdersState.value = allOrdersState.value.map {
            if (it.orderId == orderId) it.copy(paymentMethod = paymentMethod) else it
        }
        refreshSplitStates()
    }

    actual fun updateOrderPaymentStatus(orderId: String, status: String) {
        allOrdersState.value = allOrdersState.value.map {
            if (it.orderId == orderId) it.copy(status = status) else it
        }
        refreshSplitStates()
    }

    actual fun updateDishPrice(id: String, priceEur: Double) {
        dishesState.value = dishesState.value.map { if (it.id == id) it.copy(priceEur = priceEur) else it }
    }

    actual fun updateDishDiscountedPrice(id: String, discountedPrice: Double) {
        dishesState.value = dishesState.value.map { if (it.id == id) it.copy(discountedPrice = discountedPrice) else it }
    }

    actual fun updateDishSoldOut(id: String, soldOut: Boolean) {
        dishesState.value = dishesState.value.map { if (it.id == id) it.copy(soldOut = soldOut) else it }
    }

    actual fun upsertDish(dish: DishState) {
        val existingIndex = dishesState.value.indexOfFirst { it.id == dish.id }
        dishesState.value = if (existingIndex >= 0) {
            dishesState.value.map { if (it.id == dish.id) dish else it }
        } else {
            dishesState.value + dish
        }
    }

    actual fun deleteDish(id: String) {
        dishesState.value = dishesState.value.filterNot { it.id == id }
    }

    actual fun setDishImageBase64(id: String, imageBase64: String?) {
        dishesState.value = dishesState.value.map { if (it.id == id) it.copy(imageBase64 = imageBase64) else it }
    }

    actual fun playAlertSound() {
        // Web preview: no device sound integration.
    }

    actual fun printReceipt(order: OrderPayload) {
        // Web preview: printing bridge is intentionally a no-op.
    }

    actual fun printOrder(order: OrderPayload) {
        // Web preview: printing bridge is intentionally a no-op.
    }

    actual fun printKitchen(order: OrderPayload) {
        // Web preview: printing bridge is intentionally a no-op.
    }

    private fun refreshSplitStates() {
        val now = nowMillis()
        val todayStart = startOfTodayMillis(now)
        val all = allOrdersState.value.sortedByDescending { it.createdAtMillis }
        todayOrdersState.value = all.filter { it.createdAtMillis >= todayStart }
        archivedOrdersState.value = all.filter { it.createdAtMillis < todayStart }
    }
}
