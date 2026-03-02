package com.cofopt.cashregister.cmp.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cofopt.cashregister.cmp.components.EmptyStateText
import com.cofopt.cashregister.cmp.components.HistoryOrderRow
import com.cofopt.cashregister.cmp.components.OrderDetailDialog
import com.cofopt.cashregister.cmp.components.SelectableOutlinedButton
import com.cofopt.cashregister.cmp.platform.CashRegisterPlatform
import com.cofopt.cashregister.cmp.utils.formatMoney
import com.cofopt.cashregister.cmp.utils.formatOrderTime
import com.cofopt.cashregister.cmp.utils.nowMillis
import com.cofopt.cashregister.cmp.utils.startOfTodayMillis
import com.cofopt.cashregister.network.OrderPayload
import com.cofopt.cashregister.utils.tr

private enum class HistoryRange {
    TODAY,
    LAST_7_DAYS,
    ALL
}

@Composable
fun HistoryScreen() {
    val todayOrders by CashRegisterPlatform.todayOrders.collectAsState()
    val archivedOrders by CashRegisterPlatform.archivedOrders.collectAsState()

    var range by remember { mutableStateOf(HistoryRange.TODAY) }
    var selectedForDetails by remember { mutableStateOf<OrderPayload?>(null) }

    val now = nowMillis()
    val filteredOrders = remember(todayOrders, archivedOrders, range, now) {
        val base = when (range) {
            HistoryRange.TODAY -> todayOrders
            HistoryRange.LAST_7_DAYS -> todayOrders + archivedOrders
            HistoryRange.ALL -> todayOrders + archivedOrders
        }

        val cutoff = when (range) {
            HistoryRange.TODAY -> startOfTodayMillis(now)
            HistoryRange.LAST_7_DAYS -> now - 7L * 24 * 60 * 60 * 1000
            HistoryRange.ALL -> Long.MIN_VALUE
        }
        base
            .filter { it.createdAtMillis >= cutoff }
            .sortedByDescending { it.createdAtMillis }
    }

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SelectableOutlinedButton(
                    selected = range == HistoryRange.TODAY,
                    label = tr("Today", "今日", "Vandaag"),
                    selectedLabel = tr("Today ✓", "今日 ✓", "Vandaag ✓"),
                    onClick = { range = HistoryRange.TODAY }
                )
                SelectableOutlinedButton(
                    selected = range == HistoryRange.LAST_7_DAYS,
                    label = tr("Last 7 Days", "近7天", "Laatste 7 dagen"),
                    selectedLabel = tr("Last 7 Days ✓", "近7天 ✓", "Laatste 7 dagen ✓"),
                    onClick = { range = HistoryRange.LAST_7_DAYS }
                )
                SelectableOutlinedButton(
                    selected = range == HistoryRange.ALL,
                    label = tr("All", "全部", "Alles"),
                    selectedLabel = tr("All ✓", "全部 ✓", "Alles ✓"),
                    onClick = { range = HistoryRange.ALL }
                )
            }

            val totalOrders = filteredOrders.size
            val totalSales = filteredOrders.sumOf { it.total }
            val averageOrder = if (totalOrders > 0) totalSales / totalOrders else 0.0

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                shape = MaterialTheme.shapes.medium
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatBlock(
                        label = tr("Total Orders", "订单总数", "Totaal bestellingen"),
                        value = totalOrders.toString()
                    )
                    StatBlock(
                        label = tr("Total Sales", "销售总额", "Totale omzet"),
                        value = "€${formatMoney(totalSales)}"
                    )
                    StatBlock(
                        label = tr("Average Order", "平均订单", "Gemiddelde bestelling"),
                        value = "€${formatMoney(averageOrder)}"
                    )
                }
            }

            if (filteredOrders.isEmpty()) {
                EmptyStateText(text = tr("No orders", "暂无订单", "Geen bestellingen"))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredOrders, key = { it.orderId }) { o ->
                        HistoryOrderRow(
                            order = o,
                            timeLabel = formatOrderTime(o.createdAtMillis),
                            onShowDetails = { selectedForDetails = o },
                            onPrintReceipt = { CashRegisterPlatform.printReceipt(o) },
                            onPrintOrder = { CashRegisterPlatform.printOrder(o) },
                            onPrintKitchen = { CashRegisterPlatform.printKitchen(o) }
                        )
                    }
                }
            }
        }
    }

    selectedForDetails?.let { o ->
        OrderDetailDialog(
            order = o,
            onDismiss = { selectedForDetails = null },
            onCancel = {
                CashRegisterPlatform.removeOrder(o.orderId)
                selectedForDetails = null
            },
            onCashCheckout = {
                CashRegisterPlatform.updateOrderPaymentMethod(o.orderId, "CASH")
                CashRegisterPlatform.updateOrderPaymentStatus(o.orderId, "PAID")
            },
            onCardCheckout = {
                CashRegisterPlatform.updateOrderPaymentMethod(o.orderId, "CARD")
                CashRegisterPlatform.updateOrderPaymentStatus(o.orderId, "PAID")
            }
        )
    }
}

@Composable
private fun StatBlock(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF6A7078)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
