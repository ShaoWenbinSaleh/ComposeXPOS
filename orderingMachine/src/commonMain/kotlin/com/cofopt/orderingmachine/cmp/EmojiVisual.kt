package com.cofopt.orderingmachine.cmp

import androidx.compose.foundation.Image
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import composexpos.orderingmachine.generated.resources.Res
import composexpos.orderingmachine.generated.resources.emoji_bank
import composexpos.orderingmachine.generated.resources.emoji_card
import composexpos.orderingmachine.generated.resources.emoji_cash
import composexpos.orderingmachine.generated.resources.emoji_diamond
import composexpos.orderingmachine.generated.resources.emoji_dine_in
import composexpos.orderingmachine.generated.resources.emoji_drink_1
import composexpos.orderingmachine.generated.resources.emoji_drink_2
import composexpos.orderingmachine.generated.resources.emoji_drink_3
import composexpos.orderingmachine.generated.resources.emoji_flag_cn
import composexpos.orderingmachine.generated.resources.emoji_flag_jp
import composexpos.orderingmachine.generated.resources.emoji_flag_nl
import composexpos.orderingmachine.generated.resources.emoji_flag_tr
import composexpos.orderingmachine.generated.resources.emoji_flag_us
import composexpos.orderingmachine.generated.resources.emoji_globe
import composexpos.orderingmachine.generated.resources.emoji_hot_sauce
import composexpos.orderingmachine.generated.resources.emoji_ketchup
import composexpos.orderingmachine.generated.resources.emoji_mayo
import composexpos.orderingmachine.generated.resources.emoji_meat
import composexpos.orderingmachine.generated.resources.emoji_takeaway
import composexpos.orderingmachine.generated.resources.emoji_vegan
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

@Composable
fun EmojiVisual(
    emoji: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    fallbackFontSize: TextUnit = TextUnit.Unspecified,
    fallbackColor: Color = Color.Unspecified
) {
    val drawable = emojiToDrawable(emoji)
    if (drawable != null) {
        Image(
            painter = painterResource(drawable),
            contentDescription = contentDescription,
            modifier = modifier
        )
        return
    }

    Text(
        text = emoji,
        modifier = modifier,
        color = fallbackColor,
        fontSize = fallbackFontSize,
        textAlign = TextAlign.Center
    )
}

fun emojiToDrawable(emoji: String): DrawableResource? {
    return when (emoji.trim().replace("\uFE0F", "")) {
        "🍽" -> Res.drawable.emoji_dine_in
        "🥡" -> Res.drawable.emoji_takeaway
        "🇺🇸" -> Res.drawable.emoji_flag_us
        "🇨🇳" -> Res.drawable.emoji_flag_cn
        "🇳🇱" -> Res.drawable.emoji_flag_nl
        "🇯🇵" -> Res.drawable.emoji_flag_jp
        "🇹🇷" -> Res.drawable.emoji_flag_tr
        "🌐" -> Res.drawable.emoji_globe
        "🥩" -> Res.drawable.emoji_meat
        "🌱" -> Res.drawable.emoji_vegan
        "🍅" -> Res.drawable.emoji_ketchup
        "🌶" -> Res.drawable.emoji_hot_sauce
        "🥚" -> Res.drawable.emoji_mayo
        "🥤" -> Res.drawable.emoji_drink_1
        "🧃" -> Res.drawable.emoji_drink_2
        "🍹" -> Res.drawable.emoji_drink_3
        "💳" -> Res.drawable.emoji_card
        "💵" -> Res.drawable.emoji_cash
        "💠" -> Res.drawable.emoji_diamond
        "🏦" -> Res.drawable.emoji_bank
        else -> null
    }
}
