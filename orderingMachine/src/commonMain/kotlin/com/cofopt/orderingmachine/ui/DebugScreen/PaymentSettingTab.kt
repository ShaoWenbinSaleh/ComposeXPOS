package com.cofopt.orderingmachine.ui.DebugScreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cofopt.orderingmachine.network.WecrConfig
import com.cofopt.orderingmachine.network.rememberOrderingPlatformContext
import com.cofopt.orderingmachine.ui.common.components.DebugSectionCard

@Composable
fun PaymentSettingTab() {
    val context = rememberOrderingPlatformContext()

    var posIp by remember { mutableStateOf(WecrConfig.posIp(context)) }
    var posPortText by remember { mutableStateOf(WecrConfig.posPort(context).toString()) }
    var enableCardPayment by remember { mutableStateOf(WecrConfig.enableCardPayment(context)) }
    var forceTestAmount by remember { mutableStateOf(WecrConfig.forceTestAmount(context)) }
    var debugCardSmEnabled by remember { mutableStateOf(WecrConfig.debugCardSmEnabled(context)) }
    var debugPosRequestSuccess by remember { mutableStateOf(WecrConfig.debugPosRequestSuccess(context)) }
    var debugPosTriggerSuccess by remember { mutableStateOf(WecrConfig.debugPosTriggerSuccess(context)) }

    var saveStatus by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        DebugSectionCard(title = "Payment (Open-Source Mock)") {
            Text(
                text = "This build uses mock payment only. No real gateway request, signature, or key exchange is executed.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF5E6470),
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = posIp,
                onValueChange = { posIp = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("POS_IP (placeholder)") },
                singleLine = true,
            )

            OutlinedTextField(
                value = posPortText,
                onValueChange = { posPortText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("POS_PORT (placeholder)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )

            ToggleRow(
                title = "Enable card payment option",
                checked = enableCardPayment,
                onCheckedChange = {
                    enableCardPayment = it
                    WecrConfig.setEnableCardPayment(context, it)
                    saveStatus = "Saved"
                },
            )

            ToggleRow(
                title = "Force mock amount = 0.01",
                checked = forceTestAmount,
                onCheckedChange = {
                    forceTestAmount = it
                    WecrConfig.setForceTestAmount(context, it)
                    saveStatus = "Saved"
                },
            )

            ToggleRow(
                title = "Debug state machine manually",
                checked = debugCardSmEnabled,
                onCheckedChange = {
                    debugCardSmEnabled = it
                    WecrConfig.setDebugCardSmEnabled(context, it)
                    saveStatus = "Saved"
                },
            )

            ToggleRow(
                title = "Mock request step success",
                checked = debugPosRequestSuccess,
                onCheckedChange = {
                    debugPosRequestSuccess = it
                    WecrConfig.setDebugPosRequestSuccess(context, it)
                    saveStatus = "Saved"
                },
            )

            ToggleRow(
                title = "Mock trigger step success",
                checked = debugPosTriggerSuccess,
                onCheckedChange = {
                    debugPosTriggerSuccess = it
                    WecrConfig.setDebugPosTriggerSuccess(context, it)
                    saveStatus = "Saved"
                },
            )

            Button(
                onClick = {
                    val port = posPortText.toIntOrNull() ?: return@Button
                    WecrConfig.savePaymentSettings(context, login = "", sid = "", posIp = posIp.trim(), posPort = port)
                    saveStatus = "Saved"
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save Mock Config")
            }

            saveStatus?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(end = 12.dp),
        )

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF22B573),
            ),
        )
    }
}
