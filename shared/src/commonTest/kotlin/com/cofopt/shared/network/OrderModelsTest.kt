package com.cofopt.shared.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OrderModelsTest {
    @Test
    fun validOrderIsAccepted() {
        assertNull(validOrder().submissionValidationError())
    }

    @Test
    fun invalidNumbersAreRejected() {
        assertEquals("total_invalid", validOrder().copy(total = Double.NaN).submissionValidationError())
        assertEquals(
            "item_price_invalid",
            validOrder().copy(items = listOf(validItem().copy(unitPrice = Double.POSITIVE_INFINITY)))
                .submissionValidationError(),
        )
        assertEquals(
            "item_quantity_invalid",
            validOrder().copy(items = listOf(validItem().copy(quantity = 0))).submissionValidationError(),
        )
    }

    @Test
    fun missingIdentityAndItemsAreRejected() {
        assertEquals("order_id_missing", validOrder().copy(orderId = " ").submissionValidationError())
        assertEquals("items_missing", validOrder().copy(items = emptyList()).submissionValidationError())
    }

    @Test
    fun invalidServerReceiptTimeIsRejected() {
        assertEquals(
            "accepted_at_invalid",
            validOrder().copy(acceptedAtMillis = 0).submissionValidationError(),
        )
    }

    private fun validOrder() = OrderPayload(
        orderId = "OM_1",
        createdAtMillis = 1,
        dineIn = true,
        paymentMethod = "CARD",
        paymentStatus = "PAID",
        status = "PAID",
        total = 2.5,
        items = listOf(validItem()),
    )

    private fun validItem() = OrderItemPayload(
        menuItemId = "dish-1",
        nameEn = "Dish",
        nameZh = "",
        nameNl = "",
        quantity = 1,
        unitPrice = 2.5,
    )
}
