package com.cofopt.cashregister.cmp.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cofopt.cashregister.cmp.components.ScreenHeader
import com.cofopt.cashregister.cmp.components.SelectableOutlinedButton
import com.cofopt.cashregister.cmp.platform.CashRegisterPlatform
import com.cofopt.cashregister.cmp.utils.nowMillis
import com.cofopt.cashregister.cmp.utils.startOfTodayMillis
import com.cofopt.cashregister.utils.LocalLanguage
import com.cofopt.cashregister.utils.localizedName
import com.cofopt.cashregister.utils.tr

enum class SalesTimeRange {
    TODAY,
    LAST_WEEK
}

@Composable
fun SalesScreen() {
    val orders by CashRegisterPlatform.orders.collectAsState()
    val allDishes by CashRegisterPlatform.dishes.collectAsState()
    val currentLanguage = LocalLanguage.current

    var timeRange by remember { mutableStateOf(SalesTimeRange.TODAY) }

    val now = nowMillis()
    val todayStart = startOfTodayMillis(now)
    val weekStart = now - 7L * 24L * 60L * 60L * 1000L

    val startTime = when (timeRange) {
        SalesTimeRange.TODAY -> todayStart
        SalesTimeRange.LAST_WEEK -> weekStart
    }

    val filteredOrders = remember(orders, startTime, now) {
        orders.filter {
            it.createdAtMillis >= startTime &&
                it.createdAtMillis <= now &&
                it.status == "PAID"
        }
    }

    val dishSalesMap = remember(filteredOrders) {
        val map = mutableMapOf<String, Int>()
        filteredOrders.forEach { order ->
            order.items.forEach { item ->
                map[item.menuItemId] = (map[item.menuItemId] ?: 0) + item.quantity
            }
        }
        map
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
            ScreenHeader(title = tr("Dish Sales Statistics", "菜品销售统计", "Gerecht Verkoop Statistieken"))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SelectableOutlinedButton(
                    selected = timeRange == SalesTimeRange.TODAY,
                    label = tr("Today", "今日", "Vandaag"),
                    selectedLabel = tr("Today ✓", "今日 ✓", "Vandaag ✓"),
                    onClick = { timeRange = SalesTimeRange.TODAY }
                )
                SelectableOutlinedButton(
                    selected = timeRange == SalesTimeRange.LAST_WEEK,
                    label = tr("Last Week", "近一周", "Laatste Week"),
                    selectedLabel = tr("Last Week ✓", "近一周 ✓", "Laatste Week ✓"),
                    onClick = { timeRange = SalesTimeRange.LAST_WEEK }
                )
            }

            HorizontalDivider()

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                items(allDishes) { dish ->
                    val quantity = dishSalesMap[dish.id] ?: 0
                    val dishName = dish.localizedName(currentLanguage)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = dishName,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = quantity.toString(),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (quantity > 0) MaterialTheme.colorScheme.primary else Color.Gray
                        )
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
