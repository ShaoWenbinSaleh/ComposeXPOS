package com.cofopt.cashregister.cmp.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cofopt.cashregister.cmp.debug.DebugPlatformActions
import com.cofopt.cashregister.cmp.debug.PaymentTestTabContent
import com.cofopt.cashregister.cmp.debug.PrinterTestTabContent
import com.cofopt.cashregister.cmp.platform.CallingPlatform
import com.cofopt.cashregister.cmp.platform.CashRegisterPlatform
import com.cofopt.cashregister.utils.tr

@Composable
fun DebugToolsScreen(onBack: () -> Unit) {
    var selectedTabIndex by remember { mutableStateOf(0) }
    val paymentActions = DebugPlatformActions.rememberPaymentActions()
    val printerActions = DebugPlatformActions.rememberPrinterActions()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = tr("Debug", "调试", "Debug"),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )
            Button(onClick = onBack) {
                Text(tr("Back", "返回", "Terug"))
            }
        }

        TabRow(selectedTabIndex = selectedTabIndex) {
            Tab(
                selected = selectedTabIndex == 0,
                onClick = { selectedTabIndex = 0 },
                text = { Text(tr("Payment Test", "支付测试", "Betaling test")) }
            )
            Tab(
                selected = selectedTabIndex == 1,
                onClick = { selectedTabIndex = 1 },
                text = { Text(tr("Printer Test", "打印测试", "Printer test")) }
            )
            Tab(
                selected = selectedTabIndex == 2,
                onClick = { selectedTabIndex = 2 },
                text = { Text(tr("Calling Machine", "叫号机", "Oproepmachine")) }
            )
            Tab(
                selected = selectedTabIndex == 3,
                onClick = { selectedTabIndex = 3 },
                text = { Text(tr("Ordering Machines", "点餐机", "Bestelmachines")) }
            )
            Tab(
                selected = selectedTabIndex == 4,
                onClick = { selectedTabIndex = 4 },
                text = { Text(tr("System", "系统", "Systeem")) }
            )
        }

        when (selectedTabIndex) {
            0 -> PaymentTestTabContent(actions = paymentActions)
            1 -> PrinterTestTabContent(actions = printerActions)
            2 -> DebugPlatformActions.CallingMachineTabContent()
            3 -> DebugPlatformActions.OrderingMachinesTabContent()
            else -> {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { CashRegisterPlatform.clearOrders() }) {
                        Text(tr("Clear Orders", "清空订单", "Bestellingen wissen"))
                    }
                    Button(onClick = {
                        CallingPlatform.clearPreparing()
                        CallingPlatform.clearReady()
                    }) {
                        Text(tr("Clear Calling Queue", "清空叫号队列", "Oproepwachtrij wissen"))
                    }
                }
            }
        }
    }
}
