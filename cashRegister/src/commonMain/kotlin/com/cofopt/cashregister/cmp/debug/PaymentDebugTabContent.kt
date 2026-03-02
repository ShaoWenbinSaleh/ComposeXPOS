package com.cofopt.cashregister.cmp.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cofopt.cashregister.cmp.utils.nowMillis
import com.cofopt.cashregister.utils.tr
import kotlinx.coroutines.launch

interface PaymentDebugActions {
    val alertSoundOptions: List<String>

    fun loadAlertSoundId(): String
    fun saveAlertSoundId(id: String)

    fun loadForceTestAmount(): Boolean
    fun saveForceTestAmount(enabled: Boolean)

    suspend fun resetMenuPricesToDefault(): Result<Int>
    suspend fun runMockPayment(amount: Double): String
    fun playAlertSound()
}

@Composable
fun PaymentTestTabContent(
    actions: PaymentDebugActions,
    onResetSuccess: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val savedText = tr("Saved", "已保存", "Opgeslagen")
    val testingText = tr("Testing...", "测试中...", "Testen...")

    var alertSoundId by remember(actions) { mutableStateOf(actions.loadAlertSoundId()) }
    var forceTestAmount by remember(actions) { mutableStateOf(actions.loadForceTestAmount()) }
    var manualAmountText by remember { mutableStateOf("12.50") }

    var status by remember { mutableStateOf<String?>(null) }
    var isRunning by remember { mutableStateOf(false) }
    var testLog by remember { mutableStateOf("") }

    fun logAppend(message: String) {
        testLog += "${nowMillis()} $message\n"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = tr("Menu Management", "菜单管理", "Menu beheer"),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Button(
                    onClick = {
                        scope.launch {
                            val result = actions.resetMenuPricesToDefault()
                            result.fold(
                                onSuccess = { count ->
                                    logAppend("Reset $count menu items to default prices")
                                    onResetSuccess()
                                },
                                onFailure = { error ->
                                    logAppend("Error resetting prices: ${error.message ?: error::class.simpleName}")
                                }
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(tr("Reset all menu items price", "重置所有菜品价格", "Reset alle menu-item prijzen"))
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = tr("Mock Payment", "Mock 支付", "Mock betaling"),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = manualAmountText,
                    onValueChange = { manualAmountText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(tr("Amount (EUR)", "金额（欧元）", "Bedrag (EUR)")) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = tr("Force amount = 0.01", "强制金额 = 0.01", "Forceer bedrag = 0.01"),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Switch(
                        checked = forceTestAmount,
                        onCheckedChange = { enabled ->
                            forceTestAmount = enabled
                            actions.saveForceTestAmount(enabled)
                            status = savedText
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF22B573)
                        )
                    )
                }

                Text(
                    text = tr(
                        "Open-source mode uses mock payment flow. This does not contact real payment provider or POS.",
                        "开源模式使用 mock 支付流程，不会连接真实支付服务或 POS。",
                        "Open-source modus gebruikt mock-betalingen en maakt geen verbinding met echte betaalprovider of POS."
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF6A7078)
                )

                Button(
                    onClick = {
                        if (isRunning) return@Button
                        val rawAmount = manualAmountText.trim().toDoubleOrNull()
                        val amount = if (forceTestAmount) 0.01 else rawAmount
                        if (amount == null || amount <= 0.0) {
                            status = "ERROR: Invalid amount"
                            return@Button
                        }

                        isRunning = true
                        status = testingText
                        logAppend("Start mock payment: amount=$amount")

                        scope.launch {
                            try {
                                val summary = actions.runMockPayment(amount)
                                status = summary
                                logAppend(summary)
                            } catch (e: Exception) {
                                status = "ERROR: ${e.message}"
                                logAppend("ERROR: ${e.message}")
                            } finally {
                                isRunning = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isRunning,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRunning) Color.Gray else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = if (isRunning) tr("Testing...", "测试中...", "Testen...")
                        else tr("Run Mock Payment", "执行 Mock 支付", "Voer mock-betaling uit"),
                        fontSize = 18.sp
                    )
                }

                status?.let { current ->
                    Text(
                        text = current,
                        style = MaterialTheme.typography.bodyLarge,
                        color = when {
                            current == savedText -> Color(0xFF2E7D32)
                            current.startsWith("OK") -> Color(0xFF2E7D32)
                            current.startsWith("ERROR") -> Color(0xFFC62828)
                            else -> Color(0xFF4B4F55)
                        }
                    )
                }

                Text(
                    text = tr("Alert Sound", "提示音效", "Meldingsgeluid"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    actions.alertSoundOptions.forEach { optionId ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            RadioButton(
                                selected = alertSoundId == optionId,
                                onClick = {
                                    alertSoundId = optionId
                                    actions.saveAlertSoundId(optionId)
                                }
                            )
                            Text(optionId)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { actions.playAlertSound() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(tr("Test Sound", "测试音效", "Test geluid"))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1E1E1E)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Test Log",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF2D2D2D), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = testLog.ifEmpty { "Click 'Run Mock Payment' to begin..." },
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp
                        ),
                        color = Color(0xFFE0E0E0),
                        modifier = Modifier.verticalScroll(rememberScrollState())
                    )
                }
            }
        }
    }
}
