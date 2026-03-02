package com.cofopt.orderingmachine

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.isUnspecified
import androidx.compose.ui.unit.sp

internal fun TextStyle.scale(factor: Float): TextStyle {
    val fs = if (fontSize.isUnspecified) fontSize else (fontSize.value * factor).sp
    val lh = if (lineHeight.isUnspecified) lineHeight else (lineHeight.value * factor).sp
    return copy(fontSize = fs, lineHeight = lh)
}
