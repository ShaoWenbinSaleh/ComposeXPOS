package com.cofopt.orderingmachine.network

import kotlinx.serialization.Serializable

@Serializable
data class CashRegisterOrderItemPayload(
    val menuItemId: String,
    val nameEn: String,
    val nameZh: String,
    val nameNl: String,
    val quantity: Int,
    val unitPrice: Double,
    val customizations: Map<String, String> = emptyMap(),
    val customizationLines: List<com.cofopt.orderingmachine.network.CustomizationPrintLinePayload> = emptyList()
)

@Serializable
data class CashRegisterOrderPayload(
    val orderId: String,
    val createdAtMillis: Long,
    val source: String,
    val deviceName: String,
    val dineIn: Boolean,
    val paymentMethod: String,
    val paymentStatus: String = "PAID",
    // Keep the legacy field during the protocol transition. Current servers
    // prefer paymentStatus; older servers still read status.
    val status: String = paymentStatus,
    val callNumber: Int? = null,
    val total: Double,
    val items: List<CashRegisterOrderItemPayload>
)
