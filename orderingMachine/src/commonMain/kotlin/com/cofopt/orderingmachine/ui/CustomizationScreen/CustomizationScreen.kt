package com.cofopt.orderingmachine.ui.CustomizationScreen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cofopt.orderingmachine.CachedAssetImage
import com.cofopt.orderingmachine.currentTimeMillis
import com.cofopt.orderingmachine.formatEuroAmount
import com.cofopt.orderingmachine.MenuCategory
import com.cofopt.orderingmachine.cmp.EmojiVisual
import com.cofopt.orderingmachine.localizedDescription
import com.cofopt.orderingmachine.localizedName
import com.cofopt.orderingmachine.tr
import com.cofopt.orderingmachine.ui.OrderingScreen.AllergenLabelRow
import com.cofopt.orderingmachine.ui.common.dialog.CommonChoiceDialog
import kotlinx.coroutines.delay

private const val SPECIAL_OPT_VEGAN = "special_vegan"
private const val SPECIAL_OPT_SAUCE = "special_sauce"
private const val SPECIAL_OPT_DRINK = "special_drink"

private const val SPECIAL_VAL_VEGAN_MEAT = "meat"
private const val SPECIAL_VAL_VEGAN_VEGAN = "vegan"

private const val SPECIAL_VAL_SAUCE_KETCHUP = "ketchup"
private const val SPECIAL_VAL_SAUCE_HOT = "hot_sauce"
private const val SPECIAL_VAL_SAUCE_MAYO = "mayonnaise"

private const val SPECIAL_VAL_DRINK_1 = "drink1"
private const val SPECIAL_VAL_DRINK_2 = "drink2"
private const val SPECIAL_VAL_DRINK_3 = "drink3"

data class Step(
    val key: String,
    val titleEn: String,
    val titleZh: String,
    val titleNl: String
)

private data class EmojiOption(
    val value: String,
    val emoji: String,
    val labelEn: String,
    val labelZh: String,
    val labelNl: String,
    val labelJa: String,
    val labelTr: String
)

