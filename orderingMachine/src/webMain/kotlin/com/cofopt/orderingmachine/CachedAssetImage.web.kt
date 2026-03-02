package com.cofopt.orderingmachine

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import composexpos.orderingmachine.generated.resources.Res
import composexpos.orderingmachine.generated.resources.allDrawableResources
import org.jetbrains.compose.resources.painterResource

@Composable
actual fun CachedAssetImage(
    assetPath: String,
    contentDescription: String?,
    modifier: Modifier,
    contentScale: ContentScale
) {
    val drawableName = remember(assetPath) { assetPathToDrawableName(assetPath) }
    val drawable = remember(drawableName) { Res.allDrawableResources[drawableName] }

    if (drawable != null) {
        Image(
            painter = painterResource(drawable),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale
        )
    } else {
        Box(
            modifier = modifier
                .background(Color(0xFFE6E6E6))
        ) {
            Box(modifier = Modifier.fillMaxSize())
        }
    }
}

private fun assetPathToDrawableName(assetPath: String): String {
    val noQuery = assetPath.substringBefore('?').substringBefore('#')
    val withoutPrefix = noQuery.removePrefix("images/")
    val noExt = withoutPrefix.substringBeforeLast('.', withoutPrefix)
    val normalized = noExt
        .trim()
        .lowercase()
        .replace(Regex("[^a-z0-9_]+"), "_")
        .replace(Regex("_+"), "_")
        .trim('_')
    return if (normalized.firstOrNull()?.isDigit() == true) {
        "img_$normalized"
    } else {
        normalized
    }
}
