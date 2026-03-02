package com.cofopt.orderingmachine.ui.OrderingScreen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cofopt.orderingmachine.CartItem
import com.cofopt.orderingmachine.formatEuroAmount
import com.cofopt.orderingmachine.Language
import com.cofopt.orderingmachine.localizedName
import com.cofopt.orderingmachine.tr

@Composable
internal fun OrderSummaryDialog(
    language: Language,
    dineIn: Boolean,
    cartItems: List<CartItem>,
    total: Double,
    onDismiss: () -> Unit,
    onUpdateQuantity: (String, Int) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .fillMaxHeight(0.82f)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { },
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tr(language, "Order Summary", "订单详情", "Besteloverzicht", ja = "注文内容", tr = "Sipariş özeti"),
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (dineIn) tr(
                                    language,
                                    "Dine In",
                                    "堂食",
                                    "Hier eten",
                                    ja = "店内飲食",
                                    tr = "Restoranda"
                                ) else tr(language, "Take Away", "打包", "Meenemen", ja = "お持ち帰り", tr = "Paket servis"),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        TextButton(onClick = onDismiss) {
                            Text(
                                text = tr(language, "Close", "关闭", "Sluiten", ja = "閉じる", tr = "Kapat"),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F8FA)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        if (cartItems.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 14.dp, vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = tr(
                                        language,
                                        "Empty Cart",
                                        "购物车为空",
                                        "Winkelwagen leeg",
                                        ja = "カートは空です",
                                        tr = "Sepet boş"
                                    ),
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF6A7078)
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 14.dp, vertical = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                items(cartItems) { ci ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = ci.menuItem.localizedName(language),
                                                style = MaterialTheme.typography.headlineMedium,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )

                                            if (ci.customizations.isNotEmpty()) {
                                                val customizationText = ci.customizations.mapNotNull { (optionId, choiceId) ->
                                                    val option = ci.menuItem.customizations.find { it.id == optionId }
                                                    val choice = option?.choices?.find { it.id == choiceId }
                                                    choice?.localizedName(language)
                                                }.joinToString(", ")

                                                Text(
                                                    text = customizationText,
                                                    style = MaterialTheme.typography.titleLarge,
                                                    color = Color(0xFF6A7078),
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }

                                            Text(
                                                text = "€ ${formatEuroAmount(ci.menuItem.price * ci.quantity)}",
                                                style = MaterialTheme.typography.headlineSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF2B2F33)
                                            )
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = { onUpdateQuantity(ci.uuid, ci.quantity - 1) },
                                                modifier = Modifier
                                                    .width(64.dp)
                                                    .height(56.dp),
                                                shape = RoundedCornerShape(14.dp),
                                                border = BorderStroke(2.dp, Color(0xFFE53935))
                                            ) {
                                                Text(
                                                    text = "-",
                                                    style = MaterialTheme.typography.headlineLarge,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFFE53935)
                                                )
                                            }

                                            Text(
                                                text = "${ci.quantity}",
                                                style = MaterialTheme.typography.headlineLarge,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.width(44.dp)
                                            )

                                            Button(
                                                onClick = { onUpdateQuantity(ci.uuid, ci.quantity + 1) },
                                                modifier = Modifier
                                                    .width(64.dp)
                                                    .height(56.dp),
                                                shape = RoundedCornerShape(14.dp)
                                            ) {
                                                Text(
                                                    text = "+",
                                                    style = MaterialTheme.typography.headlineLarge,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = tr(language, "Total", "合计", "Totaal", ja = "合計", tr = "Toplam"),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "€ ${formatEuroAmount(total)}",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}
