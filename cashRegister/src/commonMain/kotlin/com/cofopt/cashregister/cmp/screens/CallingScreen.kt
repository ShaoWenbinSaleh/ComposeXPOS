package com.cofopt.cashregister.cmp.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cofopt.cashregister.cmp.platform.CallingPlatform
import com.cofopt.cashregister.cmp.platform.ManualCallAddResult
import com.cofopt.cashregister.utils.tr

@Composable
fun CallingScreen() {
    val preparing by CallingPlatform.preparing.collectAsState()
    val ready by CallingPlatform.ready.collectAsState()
    val filteredPreparing = remember(preparing) { preparing }
    val filteredReady = remember(ready) { ready }

    val manualInvalidNumberText = tr(
        "Please enter a valid number",
        "请输入有效数字",
        "Voer een geldig nummer in"
    )
    val manualDuplicateText = tr(
        "This number already exists",
        "该号码已存在",
        "Dit nummer bestaat al"
    )
    val manualOutOfRangeText = tr(
        "Number is out of range",
        "号码超出范围",
        "Nummer is buiten bereik"
    )

    var showAddDialog by remember { mutableStateOf(false) }
    var manualInput by remember { mutableStateOf("") }
    var manualInputError by remember { mutableStateOf<String?>(null) }
    var manualTargetState by remember { mutableStateOf("ready") }

    var showAlertDialog by remember { mutableStateOf(false) }
    var alertCallNumber by remember { mutableStateOf<Int?>(null) }

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
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f).fillMaxSize(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = tr("Preparing", "备餐中", "In voorbereiding"),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Button(
                                onClick = { CallingPlatform.clearPreparing() },
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text(
                                    text = tr("Clear", "清除", "Wissen"),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        LazyVerticalGrid(
                            columns = GridCells.Fixed(4),
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            gridItems(filteredPreparing, key = { it }) { n ->
                                CallingNumberCard(
                                    number = n,
                                    onClick = { CallingPlatform.markReady(n) }
                                )
                            }
                        }
                    }
                }

                Card(
                    modifier = Modifier.weight(1f).fillMaxSize(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = tr("Ready", "请取餐", "Klaar"),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Button(
                                onClick = { CallingPlatform.clearReady() },
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text(
                                    text = tr("Clear", "清除", "Wissen"),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        LazyVerticalGrid(
                            columns = GridCells.Fixed(4),
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            gridItems(filteredReady, key = { it }) { n ->
                                ReadyCallingNumberCard(
                                    number = n,
                                    onAlert = {
                                        CallingPlatform.sendAlert(n)
                                        alertCallNumber = n
                                        showAlertDialog = true
                                    },
                                    onFinish = {
                                        CallingPlatform.complete(n)
                                        CallingPlatform.updateOrderStatusByCallNumber(n, "COMPLETED")
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    manualInput = ""
                    manualInputError = null
                    manualTargetState = "ready"
                    showAddDialog = true
                }
            ) {
                Text(text = tr("Add a calling number", "手动添加叫号", "Voeg een afhaalnummer toe"))
            }
        }

        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                title = {
                    Text(text = tr("Add a calling number", "手动添加叫号", "Voeg een afhaalnummer toe"))
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextField(
                            value = manualInput,
                            onValueChange = {
                                manualInput = it
                                manualInputError = null
                            },
                            singleLine = true,
                            label = { Text(text = tr("Number", "号码", "Nummer")) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            RadioButton(
                                selected = manualTargetState == "preparing",
                                onClick = { manualTargetState = "preparing" }
                            )
                            Text(text = tr("Preparing", "备餐中", "Voorbereiden"))

                            Spacer(modifier = Modifier.width(16.dp))

                            RadioButton(
                                selected = manualTargetState == "ready",
                                onClick = { manualTargetState = "ready" }
                            )
                            Text(text = tr("Ready", "请取餐", "Klaar"))
                        }

                        if (manualInputError != null) {
                            Text(
                                text = manualInputError!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val parsed = manualInput.trim().toIntOrNull()
                            if (parsed == null) {
                                manualInputError = manualInvalidNumberText
                                return@TextButton
                            }

                            val result = when (manualTargetState) {
                                "preparing" -> CallingPlatform.addManualPreparing(parsed)
                                "ready" -> CallingPlatform.addManualReady(parsed)
                                else -> ManualCallAddResult.OutOfRange
                            }

                            when (result) {
                                ManualCallAddResult.Added -> showAddDialog = false
                                ManualCallAddResult.Duplicate -> manualInputError = manualDuplicateText
                                ManualCallAddResult.OutOfRange -> manualInputError = manualOutOfRangeText
                            }
                        }
                    ) {
                        Text(text = tr("Add", "添加", "Toevoegen"))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddDialog = false }) {
                        Text(text = tr("Cancel", "取消", "Annuleren"))
                    }
                }
            )
        }

        if (showAlertDialog && alertCallNumber != null) {
            AlertDialog(
                onDismissRequest = { showAlertDialog = false },
                title = { Text(text = tr("Alert", "提醒", "Herinnering")) },
                text = {
                    Text(
                        text = tr(
                            "Call number ${alertCallNumber} is ready for pickup.",
                            "叫号 ${alertCallNumber} 请取餐。",
                            "Afhaalnummer ${alertCallNumber} is klaar voor afhalen."
                        )
                    )
                },
                confirmButton = {
                    TextButton(onClick = { showAlertDialog = false }) {
                        Text(text = tr("OK", "确定", "OK"))
                    }
                }
            )
        }
    }
}

@Composable
fun ReadyCallingNumberCard(
    number: Int,
    onAlert: () -> Unit,
    onFinish: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Red),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(4.dp),
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            TextButton(
                onClick = onAlert,
                modifier = Modifier.fillMaxWidth().height(24.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(
                    text = tr("Alert", "提醒", "Herinnering"),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White
                )
            }
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            TextButton(
                onClick = onFinish,
                modifier = Modifier.fillMaxWidth().height(24.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(
                    text = tr("Finish", "完成", "Voltooid"),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun CallingNumberCard(
    number: Int,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Red),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        onClick = onClick
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}
