package com.cofopt.orderingmachine.ui.OrderingScreen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cofopt.orderingmachine.CachedAssetImage
import com.cofopt.orderingmachine.EuAllergen
import com.cofopt.orderingmachine.formatEuroAmount
import com.cofopt.orderingmachine.Language
import com.cofopt.orderingmachine.MenuCategory
import com.cofopt.orderingmachine.MenuItem
import com.cofopt.orderingmachine.localizedName
import com.cofopt.orderingmachine.tr

private const val SPECIAL_OPT_VEGAN = "special_vegan"
private const val SPECIAL_OPT_SAUCE = "special_sauce"
private const val SPECIAL_OPT_DRINK = "special_drink"

private const val SPECIAL_VAL_VEGAN_MEAT = "meat"
private const val SPECIAL_VAL_SAUCE_KETCHUP = "ketchup"
private const val SPECIAL_VAL_DRINK_1 = "drink1"

internal fun buildDefaultSelectionsForItem(item: MenuItem): Map<String, String> {
    val base = if (item.customizations.isNotEmpty()) {
        item.customizations.associate { option ->
            option.id to option.choices.first().id
        }
    } else {
        emptyMap()
    }

    val extra = buildMap {
        if (item.chooseVegan) put(SPECIAL_OPT_VEGAN, SPECIAL_VAL_VEGAN_MEAT)
        if (item.chooseSource) put(SPECIAL_OPT_SAUCE, SPECIAL_VAL_SAUCE_KETCHUP)
        if (item.chooseDrink) put(SPECIAL_OPT_DRINK, SPECIAL_VAL_DRINK_1)
    }

    return base + extra
}

@Composable
internal fun MenuItemBottomSheet(
    language: Language,
    item: MenuItem,
    quantity: Int,
    selections: Map<String, String>,
    onQuantityChange: (Int) -> Unit,
    onSelectCustomization: (String, String) -> Unit,
    onCancel: () -> Unit,
    onAddToCart: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp),
                contentAlignment = Alignment.Center
            ) {
                CachedAssetImage(
                    assetPath = item.imagePath ?: "images/food_${item.id}.png",
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.localizedName(language),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "€ ${formatEuroAmount(item.price)}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE53935)
                )
            }
        }

        // Add free items notice for Noodles and Main course categories
        if (item.category == MenuCategory.NOODLES || item.category == MenuCategory.MAIN_COURSE) {
            item {
                Text(
                    text = tr(
                        language,
                        "Comes with one free salad and one free soja egg. Please take them at the buffet.",
                        "赠送一份小菜和一个茶叶蛋。请到吧台自取。",
                        "Wordt geserveerd met één gratis salade en één gratis soja-ei.U kunt deze zelf nemen bij het buffet.",
                        ja = "サラダ1つと味付け卵1つが無料で付きます。ビュッフェからお取りください。",
                        tr = "Bir ücretsiz salata ve bir ücretsiz soya yumurtası dahildir. Lütfen büfeden alınız."
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }

        if (item.allergens.isNotEmpty()) {
            item {
                AllergenLabelRow(language = language, allergens = item.allergens)
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(text = tr(language, "Cancel", "取消", "Annuleren", ja = "キャンセル", tr = "İptal"), fontWeight = FontWeight.Bold)
                }

                Row(
                    modifier = Modifier
                        .weight(1f),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { onQuantityChange(quantity - 1) },
                        enabled = quantity > 1,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text(text = "-")
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = quantity.toString(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    OutlinedButton(
                        onClick = { onQuantityChange(quantity + 1) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text(text = "+")
                    }
                }

                Button(
                    onClick = onAddToCart,
                    enabled = !item.soldOut,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(text = tr(language, "Add to cart", "加入购物车", "Toevoegen", ja = "カートに追加", tr = "Sepete ekle"), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun SpecialChoiceButton(
    language: Language,
    labelEn: String,
    labelZh: String,
    labelNl: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline

    OutlinedButton(
        modifier = modifier.height(56.dp),
        onClick = onClick,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        border = BorderStroke(1.dp, borderColor),
        shape = RoundedCornerShape(16.dp)
    ) {
        Text(
            text = tr(language, labelEn, labelZh, labelNl),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun AllergenLabelRow(language: Language, allergens: Set<EuAllergen>) {
    if (allergens.isEmpty()) return

    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
                text = tr(language, "Allergens:", "过敏原：", "Allergenen:", ja = "アレルゲン:", tr = "Alerjenler:"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            
            allergens.sortedBy { it.ordinal }.forEach { allergen ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    CachedAssetImage(
                        assetPath = "images/menu/allergens/${allergenAssetName(allergen)}.png",
                        contentDescription = null,
                        modifier = Modifier
                            .size(32.dp),
                        contentScale = ContentScale.Fit
                    )
                    Text(
                        text = allergen.localizedName(language),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
    }
}
