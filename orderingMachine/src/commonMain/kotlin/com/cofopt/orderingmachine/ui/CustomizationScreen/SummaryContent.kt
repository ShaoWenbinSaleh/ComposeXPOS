package com.cofopt.orderingmachine.ui.CustomizationScreen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cofopt.orderingmachine.CachedAssetImage
import com.cofopt.orderingmachine.cmp.EmojiVisual
import com.cofopt.orderingmachine.localizedDescription
import com.cofopt.orderingmachine.tr

@Composable
fun SummaryContent(
    language: com.cofopt.orderingmachine.Language,
    steps: List<Step>,
    selections: Map<String, String>,
    selectionLabelFor: (Step) -> String?,
    selectionEmojiFor: (Step) -> String?,
    onEdit: (Int) -> Unit,
    item: com.cofopt.orderingmachine.MenuItem,
    quantity: Int,
    compact: Boolean,
    onQuantityChange: (Int) -> Unit,
    onAddToCart: () -> Unit,
    onCancel: () -> Unit
) {
    val desc = item.localizedDescription(language)
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = tr(
                language,
                "Summary",
                "小结",
                "Samenvatting"
            ),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(steps.size) { idx ->
                val step = steps[idx]
                val label = selectionLabelFor(step)
                val emoji = selectionEmojiFor(step)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = tr(
                                language,
                                step.titleEn,
                                step.titleZh,
                                step.titleNl
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (emoji != null) {
                                    EmojiVisual(
                                        emoji = emoji,
                                        contentDescription = label,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Text(
                                    text = label ?: tr(
                                        language,
                                        "Pending",
                                        "待选择",
                                        "In afwachting"
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (label == null) Color.Gray else Color.Unspecified
                                )
                            }
                            OutlinedButton(onClick = { onEdit(idx) }) {
                                Text(
                                    tr(
                                        language,
                                        "Edit",
                                        "编辑",
                                        "Bewerken"
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { onQuantityChange((quantity - 1).coerceAtLeast(1)) },
                    enabled = quantity > 1,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(if (compact) 48.dp else 60.dp)
                ) {
                    Text(text = "-")
                }
                Spacer(modifier = Modifier.width(if (compact) 10.dp else 16.dp))
                Text(
                    text = quantity.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(if (compact) 10.dp else 16.dp))
                OutlinedButton(
                    onClick = { onQuantityChange(quantity + 1) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(if (compact) 48.dp else 60.dp)
                ) {
                    Text(text = "+")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    enabled = true,
                    modifier = Modifier
                        .weight(1f)
                        .height(if (compact) 56.dp else 72.dp),
                    shape = RoundedCornerShape(14.dp),
                    interactionSource = remember { MutableInteractionSource() } // Disable ripple
                ) {
                    Text(text = tr(
                        language,
                        "Cancel",
                        "取消",
                        "Annuleren"
                    ), fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Button(
                    onClick = onAddToCart,
                    modifier = Modifier
                        .weight(1f)
                        .height(if (compact) 56.dp else 72.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(text = tr(
                        language,
                        "Add to cart",
                        "加入购物车",
                        "Toevoegen"
                    ), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
