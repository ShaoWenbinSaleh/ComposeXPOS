package com.cofopt.orderingmachine.ui.OrderingScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.Color
import com.cofopt.orderingmachine.CachedAssetImage
import com.cofopt.orderingmachine.CartItem
import com.cofopt.orderingmachine.EuAllergen
import com.cofopt.orderingmachine.formatEuroAmount
import com.cofopt.orderingmachine.Language
import com.cofopt.orderingmachine.MenuItem
import com.cofopt.orderingmachine.localizedName

@Composable
internal fun MenuGrid(
    language: Language,
    menu: List<MenuItem>,
    cartItems: List<CartItem>,
    compact: Boolean,
    onOpen: (MenuItem, Rect) -> Unit,
    modifier: Modifier = Modifier,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState = rememberLazyGridState()
) {
    val columns = if (compact) 1 else 2
    val spacing = if (compact) 12.dp else 24.dp
    val contentPadding = if (compact) PaddingValues(vertical = 12.dp, horizontal = 8.dp) else PaddingValues(vertical = 24.dp, horizontal = 8.dp)

    LazyVerticalGrid(
        state = gridState,
        modifier = modifier.fillMaxSize(),
        columns = GridCells.Fixed(columns),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalArrangement = Arrangement.spacedBy(spacing)
    ) {
        items(menu, key = { it.id }) { item ->
            val quantity = cartItems.filter { it.menuItem.id == item.id }.sumOf { it.quantity }

            MenuCard(
                language = language,
                item = item,
                quantity = quantity,
                onOpen = { bounds -> onOpen(item, bounds) }
            )
        }
    }
}

@Composable
private fun MenuCard(
    language: Language,
    item: MenuItem,
    quantity: Int,
    onOpen: (Rect) -> Unit
) {
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val baseModifier = Modifier
        .fillMaxWidth()
        .aspectRatio(1f)
        .onGloballyPositioned { coords ->
            bounds = coords.boundsInRoot()
        }
    Card(
        modifier = baseModifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        onClick = { if (!item.soldOut) onOpen(bounds) }) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CachedAssetImage(
                assetPath = item.imagePath ?: "images/food_${item.id}.png",
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 8.dp),
                contentScale = ContentScale.FillWidth
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(48.dp), // Fixed height for 2 lines
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = item.localizedName(language),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(0.8f),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AllergenIcons(allergens = item.allergens)
                Text(
                    text = if (item.soldOut) "SOLD OUT" else "€${formatEuroAmount(item.price)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE53935)
                )
            }
        }
    }
}

@Composable
internal fun AllergenIcons(allergens: Set<EuAllergen>) {
    if (allergens.isEmpty()) return
    val sorted = allergens.toList().sortedBy { it.ordinal }
    val max = 4
    val visible = sorted.take(max)
    val extra = (sorted.size - visible.size).coerceAtLeast(0)

    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        visible.forEach { a ->
            AllergenIcon(allergen = a)
        }
        if (extra > 0) {
            Text(
                text = "...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AllergenIcon(allergen: EuAllergen) {
    val assetPath = "images/menu/allergens/${allergenAssetName(allergen)}.png"
    Box(
        modifier = Modifier
            .size(18.dp)
    ) {
        CachedAssetImage(
            assetPath = assetPath,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
    }
}

internal fun allergenAssetName(a: EuAllergen): String {
    return when (a) {
        EuAllergen.EGGS -> "eggs"
        EuAllergen.GLUTEN -> "gluten"
        EuAllergen.LUPIN -> "lupin"
        EuAllergen.MILK -> "milk"
        EuAllergen.MUSTARD -> "mustard"
        EuAllergen.NUTS -> "nuts"
        EuAllergen.PEANUTS -> "peanuts"
        EuAllergen.CRUSTACEANS -> "crustaceans"
        EuAllergen.CELERY -> "celery"
        EuAllergen.SESAME_SEEDS -> "sesame"
        EuAllergen.SOYBEANS -> "soybeans"
        EuAllergen.FISH -> "fish"
        EuAllergen.MOLLUSCS -> "molluscs"
        EuAllergen.SULPHITES -> "sulphites"
    }
}
