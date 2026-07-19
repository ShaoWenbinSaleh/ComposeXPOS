package com.cofopt.cashregister.cmp.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cofopt.cashregister.cmp.platform.CallingPlatform
import com.cofopt.cashregister.network.OrderPayload
import com.cofopt.cashregister.utils.tr
import com.cofopt.shared.network.OrderingCashRegisterConfigRequest
import com.cofopt.shared.network.OrderingCashRegisterConfigResponse
import com.cofopt.shared.network.COMPOSEXPOS_LINK_SHARED_KEY
import com.cofopt.shared.network.IosCallingEndpointStore
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import platform.Foundation.*
import kotlin.coroutines.resume

actual object DebugPlatformActions {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Composable
    actual fun rememberPaymentActions(): PaymentDebugActions = remember { IosPaymentDebugActions }

    @Composable
    actual fun rememberPrinterActions(): PrinterDebugActions = remember { IosPrinterDebugActions }

    @Composable
    actual fun CallingMachineTabContent() {
        val bridgeStatus by CallingPlatform.bridgeStatus.collectAsState()
        var host by remember { mutableStateOf(IosCallingEndpointStore.loadHost().orEmpty()) }
        var port by remember { mutableStateOf(IosCallingEndpointStore.loadPort().toString()) }
        var displayLanguage by remember { mutableStateOf(bridgeStatus.displayLanguage) }
        var voiceLanguage by remember { mutableStateOf(bridgeStatus.voiceLanguage) }
        var message by remember { mutableStateOf<String?>(null) }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF4F6F8))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = tr("Calling Machine", "叫号机", "Oproepmachine"),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text("Connected: ${bridgeStatus.connected}")
                Text("Target: ${(bridgeStatus.targetHost ?: "-")}:${bridgeStatus.targetPort ?: "-"}")
                if (bridgeStatus.reconnectAttempts > 0) {
                    Text("Reconnect attempts: ${bridgeStatus.reconnectAttempts}")
                }
                bridgeStatus.lastError?.takeIf { it.isNotBlank() }?.let {
                    Text("Last error: $it", color = Color(0xFFC62828))
                }

                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("CallingMachine Host") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("CallingMachine Port") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = displayLanguage,
                    onValueChange = { displayLanguage = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Display language (en/zh/nl/ja/tr)") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = voiceLanguage,
                    onValueChange = { voiceLanguage = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Voice language (en/zh/nl/ja/tr)") },
                    singleLine = true
                )
                Button(
                    onClick = {
                        message = if (
                            CallingPlatform.updateCallingLanguages(displayLanguage, voiceLanguage)
                        ) {
                            "Calling languages updated"
                        } else {
                            "ERROR: Invalid language or persistence failure"
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(tr("Apply languages", "应用语言", "Talen toepassen"))
                }
                Button(
                    onClick = {
                        val parsedPort = port.toIntOrNull()
                        if (parsedPort == null || !CallingPlatform.connectToCallingMachine(host, parsedPort)) {
                            message = "ERROR: Invalid host or port"
                        } else {
                            message = "Connecting to ${host.trim()}:$parsedPort"
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(tr("Connect", "连接", "Verbinden"))
                }
                if (bridgeStatus.connected) {
                    Button(
                        onClick = {
                            CallingPlatform.disconnectCallingMachine()
                            message = "Disconnected"
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(tr("Disconnect", "断开连接", "Verbreken"))
                    }
                }
                message?.let {
                    Text(
                        it,
                        color = if (it.startsWith("ERROR")) Color(0xFFC62828) else Color(0xFF1E7D34)
                    )
                }
            }
        }
    }

    @Composable
    actual fun OrderingMachinesTabContent() {
        val scope = rememberCoroutineScope()
        var orderingHost by remember { mutableStateOf("") }
        var orderingPort by remember { mutableStateOf("19081") }
        var cashRegisterHost by remember { mutableStateOf("") }
        var cashRegisterPort by remember { mutableStateOf("8080") }
        var statusText by remember { mutableStateOf<String?>(null) }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF4F6F8))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = tr("Ordering Machines", "点餐机", "Bestelmachines"),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = tr(
                        "Push CashRegister endpoint to OrderingMachine Android.",
                        "将 CashRegister 地址下发到 Android 点餐机。",
                        "Push CashRegister-endpoint naar Android-bestelmachine."
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )

                OutlinedTextField(
                    value = orderingHost,
                    onValueChange = { orderingHost = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("OrderingMachine Host") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = orderingPort,
                    onValueChange = { orderingPort = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("OrderingMachine Port") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = cashRegisterHost,
                    onValueChange = { cashRegisterHost = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("CashRegister Host") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = cashRegisterPort,
                    onValueChange = { cashRegisterPort = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("CashRegister Port") },
                    singleLine = true
                )

                Button(
                    onClick = {
                        val op = orderingPort.toIntOrNull()
                        val cp = cashRegisterPort.toIntOrNull()
                        if (orderingHost.trim().isEmpty() || op == null || op !in 1..65535 || cashRegisterHost.trim().isEmpty() || cp == null || cp !in 1..65535) {
                            statusText = "ERROR: Invalid host or port"
                            return@Button
                        }
                        statusText = "Pushing config..."
                        scope.launch {
                            statusText = pushOrderingConfig(
                                orderingHost = orderingHost,
                                orderingPort = op,
                                cashRegisterHost = cashRegisterHost,
                                cashRegisterPort = cp
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(tr("Push Config", "下发配置", "Config pushen"))
                }

                statusText?.let { msg ->
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (msg.startsWith("ERROR")) Color(0xFFC62828) else Color(0xFF1E7D34)
                    )
                }
            }
        }
    }

    @OptIn(BetaInteropApi::class)
    private suspend fun pushOrderingConfig(
        orderingHost: String,
        orderingPort: Int,
        cashRegisterHost: String,
        cashRegisterPort: Int
    ): String = suspendCancellableCoroutine { cont ->
        val endpoint = "http://${orderingHost.trim()}:$orderingPort/cashregister"
        val url = NSURL.URLWithString(endpoint)
        if (url == null) {
            cont.resume("ERROR: Invalid URL")
            return@suspendCancellableCoroutine
        }

        val requestPayload = json.encodeToString(
            OrderingCashRegisterConfigRequest(
                host = cashRegisterHost.trim(),
                port = cashRegisterPort,
                sharedKey = null
            )
        )

        val request = NSMutableURLRequest.requestWithURL(url).apply {
            setHTTPMethod("POST")
            setValue("application/json", forHTTPHeaderField = "Content-Type")
            setValue(COMPOSEXPOS_LINK_SHARED_KEY, forHTTPHeaderField = "X-ComposeXPOS-Key")
            setHTTPBody(requestPayload.encodeToByteArray().toNSData())
        }

        val task = NSURLSession.sharedSession.dataTaskWithRequest(
            request = request as NSURLRequest,
            completionHandler = { data, response, error ->
            if (error != null) {
                cont.resume("ERROR: ${error.localizedDescription()}")
                return@dataTaskWithRequest
            }

            val statusCode = (response as? NSHTTPURLResponse)?.statusCode?.toInt() ?: -1
            val raw = if (data != null) {
                NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString().orEmpty()
            } else {
                ""
            }
            if (statusCode !in 200..299) {
                val message = parseMessage(raw) ?: "HTTP $statusCode"
                cont.resume("ERROR: $message")
                return@dataTaskWithRequest
            }

            val payload = runCatching {
                json.decodeFromString<OrderingCashRegisterConfigResponse>(raw)
            }.getOrNull()
            if (payload?.status?.equals("ok", ignoreCase = true) == true) {
                val host = payload.host?.ifBlank { cashRegisterHost.trim() } ?: cashRegisterHost.trim()
                val port = payload.port ?: cashRegisterPort
                cont.resume("OrderingMachine configured: $host:$port")
            } else {
                val message = payload?.message ?: parseMessage(raw) ?: "invalid_response"
                cont.resume("ERROR: $message")
            }
        })

        cont.invokeOnCancellation { task.cancel() }
        task.resume()
    }

    private fun parseMessage(raw: String): String? {
        if (raw.isBlank()) return null
        return runCatching {
            json.decodeFromString<OrderingCashRegisterConfigResponse>(raw).message
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private object IosPaymentDebugActions : PaymentDebugActions {
        override val alertSoundOptions: List<String> = listOf("OFF")
        override fun loadAlertSoundId(): String = "OFF"
        override fun saveAlertSoundId(id: String) {}
        override fun loadForceTestAmount(): Boolean = false
        override fun saveForceTestAmount(enabled: Boolean) {}
        override suspend fun resetMenuPricesToDefault(): Result<Int> = Result.success(0)
        override suspend fun runMockPayment(amount: Double): String = "OK: iOS mock payment"
        override fun playAlertSound() {}
    }

    private object IosPrinterDebugActions : PrinterDebugActions {
        override suspend fun print(kind: DebugPrinterKind, order: OrderPayload, callNumber: String?): Boolean = true
    }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun ByteArray.toNSData(): NSData = usePinned {
    NSData.create(bytes = it.addressOf(0), length = size.toULong())
}
