package com.cofopt.shared.network

import kotlinx.serialization.Serializable

@Serializable
data class CustomizationPrintLinePayload(
    val titleEn: String,
    val titleZh: String,
    val valueEn: String,
    val valueZh: String,
    val valueNl: String = "",
)

@Serializable
data class OrderItemPayload(
    val menuItemId: String,
    val nameEn: String,
    val nameZh: String,
    val nameNl: String,
    val quantity: Int,
    val unitPrice: Double,
    val customizations: Map<String, String> = emptyMap(),
    val customizationLines: List<CustomizationPrintLinePayload> = emptyList(),
)

@Serializable
data class OrderPayload(
    val orderId: String,
    val createdAtMillis: Long,
    val source: String = "KIOSK",
    val deviceName: String = "",
    val callNumber: Int? = null,
    val dineIn: Boolean,
    val paymentMethod: String,
    val paymentStatus: String? = null,
    val status: String = "UNPAID",
    val total: Double,
    val items: List<OrderItemPayload>,
)
