package com.cofopt.cashregister.cmp.utils

import kotlin.math.absoluteValue
import kotlin.math.roundToLong

fun formatMoney(value: Double): String {
    val scaled = (value * 100.0).roundToLong()
    val absScaled = scaled.absoluteValue
    val integerPart = absScaled / 100
    val fractionPart = (absScaled % 100).toString().padStart(2, '0')
    val sign = if (scaled < 0) "-" else ""
    return "$sign$integerPart.$fractionPart"
}
