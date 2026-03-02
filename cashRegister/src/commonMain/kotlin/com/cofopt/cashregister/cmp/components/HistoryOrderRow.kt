package com.cofopt.cashregister.cmp.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cofopt.cashregister.cmp.utils.formatMoney
import com.cofopt.cashregister.network.OrderPayload
import com.cofopt.cashregister.utils.tr

@Composable
fun HistoryOrderRow(
    order: OrderPayload,
    timeLabel: String,
    onShowDetails: () -> Unit,
    onPrintReceipt: () -> Unit,
    onPrintOrder: () -> Unit,
    onPrintKitchen: () -> Unit
) {
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "#${order.orderId.takeLast(6)}${if (order.callNumber != null) "  " + tr(
                            "Call",
                            "叫号",
                            "Oproepen"
                        ) + ":${order.callNumber}" else ""}  ${if (order.dineIn) tr(
                            "Dine In",
                            "堂食",
                            "Eat in"
                        ) else tr("Takeaway", "打包", "Afhalen")
                        }",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = timeLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF6A7078)
                    )
                }

                Text(
                    text = "€ ${formatMoney(order.total)}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = "${if (order.deviceName.isNotBlank()) order.deviceName + " · " else ""}${order.source} · ${order.paymentMethod} · ${order.status}",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF4B4F55)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(onClick = onShowDetails) {
                    Text(tr("Details", "详情", "Details"))
                }
                OutlinedButton(onClick = onPrintReceipt) {
                    Text(tr("Print Receipt", "打印小票", "Print Bon"))
                }
                OutlinedButton(onClick = onPrintOrder) {
                    Text(tr("Print Order", "打印订单", "Print Bestelling"))
                }
                OutlinedButton(onClick = onPrintKitchen) {
                    Text(tr("Print Kitchen", "打印后厨", "Print Keuken"))
                }
            }
        }
    }
}
