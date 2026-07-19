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
    /** Server receipt time used for operational day bucketing when device clocks disagree. */
    val acceptedAtMillis: Long? = null,
    /** Server-managed identity of the original LAN submission for safe idempotent retries. */
    val submissionFingerprint: String? = null,
)

/** Returns a stable machine-readable reason when an order cannot be accepted over LAN. */
fun OrderPayload.submissionValidationError(): String? {
    if (orderId.isBlank()) return "order_id_missing"
    if (orderId.length > 128) return "order_id_too_long"
    if (createdAtMillis <= 0L) return "created_at_invalid"
    if (acceptedAtMillis != null && acceptedAtMillis <= 0L) return "accepted_at_invalid"
    if (source.isBlank() || source.length > 64) return "source_invalid"
    if (deviceName.length > 256) return "device_name_too_long"
    if (callNumber != null && callNumber <= 0) return "call_number_invalid"
    if (paymentMethod.isBlank() || paymentMethod.length > 32) return "payment_method_invalid"
    if (status.isBlank() || status.length > 32) return "status_invalid"
    if (paymentStatus != null && (paymentStatus.isBlank() || paymentStatus.length > 32)) {
        return "payment_status_invalid"
    }
    if (!total.isFinite() || total < 0.0) return "total_invalid"
    if (items.isEmpty()) return "items_missing"
    if (items.size > 500) return "too_many_items"

    items.forEach { item ->
        if (item.menuItemId.isBlank() || item.menuItemId.length > 128) return "item_id_invalid"
        if (
            item.nameEn.isBlank() &&
            item.nameZh.isBlank() &&
            item.nameNl.isBlank()
        ) {
            return "item_name_missing"
        }
        if (item.quantity !in 1..999) return "item_quantity_invalid"
        if (!item.unitPrice.isFinite() || item.unitPrice < 0.0) return "item_price_invalid"
    }

    return null
}
