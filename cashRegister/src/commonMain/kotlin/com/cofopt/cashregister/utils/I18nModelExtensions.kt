package com.cofopt.cashregister.utils

import com.cofopt.cashregister.network.CustomizationPrintLinePayload
import com.cofopt.cashregister.network.OrderItemPayload

fun OrderItemPayload.localizedName(language: Language): String {
    return tr(language, nameEn, nameZh, nameNl).ifBlank { nameEn }
}

fun CustomizationPrintLinePayload.localizedValue(language: Language): String {
    return tr(language, valueEn, valueZh, valueNl).ifBlank { valueEn }
}
