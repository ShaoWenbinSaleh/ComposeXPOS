package com.cofopt.orderingmachine.ui.DebugScreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cofopt.orderingmachine.currentEpochMillis
import com.cofopt.orderingmachine.network.CashRegisterClient
import com.cofopt.orderingmachine.network.CashRegisterConfig
import com.cofopt.orderingmachine.network.CashRegisterOrderItemPayload
import com.cofopt.orderingmachine.network.CashRegisterOrderPayload
import com.cofopt.orderingmachine.network.DeviceConfig
import com.cofopt.orderingmachine.network.rememberOrderingPlatformContext
import com.cofopt.orderingmachine.network.normalizeCashRegisterHost
import com.cofopt.orderingmachine.ui.common.components.DebugSectionCard
import kotlinx.coroutines.launch

@Composable
fun SystemSettingTab() {
    val context = rememberOrderingPlatformContext()
    val scope = rememberCoroutineScope()
    val configuredEndpoint = remember(context) { CashRegisterConfig.endpoint(context) }

    var testStatus by remember { mutableStateOf<String?>(null) }
    var cashRegisterHost by remember(context) {
        mutableStateOf(configuredEndpoint?.host.orEmpty())
    }
    var cashRegisterPort by remember(context) {
        mutableStateOf((configuredEndpoint?.port ?: 8080).toString())
    }
    val deviceUuid = remember(context) { DeviceConfig.deviceUuid(context) }
    val androidDeviceName = remember { DeviceConfig.androidDeviceName() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        DebugSectionCard(title = "Device") {

            OutlinedTextField(
                value = deviceUuid,
                onValueChange = {},
                modifier = Modifier.fillMaxWidth(),
                label = { Text("DEVICE UUID (READ-ONLY)") },
                singleLine = true,
                readOnly = true,
            )

            OutlinedTextField(
                value = androidDeviceName,
                onValueChange = {},
                modifier = Modifier.fillMaxWidth(),
                label = { Text("ANDROID DEVICE NAME (READ-ONLY)") },
                singleLine = true,
                readOnly = true,
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "CashRegister Server",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            OutlinedTextField(
                value = cashRegisterHost,
                onValueChange = { cashRegisterHost = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("CASHREGISTER HOST") },
                singleLine = true,
            )

            OutlinedTextField(
                value = cashRegisterPort,
                onValueChange = { cashRegisterPort = it.filter(Char::isDigit).take(5) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("CASHREGISTER PORT") },
                singleLine = true,
            )

            Button(
                onClick = {
                    val port = cashRegisterPort.toIntOrNull()
                    val host = port?.let { normalizeCashRegisterHost(cashRegisterHost, it) }
                    if (port == null || port !in 1..65535 || host == null) {
                        testStatus = "Invalid CashRegister host or port"
                    } else {
                        if (CashRegisterConfig.save(context, host, port)) {
                            cashRegisterHost = host
                            cashRegisterPort = port.toString()
                            testStatus = "CashRegister endpoint saved"
                        } else {
                            testStatus = "CashRegister endpoint could not be saved"
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save CashRegister Endpoint")
            }

            Button(
                onClick = {
                    if (!CashRegisterClient.isConfigured(context)) {
                        testStatus = "CashRegister host is not configured yet"
                        return@Button
                    }
                    testStatus = "Sending test order..."
                    scope.launch {
                        try {
                            val payload = CashRegisterOrderPayload(
                                orderId = "TEST_${currentEpochMillis()}",
                                createdAtMillis = currentEpochMillis(),
                                source = "KIOSK",
                                deviceName = DeviceConfig.deviceUuid(context),
                                dineIn = true,
                                paymentMethod = "CARD",
                                paymentStatus = "PAID",
                                total = 1.0,
                                items = listOf(
                                    CashRegisterOrderItemPayload(
                                        menuItemId = "TEST_ITEM",
                                        nameEn = "Test Item",
                                        nameZh = "测试菜品",
                                        nameNl = "Test gerecht",
                                        quantity = 1,
                                        unitPrice = 1.0,
                                        customizations = emptyMap(),
                                    ),
                                ),
                            )
                            val result = CashRegisterClient.postOrder(context, payload)
                            testStatus = if (result != null) {
                                "Sent successfully, callNumber=$result"
                            } else {
                                "Send failed (no callNumber)"
                            }
                        } catch (e: Exception) {
                            testStatus = "Error: ${e.message}"
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Send Test Order to CashRegister")
            }

            testStatus?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
}
