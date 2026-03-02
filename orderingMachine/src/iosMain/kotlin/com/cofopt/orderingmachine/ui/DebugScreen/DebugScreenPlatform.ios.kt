package com.cofopt.orderingmachine.ui.DebugScreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cofopt.orderingmachine.network.CashRegisterConfig
import com.cofopt.orderingmachine.network.DeviceConfig
import com.cofopt.orderingmachine.network.rememberOrderingPlatformContext
import com.cofopt.orderingmachine.ui.common.components.DebugSectionCard

actual object DebugScreenPlatform {
    @Composable
    actual fun PrinterSettingTabContent() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            DebugSectionCard(title = "Printer Setting (iOS)") {
                Text(
                    text = "iOS build uses mock printing in open-source mode.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }

    @Composable
    actual fun SystemInfoTabContent() {
        val context = rememberOrderingPlatformContext()
        val deviceUuid = remember(context) { DeviceConfig.deviceUuid(context) }
        val host = remember(context) { CashRegisterConfig.host(context).ifBlank { "-" } }
        val port = remember(context) { CashRegisterConfig.port(context) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            DebugSectionCard(title = "System Information", itemSpacing = 10.dp) {
                Text(text = "Platform: iOS")
                Text(text = "Device UUID: $deviceUuid")
            }

            DebugSectionCard(title = "CashRegister Connection", itemSpacing = 10.dp) {
                Text(text = "Host: $host", modifier = Modifier.fillMaxWidth())
                Text(text = "Port: $port", modifier = Modifier.fillMaxWidth())
            }
        }
    }

    @Composable
    actual fun rememberExitAppAction(): (() -> Unit)? = null
}
