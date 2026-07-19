package com.cofopt.orderingmachine.network

import com.cofopt.shared.network.OrderPayload
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class CashRegisterProtocolTest {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun orderingPayloadDecodesWithSharedCashRegisterProtocol() {
        val payload = CashRegisterOrderPayload(
            orderId = "OM_test_1",
            createdAtMillis = 1234L,
            source = "KIOSK",
            deviceName = "ordering-1",
            dineIn = true,
            paymentMethod = "CARD",
            paymentStatus = "PAID",
            total = 3.5,
            items = listOf(
                CashRegisterOrderItemPayload(
                    menuItemId = "dish-1",
                    nameEn = "Dish",
                    nameZh = "菜品",
                    nameNl = "Gerecht",
                    quantity = 1,
                    unitPrice = 3.5,
                )
            ),
        )

        val wireJson = json.encodeToString(CashRegisterOrderPayload.serializer(), payload)
        val decoded = json.decodeFromString<OrderPayload>(wireJson)

        assertEquals("PAID", decoded.paymentStatus)
        assertEquals("PAID", decoded.status)
        assertEquals(payload.orderId, decoded.orderId)
        assertEquals(payload.items.single().menuItemId, decoded.items.single().menuItemId)
    }
}
