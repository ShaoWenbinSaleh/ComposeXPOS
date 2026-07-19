package com.cofopt.orderingmachine.viewmodel

import com.cofopt.orderingmachine.CartItem
import com.cofopt.orderingmachine.currentEpochMillis
import com.cofopt.orderingmachine.network.CashRegisterOrderItemPayload
import com.cofopt.orderingmachine.network.CashRegisterOrderPayload
import com.cofopt.orderingmachine.network.CustomizationPrintLinePayload
import com.cofopt.orderingmachine.network.DeviceConfig
import com.cofopt.orderingmachine.network.OrderingPlatformContext
import kotlin.random.Random

internal fun customizationLinesForPrintImpl(customizations: Map<String, String>): List<CustomizationPrintLinePayload> {
    if (customizations.isEmpty()) return emptyList()

    val lines = mutableListOf<CustomizationPrintLinePayload>()
    customizations.forEach { (optionId, rawValue) ->
        when (optionId) {
            "special_vegan" -> {
                val v = when (rawValue) {
                    "meat" -> CustomizationPrintLinePayload("Protein", "蛋白选择", "Meat", "肉类")
                    "vegan" -> CustomizationPrintLinePayload("Protein", "蛋白选择", "Vegan", "纯素")
                    else -> null
                }
                if (v != null) lines += v
            }

            "special_sauce" -> {
                val v = when (rawValue) {
                    "ketchup" -> CustomizationPrintLinePayload("Sauce", "酱料", "Ketchup", "番茄酱")
                    "hot_sauce" -> CustomizationPrintLinePayload("Sauce", "酱料", "Hot sauce", "辣酱")
                    "mayonnaise" -> CustomizationPrintLinePayload("Sauce", "酱料", "Mayonnaise", "蛋黄酱")
                    else -> null
                }
                if (v != null) lines += v
            }

            "special_drink" -> {
                val v = when (rawValue) {
                    "drink1" -> CustomizationPrintLinePayload("Free drink", "附赠饮料", "Drink 1", "饮料1")
                    "drink2" -> CustomizationPrintLinePayload("Free drink", "附赠饮料", "Drink 2", "饮料2")
                    "drink3" -> CustomizationPrintLinePayload("Free drink", "附赠饮料", "Drink 3", "饮料3")
                    else -> null
                }
                if (v != null) lines += v
            }

            else -> {
                lines += CustomizationPrintLinePayload(
                    titleEn = optionId,
                    titleZh = optionId,
                    valueEn = rawValue,
                    valueZh = rawValue
                )
            }
        }
    }

    return lines
}

internal fun buildCashRegisterOrderPayloadImpl(
    context: OrderingPlatformContext,
    cartItems: List<CartItem>,
    total: Double,
    dineIn: Boolean,
    paymentMethod: String,
    paymentStatus: String,
    createdAtMillis: Long = currentEpochMillis(),
): CashRegisterOrderPayload {
    val deviceName = DeviceConfig.deviceName(context)
    val deviceSuffix = deviceName
        .filter { it.isLetterOrDigit() }
        .takeLast(12)
        .ifBlank { "device" }
    val nonce = Random.nextInt(100_000, 1_000_000)
    val normalizedMethod = paymentMethod.trim().uppercase()
    val normalizedStatus = paymentStatus.trim().uppercase()

    return CashRegisterOrderPayload(
        orderId = "OM_${deviceSuffix}_${createdAtMillis}_$nonce",
        createdAtMillis = createdAtMillis,
        source = "KIOSK",
        deviceName = deviceName,
        dineIn = dineIn,
        paymentMethod = normalizedMethod,
        paymentStatus = normalizedStatus,
        total = total,
        items = cartItems.map { item ->
            CashRegisterOrderItemPayload(
                menuItemId = item.menuItem.id,
                nameEn = item.menuItem.nameEn,
                nameZh = item.menuItem.nameZh,
                nameNl = item.menuItem.nameNl,
                quantity = item.quantity,
                unitPrice = item.menuItem.price,
                customizations = item.customizations,
                customizationLines = customizationLinesForPrintImpl(item.customizations),
            )
        },
    )
}
