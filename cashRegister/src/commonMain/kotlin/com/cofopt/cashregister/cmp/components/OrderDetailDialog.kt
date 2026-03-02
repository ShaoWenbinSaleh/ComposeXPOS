package com.cofopt.cashregister.cmp.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cofopt.cashregister.cmp.utils.formatMoney
import com.cofopt.cashregister.cmp.utils.formatOrderTime
import com.cofopt.cashregister.network.OrderPayload
import com.cofopt.cashregister.utils.LocalLanguage
import com.cofopt.cashregister.utils.localizedName
import com.cofopt.cashregister.utils.localizedValue
import com.cofopt.cashregister.utils.tr

@Composable
fun OrderDetailDialog(
    order: OrderPayload,
    onDismiss: () -> Unit,
    onCancel: () -> Unit,
    onCashCheckout: () -> Unit,
    onCardCheckout: () -> Unit,
    onPaymentSuccess: () -> Unit = {}
) {
    val showCancelConfirm = remember { mutableStateOf(false) }
    val language = LocalLanguage.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = tr("Order Details", "订单详情", "Bestel Details"),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "${order.orderId} · ${formatOrderTime(order.createdAtMillis)} · ${
                        if (order.dineIn) tr("Dine In", "堂食", "Hier eten") else tr("Takeaway", "外带", "Meenemen")
                    } · ${order.paymentMethod} · ${order.status}"
                )
                Text(
                    "€ ${formatMoney(order.total)}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                order.items.forEach { item ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "${item.quantity}x ${item.localizedName(language)}  (€ ${formatMoney(item.unitPrice)})",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        item.customizationLines.forEach { line ->
                            Text(
                                text = "  ${line.titleEn}: ${line.localizedValue(language)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF6A7078)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(end = 12.dp)
            ) {
                TextButton(onClick = onDismiss) {
                    Text(tr("Close", "关闭", "Sluiten"))
                }
                Button(
                    onClick = {
                        onCashCheckout()
                        onPaymentSuccess()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                ) {
                    Text(tr("Cash", "现金", "Contant"))
                }
                Button(
                    onClick = {
                        onCardCheckout()
                        onPaymentSuccess()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))
                ) {
                    Text(tr("Card", "刷卡", "Pinpas"))
                }
                Button(
                    onClick = { showCancelConfirm.value = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD94348))
                ) {
                    Text(tr("Cancel", "取消订单", "Annuleer"))
                }
            }
        },
        dismissButton = {}
    )

    if (showCancelConfirm.value) {
        AlertDialog(
            onDismissRequest = { showCancelConfirm.value = false },
            title = { Text(tr("Cancel Order", "取消订单", "Bestelling annuleren")) },
            text = { Text(tr("Cancel this order permanently?", "确认永久取消该订单？", "Deze bestelling permanent annuleren?")) },
            confirmButton = {
                Button(
                    onClick = {
                        onCancel()
                        showCancelConfirm.value = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD94348))
                ) {
                    Text(tr("Confirm", "确认", "Bevestigen"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelConfirm.value = false }) {
                    Text(tr("Back", "返回", "Terug"))
                }
            }
        )
    }
}
