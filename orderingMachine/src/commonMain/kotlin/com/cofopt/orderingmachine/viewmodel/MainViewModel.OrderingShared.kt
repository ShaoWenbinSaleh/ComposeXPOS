package com.cofopt.orderingmachine.viewmodel

import com.cofopt.orderingmachine.currentTimeMillis
import com.cofopt.orderingmachine.network.CustomizationPrintLinePayload

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

internal fun fallbackCallNumberImpl(): Int {
    return (currentTimeMillis() % 10000).toInt()
}

internal fun formatFallbackCallNumberImpl(callNumber: Int): String {
    return callNumber.toString().padStart(4, '0')
}
