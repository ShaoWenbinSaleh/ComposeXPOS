package com.cofopt.cashregister.cmp.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cofopt.cashregister.cmp.components.OrderDetailDialog
import com.cofopt.cashregister.cmp.platform.CashRegisterPlatform
import com.cofopt.cashregister.cmp.utils.formatMoney
import com.cofopt.cashregister.cmp.utils.formatOrderTime
import com.cofopt.cashregister.network.OrderPayload
import com.cofopt.cashregister.utils.tr

@Composable
fun KioskScreen() {
    val orders by CashRegisterPlatform.todayOrders.collectAsState()

    val kioskCashOrders = remember(orders) {
        orders.filter {
            it.source.equals("KIOSK", ignoreCase = true) &&
                it.status.equals("UNPAID", ignoreCase = true) &&
                (
                    it.paymentMethod.equals("CASH", ignoreCase = true) ||
                        it.paymentMethod.equals("CARD", ignoreCase = true)
                    )
        }
    }
    var selectedOrderId by remember { mutableStateOf<String?>(null) }
    val selectedOrder = remember(kioskCashOrders, selectedOrderId) {
        kioskCashOrders.firstOrNull { it.orderId == selectedOrderId }
    }

    val seenOrderIds = remember { mutableStateListOf<String>() }
    LaunchedEffect(kioskCashOrders) {
        val ids = kioskCashOrders.map { it.orderId }
        val newIds = ids.filterNot { seenOrderIds.contains(it) }
        if (seenOrderIds.isEmpty()) {
            seenOrderIds.clear()
            seenOrderIds.addAll(ids)
        } else {
            if (newIds.isNotEmpty()) {
                CashRegisterPlatform.playAlertSound()
            }
            seenOrderIds.clear()
            seenOrderIds.addAll(ids)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = tr("Kiosk Orders", "自助订单", "Kiosk bestellingen"),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Card(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                if (kioskCashOrders.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tr("No orders", "暂无订单", "Geen bestellingen"),
                            style = MaterialTheme.typography.titleLarge,
                            color = Color(0xFF6A7078)
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        columns = GridCells.Fixed(4),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(kioskCashOrders, key = { it.orderId }) { order ->
                            KioskOrderCard(
                                order = order,
                                selected = order.orderId == selectedOrderId,
                                onClick = { selectedOrderId = order.orderId }
                            )
                        }
                    }
                }
            }
        }
    }

    if (selectedOrder != null) {
        OrderDetailDialog(
            order = selectedOrder,
            onDismiss = { selectedOrderId = null },
            onCancel = {
                CashRegisterPlatform.removeOrder(selectedOrder.orderId)
                selectedOrderId = null
            },
            onCashCheckout = {
                CashRegisterPlatform.updateOrderPaymentMethod(selectedOrder.orderId, "CASH")
                CashRegisterPlatform.updateOrderPaymentStatus(selectedOrder.orderId, "PAID")
                selectedOrderId = null
            },
            onCardCheckout = {
                CashRegisterPlatform.updateOrderPaymentMethod(selectedOrder.orderId, "CARD")
                CashRegisterPlatform.updateOrderPaymentStatus(selectedOrder.orderId, "PAID")
                selectedOrderId = null
            }
        )
    }
}

@Composable
private fun KioskOrderCard(order: OrderPayload, selected: Boolean, onClick: () -> Unit) {
    val borderColor = if (selected) Color(0xFF2F80ED) else Color(0xFFE0E3E8)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 4.dp else 2.dp),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = order.callNumber?.toString()?.padStart(2, '0') ?: "--",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1A1C1E),
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(4.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = formatOrderSummary(order),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF4B4F55),
                    maxLines = 2,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "${order.deviceName.ifBlank { "KIOSK" }} • ${formatOrderTime(order.createdAtMillis)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF6A7078),
                    maxLines = 1
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "€${formatMoney(order.total)}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF2F80ED),
                        maxLines = 1
                    )

                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF6A7078)
                    )

                    Text(
                        text = if (order.dineIn) {
                            tr("Dine In", "堂食", "Hier eten")
                        } else {
                            tr("Takeaway", "外带", "Meenemen")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (order.dineIn) Color(0xFF22B573) else Color(0xFF6A7078),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

private fun formatOrderSummary(order: OrderPayload): String {
    val items = order.items.take(2)
    val itemNames = items.map { it.nameEn }.joinToString(", ")
    val remainingCount = order.items.size - items.size

    return when {
        order.items.isEmpty() -> "No items"
        remainingCount <= 0 -> itemNames
        remainingCount == 1 -> "$itemNames + 1 more"
        else -> "$itemNames + $remainingCount more"
    }
}
