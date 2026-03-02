package com.cofopt.cashregister.cmp.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cofopt.cashregister.cmp.utils.nowMillis
import com.cofopt.cashregister.network.CustomizationPrintLinePayload
import com.cofopt.cashregister.network.OrderItemPayload
import com.cofopt.cashregister.network.OrderPayload
import com.cofopt.cashregister.utils.tr
import kotlinx.coroutines.launch

enum class DebugPrinterKind {
    ORDER,
    RECEIPT,
    KITCHEN
}

interface PrinterDebugActions {
    suspend fun print(kind: DebugPrinterKind, order: OrderPayload, callNumber: String?): Boolean
}

@Composable
fun PrinterTestTabContent(actions: PrinterDebugActions) {
    val scope = rememberCoroutineScope()
    val printingText = tr("Printing...", "打印中...", "Printen...")

    var selectedPrinterKind by remember { mutableStateOf(DebugPrinterKind.RECEIPT) }
    var testLog by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var isPrinting by remember { mutableStateOf(false) }

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
                    text = tr("Printer Test", "打印测试", "Printer test"),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = tr(
                        "Open-source mode uses a single mock printer flow.",
                        "开源模式仅保留一种 mock 打印流程。",
                        "Open-source modus gebruikt slechts een mock-printerflow."
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF6A7078)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { selectedPrinterKind = DebugPrinterKind.ORDER },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            if (selectedPrinterKind == DebugPrinterKind.ORDER) {
                                tr("Order ✓", "订单 ✓", "Order ✓")
                            } else {
                                tr("Order", "订单", "Order")
                            }
                        )
                    }
                    OutlinedButton(
                        onClick = { selectedPrinterKind = DebugPrinterKind.RECEIPT },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            if (selectedPrinterKind == DebugPrinterKind.RECEIPT) {
                                tr("Receipt ✓", "小票 ✓", "Bon ✓")
                            } else {
                                tr("Receipt", "小票", "Bon")
                            }
                        )
                    }
                    OutlinedButton(
                        onClick = { selectedPrinterKind = DebugPrinterKind.KITCHEN },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            if (selectedPrinterKind == DebugPrinterKind.KITCHEN) {
                                tr("Kitchen ✓", "后厨 ✓", "Keuken ✓")
                            } else {
                                tr("Kitchen", "后厨", "Keuken")
                            }
                        )
                    }
                }

                Button(
                    onClick = {
                        if (isPrinting) return@Button
                        isPrinting = true
                        status = printingText

                        scope.launch {
                            try {
                                val testOrder = createTestOrder()
                                val callNumber = testOrder.callNumber?.toString()
                                val success = actions.print(selectedPrinterKind, testOrder, callNumber)
                                status = if (success) {
                                    "OK: MOCK ${selectedPrinterKind.name} printed"
                                } else {
                                    "ERROR: MOCK print failed"
                                }
                                logAppend(status ?: "")
                            } catch (e: Exception) {
                                status = "ERROR: ${e.message}"
                                logAppend(status ?: "")
                            } finally {
                                isPrinting = false
                            }
                        }
                    },
                    enabled = !isPrinting,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22B573))
                ) {
                    Text(
                        text = if (isPrinting) tr("Printing...", "打印中...", "Printen...") else tr("Print Test", "打印测试", "Test print"),
                        fontSize = 18.sp
                    )
                }

                status?.let { current ->
                    Text(
                        text = current,
                        style = MaterialTheme.typography.bodyLarge,
                        color = when {
                            current.startsWith("OK") -> Color(0xFF2E7D32)
                            current.startsWith("ERROR") -> Color(0xFFC62828)
                            else -> Color(0xFF4B4F55)
                        }
                    )
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                            .background(Color(0xFF2D2D2D), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = testLog.ifEmpty { "Click 'Print Test' to begin..." },
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
}

private fun createTestOrder(): OrderPayload {
    return OrderPayload(
        orderId = "TEST-ORDER-12345",
        createdAtMillis = nowMillis(),
        source = "KIOSK",
        deviceName = "Test Device",
        callNumber = 88,
        dineIn = true,
        paymentMethod = "CARD",
        status = "PAID",
        total = 25.50,
        items = listOf(
            OrderItemPayload(
                menuItemId = "noodle1",
                nameEn = "Beef Noodle Soup",
                nameZh = "牛肉面",
                nameNl = "Rundevleesnoedelsoep",
                quantity = 2,
                unitPrice = 8.50,
                customizations = mapOf(
                    "special_staple" to "noodles",
                    "special_noodle_shape" to "regular",
                    "special_spicy" to "spicy"
                ),
                customizationLines = listOf(
                    CustomizationPrintLinePayload(
                        titleEn = "Staple",
                        titleZh = "主食",
                        valueEn = "Noodles",
                        valueZh = "面"
                    ),
                    CustomizationPrintLinePayload(
                        titleEn = "Noodle shape",
                        titleZh = "面形",
                        valueEn = "Regular (2 mm)",
                        valueZh = "二细 (2 mm)"
                    ),
                    CustomizationPrintLinePayload(
                        titleEn = "Spiciness",
                        titleZh = "辣度",
                        valueEn = "Spicy",
                        valueZh = "辣"
                    )
                )
            ),
            OrderItemPayload(
                menuItemId = "drink1",
                nameEn = "Coca Cola",
                nameZh = "可口可乐",
                nameNl = "Coca Cola",
                quantity = 1,
                unitPrice = 3.50,
                customizations = emptyMap(),
                customizationLines = emptyList()
            ),
            OrderItemPayload(
                menuItemId = "side1",
                nameEn = "Spring Rolls",
                nameZh = "春卷",
                nameNl = "Loempia's",
                quantity = 3,
                unitPrice = 5.00,
                customizations = mapOf("size" to "large"),
                customizationLines = listOf(
                    CustomizationPrintLinePayload(
                        titleEn = "Size",
                        titleZh = "大小",
                        valueEn = "Large",
                        valueZh = "大份"
                    )
                )
            )
        )
    )
}
