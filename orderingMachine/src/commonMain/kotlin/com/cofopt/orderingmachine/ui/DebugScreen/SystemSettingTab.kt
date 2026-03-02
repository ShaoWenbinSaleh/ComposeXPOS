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
import com.cofopt.orderingmachine.currentTimeMillis
import com.cofopt.orderingmachine.network.CashRegisterClient
import com.cofopt.orderingmachine.network.CashRegisterConfig
import com.cofopt.orderingmachine.network.CashRegisterOrderItemPayload
import com.cofopt.orderingmachine.network.CashRegisterOrderPayload
import com.cofopt.orderingmachine.network.DeviceConfig
import com.cofopt.orderingmachine.network.rememberOrderingPlatformContext
import com.cofopt.orderingmachine.ui.common.components.DebugSectionCard
import kotlinx.coroutines.launch

@Composable
fun SystemSettingTab() {
    val context = rememberOrderingPlatformContext()
    val scope = rememberCoroutineScope()

    var testStatus by remember { mutableStateOf<String?>(null) }
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

            val currentHost = CashRegisterConfig.host(context).ifBlank { "-" }
            val currentPort = CashRegisterConfig.port(context)

            OutlinedTextField(
                value = currentHost,
                onValueChange = {},
                modifier = Modifier.fillMaxWidth(),
                label = { Text("CURRENT HOST (AUTO)") },
                singleLine = true,
                readOnly = true,
            )

            Text(
                text = "Current Port: $currentPort",
                style = MaterialTheme.typography.bodyMedium,
            )

            Button(
                onClick = {
                    if (currentHost == "-") {
                        testStatus = "CashRegister host is not configured yet"
                        return@Button
                    }
                    testStatus = "Sending test order..."
                    scope.launch {
                        try {
                            val payload = CashRegisterOrderPayload(
                                orderId = "TEST_${currentTimeMillis()}",
                                createdAtMillis = currentTimeMillis(),
                                source = "OrderingMachine",
                                deviceName = DeviceConfig.deviceUuid(context),
                                dineIn = true,
                                paymentMethod = "TEST",
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
