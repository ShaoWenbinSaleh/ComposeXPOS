package com.cofopt.cashregister

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cofopt.cashregister.network.CallingMachineBridge
import com.cofopt.cashregister.network.ComposeXPOSDiscoveredService
import com.cofopt.cashregister.network.ComposeXPOSNsdAdvertiser
import com.cofopt.cashregister.network.ComposeXPOSNsdBrowser
import com.cofopt.cashregister.utils.tr

@Composable
internal fun CallingMachineTab() {
    val context = LocalContext.current
    val bridge = remember(context) { CallingMachineBridge.get(context) }
    val nsdBrowser = remember(context) { ComposeXPOSNsdBrowser(context) }
    val bridgeStatus by bridge.status.collectAsState()
    val discoveredServices by nsdBrowser.services.collectAsState()

    LaunchedEffect(Unit) {
        bridge.start()
        nsdBrowser.start()
    }

    DisposableEffect(Unit) {
        onDispose { nsdBrowser.stop(clearResults = true) }
    }

    var statusText by remember { mutableStateOf<String?>(null) }
    val callingServices = discoveredServices.filter { it.role == "CallingMachine" }

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
                    text = tr("Calling Machine", "叫号机", "Oproepmachine"),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text("Connected: ${bridgeStatus.connected}")
                Text("Target: ${(bridgeStatus.targetHost ?: "-")}:${bridgeStatus.targetPort ?: "-"}")
                if (!bridgeStatus.lastError.isNullOrBlank()) {
                    Text("Last Error: ${bridgeStatus.lastError}", color = Color(0xFFC62828))
                }

                OutlinedButton(
                    onClick = {
                        nsdBrowser.start()
                        statusText = "Discovering services..."
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(tr("Discover CallingMachine", "发现叫号机", "Ontdek oproepmachine"))
                }

                if (callingServices.isEmpty()) {
                    Text(
                        text = tr("No CallingMachine discovered yet", "尚未发现叫号机", "Nog geen oproepmachine gevonden"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF6A7078)
                    )
                } else {
                    callingServices.forEach { svc ->
                        val isConnected = bridgeStatus.connected &&
                            bridgeStatus.targetHost == svc.host &&
                            bridgeStatus.targetPort == svc.port
                        CallingDiscoveredServiceCard(
                            service = svc,
                            isConnected = isConnected,
                            onConnectCalling = { host, port ->
                                CashRegisterDebugConfig.saveCallingMachine(context, host, port)
                                statusText = "Connecting..."
                                bridge.connect(host, port)
                            }
                        )
                    }
                }

                statusText?.let { msg ->
                    Text(
                        text = msg,
                        color = if (msg.startsWith("ERROR")) Color(0xFFC62828) else Color(0xFF4B4F55)
                    )
                }
            }
        }
    }
}

@Composable
internal fun CallingDiscoveredServiceCard(
    service: ComposeXPOSDiscoveredService,
    isConnected: Boolean,
    onConnectCalling: (String, Int) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF4F6F8))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "${service.role} • ${service.serviceName}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "${service.host}:${service.port}",
                style = MaterialTheme.typography.bodyMedium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { onConnectCalling(service.host, service.port) },
                    enabled = !isConnected
                ) {
                    Text(
                        if (isConnected) {
                            tr("Connected", "已连接", "Verbonden")
                        } else {
                            tr("Use Target", "使用目标", "Gebruik doel")
                        }
                    )
                }
            }
        }
    }
}
