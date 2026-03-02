package com.cofopt.cashregister.cmp.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cofopt.cashregister.cmp.components.EmptyStateText
import com.cofopt.cashregister.cmp.components.ScreenHeader
import com.cofopt.cashregister.cmp.utils.formatMoney
import com.cofopt.cashregister.network.OrderPayload
import com.cofopt.cashregister.utils.LocalLanguage
import com.cofopt.cashregister.utils.localizedName
import com.cofopt.cashregister.utils.tr

@Composable
fun OrdersListScreen(
    title: String,
    subtitle: String?,
    orders: List<OrderPayload>,
    onClear: () -> Unit
) {
    val language = LocalLanguage.current
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ScreenHeader(
                title = title,
                subtitle = subtitle
            ) {
                Button(onClick = onClear) {
                    Text(tr("Clear", "清空", "Wissen"))
                }
            }

            if (orders.isEmpty()) {
                EmptyStateText(text = tr("No orders", "暂无订单", "Geen bestellingen"))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(orders, key = { it.orderId }) { o ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "#${o.orderId.takeLast(6)}  ${if (o.dineIn) tr(
                                            "Dine in",
                                            "堂食",
                                            "Hier eten"
                                        ) else tr("Takeaway", "打包", "Meenemen")
                                        }",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "€ ${formatMoney(o.total)}",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Text(
                                    text = "${o.source} · ${o.paymentMethod}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFF4B4F55)
                                )

                                Spacer(modifier = Modifier.height(2.dp))

                                o.items.forEach { item ->
                                    Text(
                                        text = "${item.quantity}x ${item.localizedName(language)}  (€ ${formatMoney(item.unitPrice)})",
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
