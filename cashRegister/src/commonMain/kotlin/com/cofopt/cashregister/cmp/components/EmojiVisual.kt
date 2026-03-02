package com.cofopt.cashregister.cmp.components

import androidx.compose.foundation.Image
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import composexpos.cashregister.generated.resources.Res
import composexpos.cashregister.generated.resources.emoji_flag_cn
import composexpos.cashregister.generated.resources.emoji_flag_jp
import composexpos.cashregister.generated.resources.emoji_flag_nl
import composexpos.cashregister.generated.resources.emoji_flag_tr
import composexpos.cashregister.generated.resources.emoji_flag_us
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

private fun emojiToDrawable(emoji: String): DrawableResource? {
    return when (emoji.trim().replace("\uFE0F", "")) {
        "🇺🇸" -> Res.drawable.emoji_flag_us
        "🇨🇳" -> Res.drawable.emoji_flag_cn
        "🇳🇱" -> Res.drawable.emoji_flag_nl
        "🇯🇵" -> Res.drawable.emoji_flag_jp
        "🇹🇷" -> Res.drawable.emoji_flag_tr
        else -> null
    }
}