@Composable
internal fun CustomizationScreen(
    language: com.cofopt.orderingmachine.Language,
    item: com.cofopt.orderingmachine.MenuItem,
    initialQuantity: Int,
    onBack: () -> Unit,
    onAddToCart: (quantity: Int, selections: Map<String, String>) -> Unit
) {
    var showIdleDialog by remember { mutableStateOf(false) }
    var idleCountdownSec by remember { mutableStateOf(30) }
    var lastInteractionMs by remember { mutableStateOf(currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            val now = currentTimeMillis()
            val elapsedSec = ((now - lastInteractionMs) / 1000).toInt()

            if (elapsedSec >= 30 && !showIdleDialog) {
                showIdleDialog = true
                idleCountdownSec = 30
            }

            if (showIdleDialog) {
                idleCountdownSec--
                if (idleCountdownSec <= 0) {
                    onBack()
                    return@LaunchedEffect
                }
            }
        }
    }

    var quantity by remember { mutableIntStateOf(initialQuantity) }

    val defaultSelections = remember(item) {
        buildMap {
            if (item.chooseVegan) put(SPECIAL_OPT_VEGAN, SPECIAL_VAL_VEGAN_MEAT)
            if (item.chooseSource) put(SPECIAL_OPT_SAUCE, SPECIAL_VAL_SAUCE_KETCHUP)
            if (item.chooseDrink) put(SPECIAL_OPT_DRINK, SPECIAL_VAL_DRINK_1)
        }
    }

    var selections by remember(item.id) { mutableStateOf(defaultSelections) }

    val steps = remember(item) {
        buildList {
            if (item.chooseVegan) add(Step(SPECIAL_OPT_VEGAN, "Protein choice", "蛋白选择", "Eiwitkeuze"))
            if (item.chooseSource) add(Step(SPECIAL_OPT_SAUCE, "Free sauce", "附赠酱料", "Gratis saus"))
            if (item.chooseDrink) add(Step(SPECIAL_OPT_DRINK, "Free drink", "附赠饮料", "Gratis drankje"))
        }
    }

    var currentStepIndex by remember { mutableStateOf(0) }
    var inSummary by remember { mutableStateOf(false) }
    val currentStep = steps.getOrNull(currentStepIndex)

    fun selectionLabelFor(step: Step): String? {
        val value = selections[step.key] ?: return null
        return when (step.key) {
            SPECIAL_OPT_VEGAN -> when (value) {
                SPECIAL_VAL_VEGAN_MEAT -> tr(language, "Meat", "肉类", "Vlees", ja = "肉", tr = "Et")
                SPECIAL_VAL_VEGAN_VEGAN -> tr(language, "Vegan", "纯素", "Vegan", ja = "ヴィーガン", tr = "Vegan")
                else -> null
            }

            SPECIAL_OPT_SAUCE -> when (value) {
                SPECIAL_VAL_SAUCE_KETCHUP -> tr(language, "Ketchup", "番茄酱", "Ketchup", ja = "ケチャップ", tr = "Ketçap")
                SPECIAL_VAL_SAUCE_HOT -> tr(language, "Hot sauce", "辣酱", "Pittige saus", ja = "ホットソース", tr = "Acı sos")
                SPECIAL_VAL_SAUCE_MAYO -> tr(language, "Mayonnaise", "蛋黄酱", "Mayonaise", ja = "マヨネーズ", tr = "Mayonez")
                else -> null
            }

            SPECIAL_OPT_DRINK -> when (value) {
                SPECIAL_VAL_DRINK_1 -> tr(language, "Drink 1", "饮料1", "Drank 1", ja = "ドリンク1", tr = "İçecek 1")
                SPECIAL_VAL_DRINK_2 -> tr(language, "Drink 2", "饮料2", "Drank 2", ja = "ドリンク2", tr = "İçecek 2")
                SPECIAL_VAL_DRINK_3 -> tr(language, "Drink 3", "饮料3", "Drank 3", ja = "ドリンク3", tr = "İçecek 3")
                else -> null
            }

            else -> null
        }
    }

    fun selectionEmojiFor(step: Step): String? {
        val value = selections[step.key] ?: return null
        return when (step.key) {
            SPECIAL_OPT_VEGAN -> when (value) {
                SPECIAL_VAL_VEGAN_MEAT -> "🥩"
                SPECIAL_VAL_VEGAN_VEGAN -> "🌱"
                else -> null
            }

            SPECIAL_OPT_SAUCE -> when (value) {
                SPECIAL_VAL_SAUCE_KETCHUP -> "🍅"
                SPECIAL_VAL_SAUCE_HOT -> "🌶️"
                SPECIAL_VAL_SAUCE_MAYO -> "🥚"
                else -> null
            }

            SPECIAL_OPT_DRINK -> when (value) {
                SPECIAL_VAL_DRINK_1 -> "🥤"
                SPECIAL_VAL_DRINK_2 -> "🧃"
                SPECIAL_VAL_DRINK_3 -> "🍹"
                else -> null
            }

            else -> null
        }
    }

    fun goToStep(index: Int) {
        inSummary = false
        currentStepIndex = index.coerceIn(0, steps.lastIndex)
    }

    fun advanceAfterSelection() {
        if (currentStepIndex < steps.lastIndex) {
            currentStepIndex += 1
        } else {
            inSummary = true
        }
    }

    fun updateSelection(key: String, value: String) {
        lastInteractionMs = currentTimeMillis()
        selections = selections.toMutableMap().apply { put(key, value) }
        advanceAfterSelection()
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isCompact = maxWidth < 900.dp
            val contentPadding = if (isCompact) 10.dp else 16.dp
            val imageWidth = if (isCompact) 140.dp else 200.dp
            val imageHeight = if (isCompact) 110.dp else 150.dp
            val topSpacing = if (isCompact) 8.dp else 12.dp

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                verticalArrangement = Arrangement.spacedBy(topSpacing)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (isCompact) 8.dp else 12.dp)) {
                    Card(
                        modifier = Modifier
                            .size(width = imageWidth, height = imageHeight),
                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                    ) {
                        CachedAssetImage(
                            assetPath = item.imagePath ?: "images/food_${item.id}.png",
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (isCompact) 4.dp else 6.dp)) {
                        Text(
                            text = item.localizedName(language),
                            style = if (isCompact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        val desc = item.localizedDescription(language)
                        if (desc.isNotBlank()) {
                            Text(
                                text = desc,
                                style = MaterialTheme.typography.bodyMedium.copy(color = Color.Gray),
                                maxLines = if (isCompact) 1 else 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = "€ ${formatEuroAmount(item.price)}",
                            style = MaterialTheme.typography.titleLarge.copy(color = Color(0xFFE60000), fontSize = if (isCompact) 18.sp else 22.sp),
                            fontWeight = FontWeight.Bold
                        )
                        if (item.allergens.isNotEmpty()) {
                            AllergenLabelRow(language = language, allergens = item.allergens)
                        }
                        if (!isCompact && (item.category == MenuCategory.NOODLES || item.category == MenuCategory.MAIN_COURSE)) {
                            Text(
                                text = tr(
                                    language,
                                    "Comes with one free salad and one free soja egg. Please take them at the buffet.",
                                    "赠送一份小菜和一个茶叶蛋。请到吧台自取。",
                                    "Wordt geserveerd met één gratis salade en één gratis soja-ei.U kunt deze zelf nemen bij het buffet."
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                }

            Row(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .width(if (isCompact) 150.dp else 220.dp)
                        .fillMaxHeight()
                        .background(Color(0xFFF5F5F5))
                        .padding(vertical = if (isCompact) 8.dp else 12.dp),
                    verticalArrangement = Arrangement.spacedBy(if (isCompact) 8.dp else 12.dp)
                ) {
                    val pendingLabel = tr(language, "Pending", "待选择", "In afwachting", ja = "未選択", tr = "Bekliyor")
                    steps.forEachIndexed { index, step ->
                        val selected = !inSummary && index == currentStepIndex
                        val label = selectionLabelFor(step)
                        val emoji = selectionEmojiFor(step)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = if (isCompact) 8.dp else 12.dp, vertical = if (isCompact) 6.dp else 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(if (isCompact) 6.dp else 8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(if (isCompact) 16.dp else 20.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) Color(0xFF1B8F3F) else Color.White)
                                    .border(2.dp, Color(0xFF1B8F3F), RoundedCornerShape(50))
                            )
                            Column {
                                Text(
                                    text = tr(language, step.titleEn, step.titleZh, step.titleNl),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (emoji != null) {
                                        EmojiVisual(
                                            emoji = emoji,
                                            contentDescription = label,
                                            modifier = Modifier.size(14.dp),
                                            fallbackFontSize = 12.sp,
                                            fallbackColor = Color.Gray
                                        )
                                    }
                                    Text(
                                        text = label ?: pendingLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.Gray,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(start = if (isCompact) 10.dp else 16.dp)
                ) {
                    if (inSummary) {
                        SummaryContent(
                            language = language,
                            steps = steps,
                            selections = selections,
                            selectionLabelFor = ::selectionLabelFor,
                            selectionEmojiFor = ::selectionEmojiFor,
                            onEdit = ::goToStep,
                            item = item,
                            quantity = quantity,
                            compact = isCompact,
                            onQuantityChange = { quantity = it },
                            onAddToCart = { onAddToCart(quantity, selections) },
                            onCancel = { onBack() }
                        )
                    } else {
                        currentStep?.let { step ->
                            Text(
                                text = tr(language, step.titleEn, step.titleZh, step.titleNl),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(if (isCompact) 8.dp else 12.dp))

                            val options = when (step.key) {
                                SPECIAL_OPT_VEGAN -> listOf(
                                    EmojiOption(SPECIAL_VAL_VEGAN_MEAT, "🥩", "Meat", "肉类", "Vlees", "肉", "Et"),
                                    EmojiOption(SPECIAL_VAL_VEGAN_VEGAN, "🌱", "Vegan", "纯素", "Vegan", "ヴィーガン", "Vegan")
                                )

                                SPECIAL_OPT_SAUCE -> listOf(
                                    EmojiOption(SPECIAL_VAL_SAUCE_KETCHUP, "🍅", "Ketchup", "番茄酱", "Ketchup", "ケチャップ", "Ketçap"),
                                    EmojiOption(SPECIAL_VAL_SAUCE_HOT, "🌶️", "Hot sauce", "辣酱", "Pittige saus", "ホットソース", "Acı sos"),
                                    EmojiOption(SPECIAL_VAL_SAUCE_MAYO, "🥚", "Mayonnaise", "蛋黄酱", "Mayonaise", "マヨネーズ", "Mayonez")
                                )

                                SPECIAL_OPT_DRINK -> listOf(
                                    EmojiOption(SPECIAL_VAL_DRINK_1, "🥤", "Drink 1", "饮料1", "Drank 1", "ドリンク1", "İçecek 1"),
                                    EmojiOption(SPECIAL_VAL_DRINK_2, "🧃", "Drink 2", "饮料2", "Drank 2", "ドリンク2", "İçecek 2"),
                                    EmojiOption(SPECIAL_VAL_DRINK_3, "🍹", "Drink 3", "饮料3", "Drank 3", "ドリンク3", "İçecek 3")
                                )

                                else -> emptyList()
                            }

                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(if (isCompact) 8.dp else 12.dp)
                            ) {
                                item {
                                    EmojiOptionGrid(
                                        language = language,
                                        options = options,
                                        selected = selections[step.key],
                                        columns = if (isCompact) 1 else 2,
                                        optionHeight = if (isCompact) 72.dp else 88.dp,
                                        onSelect = { updateSelection(step.key, it) }
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = if (isCompact) 8.dp else 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = {
                                    lastInteractionMs = currentTimeMillis()
                                    if (inSummary) {
                                        inSummary = false
                                        currentStepIndex = steps.lastIndex
                                    } else {
                                        goToStep((currentStepIndex - 1).coerceAtLeast(0))
                                    }
                                },
                                enabled = currentStepIndex > 0 || inSummary,
                                modifier = Modifier.height(if (isCompact) 72.dp else 96.dp)
                            ) {
                                Text(tr(language, "Previous", "上一步", "Vorige", ja = "戻る", tr = "Geri"))
                            }
                            OutlinedButton(
                                onClick = {
                                    lastInteractionMs = currentTimeMillis()
                                    onBack()
                                },
                                modifier = Modifier.height(if (isCompact) 72.dp else 96.dp)
                            ) {
                                Text(tr(language, "Cancel", "取消", "Annuleren", ja = "キャンセル", tr = "İptal"))
                            }
                        }
                    }
                }
            }
        }
    }
    }

    if (showIdleDialog) {
        CommonChoiceDialog(
            onDismissRequest = { },
            onConfirm = {
                showIdleDialog = false
                onBack()
            },
            onDismiss = {
                lastInteractionMs = currentTimeMillis()
                showIdleDialog = false
            },
            title = tr(
                language,
                "No activity detected",
                "长时间未操作",
                "Geen activiteit"
            ),
            text = tr(
                language,
                "Return to home? Auto in ${idleCountdownSec}s.",
                "是否返回主页？${idleCountdownSec}秒后将自动返回。",
                "Terug naar start? Automatisch over ${idleCountdownSec}s."
            ),
            confirmText = tr(language, "Return", "返回", "Terug"),
            dismissText = tr(language, "Continue", "继续", "Doorgaan")
        )
    }
}

@Composable
private fun EmojiOptionGrid(
    language: com.cofopt.orderingmachine.Language,
    options: List<EmojiOption>,
    selected: String?,
    columns: Int,
    optionHeight: androidx.compose.ui.unit.Dp,
    onSelect: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        options.chunked(columns).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                rowItems.forEach { option ->
                    val isSelected = selected == option.value
                    val label = tr(
                        language,
                        option.labelEn,
                        option.labelZh,
                        option.labelNl,
                        ja = option.labelJa,
                        tr = option.labelTr
                    )
                    Button(
                        onClick = { onSelect(option.value) },
                        modifier = Modifier
                            .weight(1f)
                            .height(optionHeight),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSelected) Color(0xFF1E88E5) else Color(0xFF757575)
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            EmojiVisual(
                                emoji = option.emoji,
                                contentDescription = label,
                                modifier = Modifier.size(20.dp),
                                fallbackFontSize = 18.sp,
                                fallbackColor = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = label,
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                repeat(columns - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
