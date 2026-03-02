package com.cofopt.orderingmachine.ui.OrderingScreen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cofopt.orderingmachine.CartItem
import com.cofopt.orderingmachine.formatEuroAmount
import com.cofopt.orderingmachine.Language
import com.cofopt.orderingmachine.tr
import com.cofopt.orderingmachine.ui.common.dialog.CommonChoiceDialog

@Composable
internal fun BottomOrderBar(
    language: Language,
    dineIn: Boolean,
    cartItems: List<CartItem>,
    total: Double,
    compact: Boolean,
    onReorder: () -> Unit,
    onMyOrder: () -> Unit,
    onPay: () -> Unit
) {
    val itemCount = cartItems.sumOf { it.quantity }
    val hasCart = itemCount > 0

    var showReorderConfirm by remember { mutableStateOf(false) }

    val modeText = if (dineIn) {
        tr(language, "Dine in", "堂食", "Hier eten", ja = "店内飲食", tr = "Restoranda")
    } else {
        tr(language, "Takeaway", "打包", "Meenemen", ja = "お持ち帰り", tr = "Paket servis")
    }

    val myOrderTitle =
        "${tr(language, "My Order", "我的订单", "Mijn bestelling", ja = "ご注文", tr = "Siparişim")} - $modeText"

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primary)
                .padding(horizontal = if (compact) 12.dp else 18.dp, vertical = if (compact) 8.dp else 12.dp)
        ) {
            Text(
                text = myOrderTitle,
                color = Color.White,
                style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            shape = RoundedCornerShape(0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(if (compact) 10.dp else 16.dp),
                verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp)
            ) {
                if (hasCart) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (compact) 68.dp else 84.dp)
                            .clickable(onClick = onMyOrder),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = if (compact) 10.dp else 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)
                            ) {
                                Text(
                                    text = "€${formatEuroAmount(total)}",
                                    style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFE53935)
                                )
                                Text(
                                    text = "|",
                                    style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                                    color = Color(0xFF8A8F96)
                                )
                                Text(
                                    text = tr(
                                        language,
                                        "Number of Items: $itemCount",
                                        "商品数量: $itemCount",
                                        "Aantal items: $itemCount",
                                        ja = "商品数: $itemCount",
                                        tr = "Ürün sayısı: $itemCount"
                                    ),
                                    style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                                    color = Color(0xFF2B2F33)
                                )
                            }

                            Text(
                                text = tr(
                                    language,
                                    "My Order »",
                                    "我的订单 »",
                                    "Mijn bestelling »",
                                    ja = "ご注文 »",
                                    tr = "Siparişim »"
                                ),
                                style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                } else {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (compact) 68.dp else 84.dp)
                            .clickable(onClick = onMyOrder),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                        border = BorderStroke(2.dp, Color(0xFFE0E0E0))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = if (compact) 10.dp else 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tr(language, "Empty Cart", "购物车为空", "Winkelwagen leeg", ja = "カートは空です", tr = "Sepet boş"),
                                style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF8A8F96)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { showReorderConfirm = true },
                        modifier = Modifier
                            .weight(0.35f)
                            .height(if (compact) 56.dp else 72.dp),
                        enabled = true,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                        border = BorderStroke(2.dp, Color(0xFFE53935))
                    ) {
                        Text(
                            text = tr(language, "Reorder", "重来", "Reorder", ja = "最初から", tr = "Yeniden başla"),
                            style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE53935)
                        )
                    }

                    Button(
                        onClick = onPay,
                        enabled = hasCart && total > 0,
                        modifier = Modifier
                            .weight(0.65f)
                            .height(if (compact) 56.dp else 72.dp),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = tr(language, "Pay", "支付", "Betalen", ja = "支払う", tr = "Öde"),
                                fontWeight = FontWeight.Bold,
                                style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall
                            )
                            Text(
                                text = ">",
                                fontWeight = FontWeight.Bold,
                                style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall
                            )
                        }
                    }
                }

                if (showReorderConfirm) {
                    CommonChoiceDialog(
                        onDismissRequest = { showReorderConfirm = false },
                        onConfirm = {
                            onReorder()
                        },
                        onDismiss = {
                            // Just dismiss, no action needed
                        },
                        title = tr(language, "Confirm", "确认", "Bevestigen", ja = "確認", tr = "Onay"),
                        text = tr(
                            language,
                            "Clear cart and return to home?",
                            "是否清空购物车并返回主页？",
                            "Winkelwagen legen en terug naar start?",
                            ja = "カートを空にしてホームに戻りますか？",
                            tr = "Sepet temizlenip ana sayfaya dönülsün mü?"
                        ),
                        confirmText = tr(language, "Yes", "确认", "Ja", ja = "はい", tr = "Evet"),
                        dismissText = tr(language, "Cancel", "取消", "Annuleren", ja = "キャンセル", tr = "İptal")
                    )
                }
            }
        }
    }
}
