package com.cofopt.orderingmachine.ui.DebugScreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cofopt.orderingmachine.network.CashRegisterConfig
import com.cofopt.orderingmachine.network.DeviceConfig
import com.cofopt.orderingmachine.network.PrinterConfig
import com.cofopt.orderingmachine.network.rememberOrderingPlatformContext
import com.cofopt.orderingmachine.ui.common.components.DebugSectionCard
import com.cofopt.orderingmachine.ui.common.components.LabeledRadioOption
import kotlinx.browser.window

actual object DebugScreenPlatform {
    @Composable
    actual fun PrinterSettingTabContent() {
        val context = rememberOrderingPlatformContext()

        var mode by remember { mutableStateOf(PrinterConfig.mode(context)) }
        var printerIp by remember { mutableStateOf(PrinterConfig.ip(context)) }
        var printerPortText by remember { mutableStateOf(PrinterConfig.port(context).toString()) }
        var status by remember { mutableStateOf<String?>(null) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            DebugSectionCard(title = "Printer Mode") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LabeledRadioOption(
                        label = "Sunmi",
                        selected = mode == "SUNMI",
                        onSelect = { mode = "SUNMI" },
                    )
                    LabeledRadioOption(
                        label = "USB",
                        selected = mode == "USB",
                        onSelect = { mode = "USB" },
                    )
                    LabeledRadioOption(
                        label = "IP",
                        selected = mode == "IP",
                        onSelect = { mode = "IP" },
                    )
                }

                if (mode == "IP") {
                    OutlinedTextField(
                        value = printerIp,
                        onValueChange = { printerIp = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("PRINTER IP") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = printerPortText,
                        onValueChange = { printerPortText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("PRINTER PORT") },
                        singleLine = true,
                    )
                }

                Button(
                    onClick = {
                        PrinterConfig.saveMode(context, mode)
                        if (mode == "IP") {
                            val port = printerPortText.toIntOrNull() ?: return@Button
                            PrinterConfig.saveIpConfig(context, printerIp.trim(), port)
                        }
                        status = "Saved"
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Save Printer Config")
                }

                Button(
                    onClick = {
                        status = "Web does not support local USB/Sunmi print driver. Use Android device for hardware print tests."
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Print Test")
                }

                status?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }

    @Composable
    actual fun SystemInfoTabContent() {
        val context = rememberOrderingPlatformContext()

        val deviceUuid = remember(context) { DeviceConfig.deviceUuid(context) }
        val deviceName = remember { DeviceConfig.androidDeviceName() }
        val localHost = remember {
            window.location.hostname.orEmpty().trim().ifBlank { "-" }
        }
        val host = remember(context) { CashRegisterConfig.host(context).ifBlank { "-" } }
        val port = remember(context) { CashRegisterConfig.port(context) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            DebugSectionCard(
                title = "System Information",
                itemSpacing = 10.dp,
            ) {
                Text(text = "App Version: 1.0 (web)")
                Text(text = "Platform: Web")
                Text(text = "Local IP: $localHost")
                Text(text = "Device: $deviceName")
                Text(text = "Device UUID: $deviceUuid")
            }

            DebugSectionCard(
                title = "Application Information",
                itemSpacing = 10.dp,
            ) {
                Text(text = "Package Name: com.cofopt.orderingmachine")
                Text(text = "Build Target: js/wasm")
                Text(text = "CashRegister Host: $host")
                Text(text = "CashRegister Port: $port")
            }
        }
    }

    @Composable
    actual fun rememberExitAppAction(): (() -> Unit)? = null
}
