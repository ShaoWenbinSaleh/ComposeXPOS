package com.cofopt.cashregister.cmp.debug

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
import com.cofopt.cashregister.cmp.platform.CashRegisterPlatform
import com.cofopt.cashregister.cmp.utils.nowMillis
import com.cofopt.cashregister.menu.MenuSeed
import com.cofopt.cashregister.network.OrderPayload
import com.cofopt.cashregister.utils.tr
import com.cofopt.shared.network.OrderingCashRegisterConfigRequest
import com.cofopt.shared.network.OrderingCashRegisterConfigResponse
import com.cofopt.shared.network.POSROID_LINK_SHARED_KEY
import kotlinx.browser.window
import kotlinx.coroutines.async
import kotlinx.coroutines.await
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.w3c.dom.WebSocket
import org.w3c.dom.events.Event
import org.w3c.fetch.Headers
import org.w3c.fetch.RequestInit
import kotlin.coroutines.resume

private const val CALLING_PORT = 9090
private const val ORDERING_PORT = 19081
private const val ORDERING_CONNECTED_KEYS = "cashregister.ordering.connected.keys"
private const val ORDERING_TARGET_HOST_KEY = "cashregister.ordering.target.host"
private const val ORDERING_TARGET_PORT_KEY = "cashregister.ordering.target.port"
private const val DISCOVERY_PREFIX_KEY = "cashregister.discovery.prefix"

private external class AbortController {
    val signal: dynamic
    fun abort()
}

private data class WebDiscoveredService(
    val role: String,
    val serviceName: String,
    val host: String,
    val port: Int,
    val supportsRemoteConfig: Boolean = true,
    val androidDeviceName: String? = null,
    val deviceUuid: String? = null
)

private data class ProbeResponse(
    val ok: Boolean,
    val status: Int,
    val body: String
)

private data class EndpointCandidate(
    val host: String,
    val port: Int
)

private data class OrderingDiscoveryResult(
    val supportsRemoteConfig: Boolean
)

actual object DebugPlatformActions {
    private var alertSoundId: String = "BEEP"
    private var forceTestAmount: Boolean = false
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Composable
    actual fun rememberPaymentActions(): PaymentDebugActions = remember { WebPaymentDebugActions }

    @Composable
    actual fun rememberPrinterActions(): PrinterDebugActions = remember { WebPrinterDebugActions }

    @Composable
    actual fun CallingMachineTabContent() {
        val scope = rememberCoroutineScope()
        val bridgeStatus by CallingPlatform.bridgeStatus.collectAsState()
        var discoveredServices by remember { mutableStateOf<List<WebDiscoveredService>>(emptyList()) }
        var discovering by remember { mutableStateOf(false) }
        var statusText by remember { mutableStateOf<String?>(null) }
        var scanPrefix by remember {
            mutableStateOf(loadDiscoveryPrefix()?.ifBlank { defaultDiscoveryPrefix() } ?: defaultDiscoveryPrefix())
        }
        var manualHost by remember {
            mutableStateOf(
                bridgeStatus.targetHost
                    ?: queryParam("callingHost")
                    ?: queryParam("calling_host")
                    ?: ""
            )
        }
        var manualPort by remember {
            mutableStateOf(
                (
                    bridgeStatus.targetPort
                        ?: queryParam("callingPort")?.toIntOrNull()
                        ?: queryParam("calling_port")?.toIntOrNull()
                        ?: CALLING_PORT
                    ).toString()
            )
        }
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
                    if (window.location.protocol == "https:") {
                        Text(
                            text = "HTTPS page may block ws/http LAN discovery on some browsers.",
                            color = Color(0xFF8D6E00),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (!bridgeStatus.lastError.isNullOrBlank()) {
                        Text("Last Error: ${bridgeStatus.lastError}", color = Color(0xFFC62828))
                    }

                    OutlinedButton(
                        onClick = {
                            if (discovering) return@OutlinedButton
                            val prefix = scanPrefix.trim()
                            if (prefix.isNotEmpty() && !isIpv4Prefix(prefix)) {
                                statusText = "ERROR: Invalid LAN prefix, expected x.x.x"
                                return@OutlinedButton
                            }
                            if (prefix.isNotEmpty()) saveDiscoveryPrefix(prefix)
                            val preferredHost = sanitizeHostInput(manualHost).takeIf { isValidHost(it) }
                            val preferredPort = manualPort.toIntOrNull()
                            val targets = buildCallingDiscoveryTargets(prefix, preferredPort, preferredHost)
                            discovering = true
                            statusText = "Scanning ${targets.size} target(s)..."
                            scope.launch {
                                val found = discoverCallingMachines(prefix, preferredPort, preferredHost)
                                discoveredServices = found
                                statusText = if (found.isEmpty()) {
                                    "No CallingMachine discovered yet"
                                } else {
                                    "Discovered ${found.size} CallingMachine service(s)"
                                }
                                discovering = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (discovering) {
                                tr("Discovering...", "发现中...", "Bezig met ontdekken...")
                            } else {
                                tr("Discover CallingMachine", "发现叫号机", "Ontdek oproepmachine")
                            }
                        )
                    }

                    OutlinedTextField(
                        value = scanPrefix,
                        onValueChange = { scanPrefix = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(tr("LAN Prefix", "局域网前缀", "LAN-prefix")) },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = manualHost,
                        onValueChange = { manualHost = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Manual Host") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = manualPort,
                        onValueChange = { manualPort = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Manual Port") },
                        singleLine = true
                    )
                    Button(
                        onClick = {
                            val host = sanitizeHostInput(manualHost)
                            val port = manualPort.toIntOrNull()
                            if (host.isBlank() || port == null || port !in 1..65535) {
                                statusText = "ERROR: Invalid manual host or port"
                                return@Button
                            }
                            statusText = "Connecting..."
                            CallingPlatform.connectToCallingMachine(host, port)
                            statusText = "Target set: $host:$port"
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(tr("Use Manual Target", "使用手动目标", "Gebruik handmatig doel"))
                    }

                    if (callingServices.isEmpty()) {
                        Text(
                            text = tr(
                                "No CallingMachine discovered yet",
                                "尚未发现叫号机",
                                "Nog geen oproepmachine gevonden"
                            ),
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
                                    statusText = "Connecting..."
                                    CallingPlatform.connectToCallingMachine(host, port)
                                    statusText = "Target set: $host:$port"
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
    actual fun OrderingMachinesTabContent() {
        val scope = rememberCoroutineScope()
        var discoveredServices by remember { mutableStateOf<List<WebDiscoveredService>>(emptyList()) }
        var connectedKeys by remember { mutableStateOf(loadOrderingConnectedKeys()) }
        var discovering by remember { mutableStateOf(false) }
        var statusText by remember { mutableStateOf<String?>(null) }
        var scanPrefix by remember {
            mutableStateOf(loadDiscoveryPrefix()?.ifBlank { defaultDiscoveryPrefix() } ?: defaultDiscoveryPrefix())
        }
        var manualHost by remember {
            mutableStateOf(
                loadOrderingTargetHost()
                    ?: queryParam("orderingHost")
                    ?: queryParam("ordering_host")
                    ?: ""
            )
        }
        var manualPort by remember {
            mutableStateOf(
                (
                    loadOrderingTargetPort()
                        ?: queryParam("orderingPort")?.toIntOrNull()
                        ?: queryParam("ordering_port")?.toIntOrNull()
                        ?: ORDERING_PORT
                    ).toString()
            )
        }
        val orderingServices = discoveredServices.filter { it.role == "OrderingMachine" }
        val cashEndpoint = remember { resolveDefaultCashRegisterEndpoint() }

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
                        text = tr("Ordering Machines", "点餐机", "Bestelmachines"),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    if (window.location.protocol == "https:") {
                        Text(
                            text = "HTTPS page may block http LAN discovery on some browsers.",
                            color = Color(0xFF8D6E00),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    val endpointText = cashEndpoint?.let { "${it.first}:${it.second}" } ?: "-"
                    Text("CashRegister endpoint: $endpointText")

                    OutlinedButton(
                        onClick = {
                            if (discovering) return@OutlinedButton
                            val prefix = scanPrefix.trim()
                            if (prefix.isNotEmpty() && !isIpv4Prefix(prefix)) {
                                statusText = "ERROR: Invalid LAN prefix, expected x.x.x"
                                return@OutlinedButton
                            }
                            if (prefix.isNotEmpty()) saveDiscoveryPrefix(prefix)
                            val preferredHost = sanitizeHostInput(manualHost).takeIf { isValidHost(it) }
                            val preferredPort = manualPort.toIntOrNull()
                            val targets = buildOrderingDiscoveryTargets(prefix, preferredPort, preferredHost)
                            discoveredServices = emptyList()
                            discovering = true
                            statusText = "Scanning ${targets.size} target(s)..."
                            scope.launch {
                                val found = discoverOrderingMachines(prefix, preferredPort, preferredHost)
                                discoveredServices = found
                                statusText = if (found.isEmpty()) {
                                    "No OrderingMachine discovered yet"
                                } else {
                                    "Discovered ${found.size} OrderingMachine service(s)"
                                }
                                discovering = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (discovering) {
                                tr("Discovering...", "发现中...", "Bezig met ontdekken...")
                            } else {
                                tr("Discover OrderingMachine", "发现点餐机", "Ontdek bestelmachine")
                            }
                        )
                    }

                    OutlinedTextField(
                        value = scanPrefix,
                        onValueChange = { scanPrefix = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(tr("LAN Prefix", "局域网前缀", "LAN-prefix")) },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = manualHost,
                        onValueChange = { manualHost = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Manual Host") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = manualPort,
                        onValueChange = { manualPort = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Manual Port") },
                        singleLine = true
                    )
                    Button(
                        onClick = {
                            val host = sanitizeHostInput(manualHost)
                            val port = manualPort.toIntOrNull()
                            if (host.isBlank() || port == null || port !in 1..65535) {
                                statusText = "ERROR: Invalid manual host or port"
                                return@Button
                            }
                            saveOrderingTarget(host, port)
                            scope.launch {
                                val fromDiscoveredWeb = orderingServices.any {
                                    it.host == host && it.port == port && !it.supportsRemoteConfig
                                }
                                val sameMachineTarget = isLoopbackHost(host) ||
                                    host.equals(window.location.hostname.orEmpty().trim(), ignoreCase = true)
                                val likelyManualWeb = sameMachineTarget && (
                                    isLikelyWebAppPort(port) ||
                                        port == 19082 ||
                                        port == 19080
                                    )

                                val detectedWeb = likelyManualWeb ||
                                    fromDiscoveredWeb ||
                                    (probeOrderingDiscovery(host, port, timeoutMs = 1200)?.supportsRemoteConfig == false) ||
                                    (isLikelyWebAppPort(port) && probeHttpRootNoCors(host, port, timeoutMs = 1000))
                                if (detectedWeb) {
                                    discoveredServices = (discoveredServices + WebDiscoveredService(
                                        role = "OrderingMachine",
                                        serviceName = "POSROID-OrderingMachine-Web-$port",
                                        host = host,
                                        port = port,
                                        supportsRemoteConfig = false
                                    ))
                                        .distinctBy { "${it.host}:${it.port}:${it.supportsRemoteConfig}" }
                                        .sortedWith(compareBy<WebDiscoveredService> { it.host }.thenBy { it.port })
                                    val updated = orderingConnectionAdd(connectedKeys, host, port)
                                    connectedKeys = updated
                                    saveOrderingConnectedKeys(updated)
                                    statusText = "OrderingMachine web instance selected: $host:$port (no /cashregister API)"
                                    return@launch
                                }

                                // For manual targets, only push config after /health confirms Android-style endpoint.
                                val health = fetchTextWithTimeout(
                                    url = "http://${host.trim()}:$port/health",
                                    timeoutMs = 1200
                                )
                                val noCorsHealthReachable = if (health == null && port == ORDERING_PORT && !isLoopbackHost(host)) {
                                    probeHttpPathNoCors(host = host, port = port, path = "/health", timeoutMs = 900)
                                } else {
                                    false
                                }
                                val supportsRemoteConfig = (health?.ok == true &&
                                    health.body.trim().equals("ok", ignoreCase = true)) || noCorsHealthReachable
                                if (!supportsRemoteConfig) {
                                    statusText = "ERROR: Manual target is unreachable or not an OrderingMachine /health endpoint"
                                    return@launch
                                }

                                val endpoint = cashEndpoint
                                if (endpoint == null) {
                                    statusText = "ERROR: Invalid local CashRegister endpoint"
                                    return@launch
                                }

                                statusText = "Pushing CashRegister config to OrderingMachine..."
                                val result = pushOrderingConfig(
                                    orderingHost = host,
                                    orderingPort = port,
                                    cashRegisterHost = endpoint.first,
                                    cashRegisterPort = endpoint.second
                                )
                                statusText = result
                                if (result.startsWith("OrderingMachine configured")) {
                                    discoveredServices = (discoveredServices + WebDiscoveredService(
                                        role = "OrderingMachine",
                                        serviceName = "POSROID-OrderingMachine-$port",
                                        host = host,
                                        port = port,
                                        supportsRemoteConfig = true
                                    ))
                                        .distinctBy { "${it.host}:${it.port}:${it.supportsRemoteConfig}" }
                                        .sortedWith(compareBy<WebDiscoveredService> { it.host }.thenBy { it.port })
                                    val updated = orderingConnectionAdd(connectedKeys, host, port)
                                    connectedKeys = updated
                                    saveOrderingConnectedKeys(updated)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(tr("Use Manual Target", "使用手动目标", "Gebruik handmatig doel"))
                    }

                    if (orderingServices.isEmpty()) {
                        Text(
                            text = tr(
                                "No OrderingMachine discovered yet",
                                "尚未发现点餐机",
                                "Nog geen bestelmachine gevonden"
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF6A7078)
                        )
                    } else {
                        orderingServices.forEach { svc ->
                            val isConnected = orderingConnectionIsConnected(
                                connectedKeys = connectedKeys,
                                host = svc.host,
                                port = svc.port,
                                uuid = svc.deviceUuid.orEmpty()
                            )
                            OrderingDiscoveredServiceCard(
                                service = svc,
                                isConnected = isConnected,
                                onUseTarget = useTarget@{ host, port ->
                                    saveOrderingTarget(host, port)
                                    if (!svc.supportsRemoteConfig) {
                                        val updated = orderingConnectionAdd(
                                            connectedKeys = connectedKeys,
                                            host = host,
                                            port = port,
                                            uuid = svc.deviceUuid.orEmpty()
                                        )
                                        connectedKeys = updated
                                        saveOrderingConnectedKeys(updated)
                                        statusText = "OrderingMachine web instance detected at $host:$port (no /cashregister API)"
                                        return@useTarget
                                    }
                                    val endpoint = cashEndpoint
                                    if (endpoint == null) {
                                        statusText = "ERROR: Invalid local CashRegister endpoint"
                                        return@useTarget
                                    }
                                    statusText = "Pushing CashRegister config to OrderingMachine..."
                                    scope.launch {
                                        val result = pushOrderingConfig(
                                            orderingHost = host,
                                            orderingPort = port,
                                            cashRegisterHost = endpoint.first,
                                            cashRegisterPort = endpoint.second
                                        )
                                        statusText = result
                                        if (result.startsWith("OrderingMachine configured")) {
                                            val updated = orderingConnectionAdd(
                                                connectedKeys = connectedKeys,
                                                host = host,
                                                port = port,
                                                uuid = svc.deviceUuid.orEmpty()
                                            )
                                            connectedKeys = updated
                                            saveOrderingConnectedKeys(updated)
                                        }
                                    }
                                },
                                onDisconnect = { host, port ->
                                    val updated = orderingConnectionRemove(
                                        connectedKeys = connectedKeys,
                                        host = host,
                                        port = port,
                                        uuid = svc.deviceUuid.orEmpty()
                                    )
                                    connectedKeys = updated
                                    saveOrderingConnectedKeys(updated)
                                    statusText = "Disconnected from OrderingMachine: $host:$port"
                                }
                            )
                        }
                    }

                    statusText?.let { msg ->
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (msg.startsWith("ERROR")) Color(0xFFC62828) else Color(0xFF4B4F55)
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun CallingDiscoveredServiceCard(
        service: WebDiscoveredService,
        isConnected: Boolean,
        onConnectCalling: (String, Int) -> Unit
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

    @Composable
    private fun OrderingDiscoveredServiceCard(
        service: WebDiscoveredService,
        isConnected: Boolean,
        onUseTarget: (String, Int) -> Unit,
        onDisconnect: (String, Int) -> Unit
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
                    text = service.serviceName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${service.host}:${service.port}",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (!service.supportsRemoteConfig) {
                    Text(
                        text = tr(
                            "Web instance (no /cashregister API)",
                            "Web 实例（无 /cashregister 接口）",
                            "Web-instantie (geen /cashregister API)"
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF6A7078)
                    )
                }
                if (!service.androidDeviceName.isNullOrBlank()) {
                    Text(
                        text = "${tr("Device", "设备", "Apparaat")}: ${service.androidDeviceName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF4B4F55)
                    )
                }
                if (!service.deviceUuid.isNullOrBlank()) {
                    Text(
                        text = "UUID: ${service.deviceUuid}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF4B4F55)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (isConnected) {
                        OutlinedButton(
                            onClick = { onDisconnect(service.host, service.port) }
                        ) {
                            Text(tr("Disconnect", "断开连接", "Verbreken"))
                        }
                    }
                    Button(
                        onClick = { onUseTarget(service.host, service.port) },
                        enabled = !isConnected
                    )
                    {
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

    private suspend fun pushOrderingConfig(
        orderingHost: String,
        orderingPort: Int,
        cashRegisterHost: String,
        cashRegisterPort: Int
    ): String {
        val endpoint = "http://${orderingHost.trim()}:$orderingPort/cashregister"
        val body = json.encodeToString(
            OrderingCashRegisterConfigRequest(
                host = cashRegisterHost.trim(),
                port = cashRegisterPort,
                sharedKey = null
            )
        )
        return runCatching {
            val headers = Headers()
            headers.append("Content-Type", "application/json")
            headers.append("X-Posroid-Key", POSROID_LINK_SHARED_KEY)
            val response = fetchTextWithTimeout(
                url = endpoint,
                method = "POST",
                headers = headers,
                body = body,
                timeoutMs = 1600
            ) ?: return@runCatching "ERROR: network_unreachable"
            if (!response.ok) {
                val msg = parseMessage(response.body) ?: "HTTP ${response.status}"
                return@runCatching "ERROR: $msg"
            }
            val payload = runCatching {
                json.decodeFromString<OrderingCashRegisterConfigResponse>(response.body)
            }.getOrNull()
            if (payload?.status?.equals("ok", ignoreCase = true) == true) {
                val host = payload.host?.ifBlank { cashRegisterHost.trim() } ?: cashRegisterHost.trim()
                val port = payload.port ?: cashRegisterPort
                "OrderingMachine configured: $host:$port"
            } else {
                "ERROR: ${payload?.message ?: parseMessage(response.body) ?: "invalid_response"}"
            }
        }.getOrElse { e ->
            "ERROR: ${e.message ?: e::class.simpleName.orEmpty()}"
        }
    }

    private fun parseMessage(raw: String): String? {
        if (raw.isBlank()) return null
        return runCatching {
            json.decodeFromString<OrderingCashRegisterConfigResponse>(raw).message
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private suspend fun discoverOrderingMachines(
        preferredPrefix: String = "",
        preferredPort: Int? = null,
        preferredHost: String? = null
    ): List<WebDiscoveredService> {
        fun normalizeFound(services: List<WebDiscoveredService>): List<WebDiscoveredService> {
            return services
                .distinctBy { "${it.host}:${it.port}:${it.supportsRemoteConfig}" }
                .sortedWith(compareBy<WebDiscoveredService> { it.host }.thenBy { it.port })
        }

        val strictLocalNoCorsPorts = buildStrictLocalNoCorsProbePorts(preferredPort)
        val dynamicLanPrefixes = discoverLocalLanPrefixesViaWebRtc(timeoutMs = 900).toList()

        suspend fun probeTarget(target: EndpointCandidate): WebDiscoveredService? {
            val normalizedHost = target.host.trim().lowercase()
            val currentHost = window.location.hostname.orEmpty().trim().lowercase()
            val isLocalOrEmulatorHost = isLoopbackHost(normalizedHost) ||
                normalizedHost == currentHost ||
                normalizedHost == "10.0.2.2" ||
                normalizedHost == "10.0.3.2"
            val discoveryTimeoutMs = if (isLocalOrEmulatorHost) 2600 else 1200
            val webMarkerTimeoutMs = if (isLocalOrEmulatorHost) 2200 else 1100

            // Fast-path for Android presence on LAN: check /health first on default port.
            if (!isLocalOrEmulatorHost && target.port == ORDERING_PORT) {
                val health = fetchTextWithTimeout(
                    url = "http://${target.host}:${target.port}/health",
                    timeoutMs = 900
                )
                if (health?.ok == true && health.body.trim().equals("ok", ignoreCase = true)) {
                    return WebDiscoveredService(
                        role = "OrderingMachine",
                        serviceName = "POSROID-OrderingMachine-${target.port}",
                        host = target.host,
                        port = target.port,
                        supportsRemoteConfig = true
                    )
                }
                if (health == null) {
                    val reachable = probeHttpPathNoCors(
                        host = target.host,
                        port = target.port,
                        path = "/health",
                        timeoutMs = 900
                    )
                    if (reachable) {
                        return WebDiscoveredService(
                            role = "OrderingMachine",
                            serviceName = "POSROID-OrderingMachine-${target.port}",
                            host = target.host,
                            port = target.port,
                            supportsRemoteConfig = true
                        )
                    }
                }
                return null
            }

            val discovery = probeOrderingDiscovery(target.host, target.port, timeoutMs = discoveryTimeoutMs)
            if (discovery != null) {
                val serviceNamePrefix = if (discovery.supportsRemoteConfig) {
                    "POSROID-OrderingMachine"
                } else {
                    "POSROID-OrderingMachine-Web"
                }
                return WebDiscoveredService(
                    role = "OrderingMachine",
                    serviceName = "$serviceNamePrefix-${target.port}",
                    host = target.host,
                    port = target.port,
                    supportsRemoteConfig = discovery.supportsRemoteConfig
                )
            }

            val webMarker = probeOrderingWebHtmlMarker(target.host, target.port, timeoutMs = webMarkerTimeoutMs)
            if (webMarker) {
                return WebDiscoveredService(
                    role = "OrderingMachine",
                    serviceName = "POSROID-OrderingMachine-Web-${target.port}",
                    host = target.host,
                    port = target.port,
                    supportsRemoteConfig = false
                )
            }

            // Local strict fallback for CORS-restricted web dev instances.
            if (isLocalOrEmulatorHost && target.port in strictLocalNoCorsPorts) {
                val noCorsDiscoveryReachable = probeHttpPathNoCors(
                    host = target.host,
                    port = target.port,
                    path = "/posroid-ordering.json",
                    timeoutMs = 1000
                )
                if (noCorsDiscoveryReachable) {
                    return WebDiscoveredService(
                        role = "OrderingMachine",
                        serviceName = "POSROID-OrderingMachine-Web-${target.port}",
                        host = target.host,
                        port = target.port,
                        supportsRemoteConfig = false
                    )
                }
            }

            val response = fetchTextWithTimeout(
                url = "http://${target.host}:${target.port}/health",
                timeoutMs = if (isLocalOrEmulatorHost) 1800 else 1500
            )
            if (response?.ok == true && response.body.trim().equals("ok", ignoreCase = true)) {
                return WebDiscoveredService(
                    role = "OrderingMachine",
                    serviceName = "POSROID-OrderingMachine-${target.port}",
                    host = target.host,
                    port = target.port,
                    supportsRemoteConfig = true
                )
            }

            // Browser private-network/CORS policy may hide response body even when Android endpoint is reachable.
            if (response == null && target.port == ORDERING_PORT && !isLoopbackHost(target.host)) {
                val reachable = probeHttpPathNoCors(
                    host = target.host,
                    port = target.port,
                    path = "/health",
                    timeoutMs = 1000
                )
                if (reachable) {
                    return WebDiscoveredService(
                        role = "OrderingMachine",
                        serviceName = "POSROID-OrderingMachine-${target.port}",
                        host = target.host,
                        port = target.port,
                        supportsRemoteConfig = true
                    )
                }
            }

            return null
        }

        val localTargets = buildLocalOrderingDiscoveryTargets(preferredPort = preferredPort, preferredHost = preferredHost)
        val localFound = scanTargets(localTargets, concurrency = 12, probe = ::probeTarget)
        if (localFound.isNotEmpty()) {
            return normalizeFound(localFound)
        }

        val localKeys = localTargets.asSequence().map { "${it.host}:${it.port}" }.toSet()
        val quickTargets = buildQuickOrderingDiscoveryTargets(
            preferredPrefix = preferredPrefix,
            preferredPort = preferredPort,
            preferredHost = preferredHost,
            extraPrefixes = dynamicLanPrefixes
        )
            .filterNot { "${it.host}:${it.port}" in localKeys }
        val quickFound = scanTargets(quickTargets, concurrency = 20, probe = ::probeTarget)
        if (quickFound.isNotEmpty()) {
            return normalizeFound(quickFound)
        }

        val quickKeys = (localTargets.asSequence() + quickTargets.asSequence())
            .map { "${it.host}:${it.port}" }
            .toSet()
        val broadTargets = buildOrderingDiscoveryTargets(
            preferredPrefix = preferredPrefix,
            preferredPort = preferredPort,
            preferredHost = preferredHost,
            extraPrefixes = dynamicLanPrefixes
        )
            .filterNot { "${it.host}:${it.port}" in quickKeys }
        val found = scanTargets(broadTargets, concurrency = 28, probe = ::probeTarget)
        return normalizeFound(found)
    }

    private fun buildLocalOrderingDiscoveryTargets(
        preferredPort: Int? = null,
        preferredHost: String? = null
    ): List<EndpointCandidate> {
        val hosts = LinkedHashSet<String>()
        fun addHost(raw: String?) {
            val host = sanitizeHostInput(raw)
            if (!isValidHost(host)) return
            hosts += host
        }

        addHost(preferredHost)
        addHost(loadOrderingTargetHost())
        addHost(queryParam("orderingHost"))
        addHost(queryParam("ordering_host"))
        addHost(window.location.hostname)
        addHost("localhost")
        addHost("127.0.0.1")
        addHost("10.0.2.2")
        addHost("10.0.3.2")

        val ports = LinkedHashSet<Int>().apply {
            addAll(buildOrderingPrimaryPorts(preferredPort))
            addAll(listOf(19082, 19080, 8080, 8081, 3000, 3001, 4173, 5173, 5174))
        }.filter { it in 1..65535 }

        if (hosts.isEmpty() || ports.isEmpty()) return emptyList()
        return buildLayeredEndpointCandidates(
            primaryHosts = hosts.toList().sortedBy { hostPriority(it) },
            primaryPorts = ports,
            fallbackHosts = emptyList(),
            fallbackPorts = emptyList(),
            maxTargets = 320
        )
    }

    private fun buildStrictLocalNoCorsProbePorts(preferredPort: Int?): Set<Int> {
        val ports = LinkedHashSet<Int>()
        fun addPort(value: Int?) {
            if (value != null && value in 1..65535) {
                ports += value
            }
        }

        addPort(preferredPort)
        addPort(loadOrderingTargetPort())
        addPort(queryParam("orderingPort")?.toIntOrNull())
        addPort(queryParam("ordering_port")?.toIntOrNull())
        addPort(19082)
        addPort(19080)
        return ports
    }

    private fun buildQuickOrderingDiscoveryTargets(
        preferredPrefix: String = "",
        preferredPort: Int? = null,
        preferredHost: String? = null,
        extraPrefixes: List<String> = emptyList()
    ): List<EndpointCandidate> {
        val localHosts = LinkedHashSet<String>()
        val lanHosts = LinkedHashSet<String>()

        fun addLocalHost(raw: String?) {
            val host = sanitizeHostInput(raw)
            if (!isValidHost(host)) return
            localHosts += host
        }

        addLocalHost(preferredHost)
        addLocalHost(loadOrderingTargetHost())
        addLocalHost(queryParam("orderingHost"))
        addLocalHost(queryParam("ordering_host"))
        addLocalHost(window.location.hostname)
        addLocalHost("localhost")
        addLocalHost("127.0.0.1")
        addLocalHost("::1")
        addLocalHost("10.0.2.2")
        addLocalHost("10.0.3.2")

        val prefixes = LinkedHashSet<String>()
        fun addPrefix(raw: String?) {
            val prefix = raw.orEmpty().trim()
            if (isIpv4Prefix(prefix)) prefixes += prefix
        }
        fun addPrefixFromHost(rawHost: String?) {
            val prefix = ipv4PrefixOfPrivateAddress(rawHost.orEmpty()) ?: return
            prefixes += prefix
        }
        addPrefix(preferredPrefix)
        addPrefix(queryParam("lanPrefix"))
        extraPrefixes.forEach { addPrefix(it) }
        addPrefixFromHost(preferredHost)
        addPrefixFromHost(loadOrderingTargetHost())
        addPrefixFromHost(window.location.hostname)

        prefixes.forEach { prefix ->
            for (i in 1..254) {
                val host = "$prefix.$i"
                if (isValidHost(host) && host !in localHosts) {
                    lanHosts += host
                }
            }
        }

        val localPorts = LinkedHashSet<Int>().apply {
            addAll(buildOrderingPrimaryPorts(preferredPort))
            addAll(listOf(19082, 19080, 8080, 8081, 3000, 3001, 4173, 5173, 5174))
        }.filter { it in 1..65535 }

        val lanPorts = LinkedHashSet<Int>().apply {
            addAll(buildOrderingPrimaryPorts(preferredPort))
            add(ORDERING_PORT)
        }.filter { it in 1..65535 }

        val quickLocalHosts = localHosts.toList().sortedBy { hostPriority(it) }
        val quickLanHosts = lanHosts.toList().sortedBy { hostPriority(it) }
        return buildLayeredEndpointCandidates(
            primaryHosts = quickLocalHosts,
            primaryPorts = localPorts,
            fallbackHosts = quickLanHosts,
            fallbackPorts = lanPorts,
            maxTargets = 1600
        )
    }

    private suspend fun discoverCallingMachines(
        preferredPrefix: String = "",
        preferredPort: Int? = null,
        preferredHost: String? = null
    ): List<WebDiscoveredService> {
        val targets = buildCallingDiscoveryTargets(preferredPrefix, preferredPort, preferredHost)
        val found = scanTargets(targets) { target ->
            val reachable = probeCallingViewer(target.host, target.port, timeoutMs = 1400)
            if (!reachable) return@scanTargets null
            WebDiscoveredService(
                role = "CallingMachine",
                serviceName = "POSROID-CallingMachine-${target.port}",
                host = target.host,
                port = target.port
            )
        }
        return found
            .distinctBy { "${it.host}:${it.port}" }
            .sortedWith(compareBy<WebDiscoveredService> { it.host }.thenBy { it.port })
    }

    private suspend fun <T : Any> scanTargets(
        targets: List<EndpointCandidate>,
        concurrency: Int = 24,
        probe: suspend (EndpointCandidate) -> T?
    ): List<T> = coroutineScope {
        if (targets.isEmpty()) return@coroutineScope emptyList()
        val semaphore = Semaphore(concurrency.coerceIn(4, 64))
        targets.map { target ->
            async {
                semaphore.withPermit { probe(target) }
            }
        }.awaitAll().filterNotNull()
    }

    private suspend fun fetchTextWithTimeout(
        url: String,
        method: String = "GET",
        headers: Headers? = null,
        body: String? = null,
        timeoutMs: Int
    ): ProbeResponse? {
        val controller = AbortController()
        val timerId = window.setTimeout(
            handler = { controller.abort() },
            timeout = timeoutMs
        )
        return try {
            val init = RequestInit(
                method = method,
                headers = headers,
                body = body
            )
            init.asDynamic().signal = controller.signal
            val response = window.fetch(url, init).await()
            val raw = runCatching { response.text().await() }.getOrDefault("")
            ProbeResponse(
                ok = response.ok,
                status = response.status.toInt(),
                body = raw
            )
        } catch (_: Throwable) {
            null
        } finally {
            window.clearTimeout(timerId)
        }
    }

    private suspend fun probeCallingViewer(
        host: String,
        port: Int,
        timeoutMs: Int
    ): Boolean = suspendCancellableCoroutine { continuation ->
        val scheme = if (window.location.protocol == "https:") "wss" else "ws"
        val socket = runCatching {
            WebSocket("$scheme://$host:$port/?mode=viewer")
        }.getOrNull()

        if (socket == null) {
            continuation.resume(false)
            return@suspendCancellableCoroutine
        }

        var finished = false
        var timeoutId = 0
        fun finish(ok: Boolean) {
            if (finished) return
            finished = true
            window.clearTimeout(timeoutId)
            runCatching { socket.close() }
            if (continuation.isActive) continuation.resume(ok)
        }

        timeoutId = window.setTimeout(
            handler = { finish(false) },
            timeout = timeoutMs
        )
        socket.onopen = { _: Event ->
            finish(true)
            null
        }
        socket.onerror = { _: Event ->
            finish(false)
            null
        }
        socket.onclose = { _: Event ->
            if (!finished) finish(false)
            null
        }
        continuation.invokeOnCancellation {
            window.clearTimeout(timeoutId)
            runCatching { socket.close() }
        }
    }

    private fun resolveDefaultCashRegisterEndpoint(): Pair<String, Int>? {
        val host = queryParam("cashregisterHost")
            ?: queryParam("cash_register_host")
            ?: window.location.hostname
            ?: loadOrderingTargetHost()
        val cleanHost = host.orEmpty().trim()
        if (!isValidHost(cleanHost)) return null
        val queryPort = queryParam("cashregisterPort")?.toIntOrNull()
            ?: queryParam("cash_register_port")?.toIntOrNull()
        val browserPort = window.location.port.orEmpty().toIntOrNull()
        val port = queryPort
            ?.takeIf { it in 1..65535 }
            ?: browserPort?.takeIf { it in 1..65535 }
            ?: 8080
        return cleanHost to port
    }

    private fun buildDiscoveryHostList(
        preferredPrefix: String = "",
        preferredHost: String? = null,
        extraPrefixes: List<String> = emptyList()
    ): List<String> {
        val hosts = LinkedHashSet<String>()
        val prefixes = LinkedHashSet<String>()

        fun addPrefix(rawPrefix: String?) {
            val prefix = rawPrefix.orEmpty().trim()
            if (!isIpv4Prefix(prefix)) return
            prefixes.add(prefix)
        }

        fun addPrefixFromHost(rawHost: String?) {
            val host = rawHost.orEmpty().trim()
            val prefix = ipv4PrefixOfPrivateAddress(host) ?: return
            prefixes.add(prefix)
        }

        fun addHost(raw: String?) {
            val host = sanitizeHostInput(raw)
            if (!isValidHost(host)) return
            hosts.add(host)
        }

        addHost(queryParam("scanHost"))
        queryParam("scanHosts")
            ?.split(',')
            ?.forEach { addHost(it) }
        addHost(preferredHost)
        addPrefix(preferredPrefix)
        addPrefix(queryParam("lanPrefix"))
        extraPrefixes.forEach { addPrefix(it) }

        addHost("localhost")
        addHost("127.0.0.1")
        addHost("::1")
        addHost("10.0.2.2")
        addHost("10.0.3.2")
        addHost(queryParam("callingHost"))
        addHost(queryParam("calling_host"))
        addHost(queryParam("orderingHost"))
        addHost(queryParam("ordering_host"))
        addHost(window.location.hostname)
        addHost(CallingPlatform.bridgeStatus.value.targetHost)
        addHost(loadOrderingTargetHost())

        addPrefixFromHost(preferredHost)
        addPrefixFromHost(queryParam("callingHost"))
        addPrefixFromHost(queryParam("calling_host"))
        addPrefixFromHost(queryParam("orderingHost"))
        addPrefixFromHost(queryParam("ordering_host"))
        addPrefixFromHost(window.location.hostname)
        addPrefixFromHost(CallingPlatform.bridgeStatus.value.targetHost)
        addPrefixFromHost(loadOrderingTargetHost())

        if ("192.168.1" in prefixes) prefixes.add("192.168.0")
        if ("192.168.0" in prefixes) prefixes.add("192.168.1")
        if ("10.0.0" in prefixes) prefixes.add("10.0.1")
        if ("10.0.1" in prefixes) prefixes.add("10.0.0")

        if (prefixes.isEmpty()) {
            // Common home/office LAN prefixes + Android hotspot default.
            orderingDiscoveryDefaultLanPrefixes().forEach { addPrefix(it) }
        }

        prefixes.forEach { prefix ->
            addPrefixRange(hosts, prefix, maxHosts = 254)
        }

        return hosts.toList()
    }

    private fun buildOrderingDiscoveryTargets(
        preferredPrefix: String = "",
        preferredPort: Int? = null,
        preferredHost: String? = null,
        extraPrefixes: List<String> = emptyList()
    ): List<EndpointCandidate> {
        val hosts = buildDiscoveryHostList(
            preferredPrefix = preferredPrefix,
            preferredHost = preferredHost,
            extraPrefixes = extraPrefixes
        ).sortedBy { hostPriority(it) }
        val primaryPorts = buildOrderingPrimaryPorts(preferredPort)
        val fallbackPorts = buildOrderingFallbackPorts(primaryPorts)
        val fallbackHosts = buildFallbackDiscoveryHosts(hosts, preferredHost, limit = 24)
        return buildLayeredEndpointCandidates(
            primaryHosts = hosts,
            primaryPorts = primaryPorts,
            fallbackHosts = fallbackHosts,
            fallbackPorts = fallbackPorts
        )
    }

    private fun buildCallingDiscoveryTargets(
        preferredPrefix: String = "",
        preferredPort: Int? = null,
        preferredHost: String? = null
    ): List<EndpointCandidate> {
        val hosts = buildDiscoveryHostList(preferredPrefix, preferredHost).sortedBy { hostPriority(it) }
        val primaryPorts = buildCallingPrimaryPorts(preferredPort)
        val fallbackPorts = buildCallingFallbackPorts(primaryPorts)
        val fallbackHosts = buildFallbackDiscoveryHosts(hosts, preferredHost, limit = 20)
        return buildLayeredEndpointCandidates(
            primaryHosts = hosts,
            primaryPorts = primaryPorts,
            fallbackHosts = fallbackHosts,
            fallbackPorts = fallbackPorts
        )
    }

    private fun buildFallbackDiscoveryHosts(
        orderedHosts: List<String>,
        preferredHost: String?,
        limit: Int
    ): List<String> {
        if (orderedHosts.isEmpty()) return emptyList()
        val hostSet = orderedHosts.toSet()
        val selected = LinkedHashSet<String>()

        fun addHost(raw: String?) {
            val clean = sanitizeHostInput(raw)
            if (clean.isBlank()) return
            if (clean !in hostSet) return
            selected += clean
        }

        addHost(preferredHost)
        addHost(window.location.hostname)
        addHost(loadOrderingTargetHost())
        addHost(CallingPlatform.bridgeStatus.value.targetHost)
        addHost(queryParam("callingHost"))
        addHost(queryParam("calling_host"))
        addHost(queryParam("orderingHost"))
        addHost(queryParam("ordering_host"))
        orderedHosts.forEach { host ->
            if (isLoopbackHost(host)) selected += host
        }
        if (selected.size < limit) {
            orderedHosts.forEach { host ->
                if (selected.size >= limit) return@forEach
                selected += host
            }
        }
        return selected.toList()
    }

    private fun buildLayeredEndpointCandidates(
        primaryHosts: List<String>,
        primaryPorts: List<Int>,
        fallbackHosts: List<String>,
        fallbackPorts: List<Int>,
        maxTargets: Int = 3200
    ): List<EndpointCandidate> {
        return buildLayeredOrderingEndpointCandidates(
            primaryHosts = primaryHosts,
            primaryPorts = primaryPorts,
            fallbackHosts = fallbackHosts,
            fallbackPorts = fallbackPorts,
            maxTargets = maxTargets
        ).map { EndpointCandidate(host = it.host, port = it.port) }
    }

    private fun buildOrderingPrimaryPorts(preferredPort: Int? = null): List<Int> {
        val ports = LinkedHashSet<Int>()
        fun addPort(value: Int?) {
            if (value != null && value in 1..65535) ports += value
        }

        ports.addAll(
            buildOrderingPrimaryPortCandidates(
                preferredPort = preferredPort,
                savedPort = loadOrderingTargetPort(),
                defaultPort = ORDERING_PORT
            )
        )
        addPort(queryParam("orderingPort")?.toIntOrNull())
        addPort(queryParam("ordering_port")?.toIntOrNull())
        addPort(queryParam("scanPort")?.toIntOrNull())
        queryParam("scanPorts")
            ?.split(',')
            ?.forEach { addPort(it.trim().toIntOrNull()) }
        return ports.toList()
    }

    private fun buildOrderingFallbackPorts(primaryPorts: List<Int>): List<Int> {
        return buildOrderingWebFallbackPortCandidates(
            primaryPorts = primaryPorts,
            preferredPort = null,
            defaultPort = ORDERING_PORT
        )
    }

    private fun buildCallingPrimaryPorts(preferredPort: Int? = null): List<Int> {
        val ports = LinkedHashSet<Int>()
        fun addPort(value: Int?) {
            if (value != null && value in 1..65535) ports += value
        }

        addPort(preferredPort)
        addPort(queryParam("callingPort")?.toIntOrNull())
        addPort(queryParam("calling_port")?.toIntOrNull())
        addPort(queryParam("scanPort")?.toIntOrNull())
        queryParam("scanPorts")
            ?.split(',')
            ?.forEach { addPort(it.trim().toIntOrNull()) }

        addPort(CALLING_PORT)
        return ports.toList()
    }

    private fun buildCallingFallbackPorts(primaryPorts: List<Int>): List<Int> {
        val ports = LinkedHashSet<Int>()
        for (delta in -3..3) {
            val value = CALLING_PORT + delta
            if (value in 1..65535) ports += value
        }
        return ports.filterNot { it in primaryPorts }
    }

    private fun hostPriority(host: String): Int {
        return orderingDiscoveryHostPriority(host = host, currentHost = window.location.hostname)
    }

    private fun isLoopbackHost(host: String): Boolean {
        return isOrderingLoopbackHost(host)
    }

    private fun sanitizeHostInput(raw: String?): String {
        return normalizeOrderingDiscoveryHost(raw)
    }

    private fun isLikelyWebAppPort(port: Int): Boolean {
        return port in listOf(19080, 19082, 3000, 3001, 4173, 5173, 5174, 8080, 8081, 8082)
    }

    private suspend fun probeOrderingDiscovery(host: String, port: Int, timeoutMs: Int): OrderingDiscoveryResult? {
        val response = fetchTextWithTimeout(
            url = "http://$host:$port/posroid-ordering.json",
            timeoutMs = timeoutMs
        ) ?: return null
        if (!response.ok) return null
        val payload = response.body.lowercase()
        if (!payload.contains("\"orderingmachine\"")) return null
        val supportsRemoteConfig = when {
            payload.contains("\"supportscashregisterconfigpush\":true") -> true
            payload.contains("\"supportscashregisterconfigpush\":false") -> false
            payload.contains("\"platform\"") && payload.contains("\"android\"") -> true
            payload.contains("\"platform\"") && payload.contains("\"web\"") -> false
            else -> false
        }
        return OrderingDiscoveryResult(supportsRemoteConfig = supportsRemoteConfig)
    }

    private suspend fun probeOrderingWebHtmlMarker(host: String, port: Int, timeoutMs: Int): Boolean {
        if (!isLikelyWebAppPort(port)) return false
        val root = fetchTextWithTimeout(
            url = "http://$host:$port/",
            timeoutMs = timeoutMs
        )
        if (isOrderingWebHtml(root?.body.orEmpty())) return true
        val index = fetchTextWithTimeout(
            url = "http://$host:$port/index.html",
            timeoutMs = timeoutMs
        )
        if (isOrderingWebHtml(index?.body.orEmpty())) return true

        // CORS-restricted static hosting fallback for local OrderingMachine web endpoint.
        val isLocalHost = isLoopbackHost(host) || host.equals(window.location.hostname.orEmpty().trim(), ignoreCase = true)
        if (isLocalHost && port == 19082) {
            return probeHttpPathNoCors(host = host, port = port, path = "/posroid-ordering.json", timeoutMs = timeoutMs)
        }
        return false
    }

    private fun isOrderingWebHtml(raw: String): Boolean {
        if (raw.isBlank()) return false
        val body = raw.lowercase()
        val hasOrderingTitle = body.contains("<title>orderingmachine web</title>") ||
            body.contains("loading orderingmachine web")
        val hasOrderingBundle = body.contains("orderingmachine.js")
        return hasOrderingTitle && hasOrderingBundle
    }

    private suspend fun probeHttpRootNoCors(host: String, port: Int, timeoutMs: Int): Boolean {
        return probeHttpPathNoCors(host = host, port = port, path = "/", timeoutMs = timeoutMs)
    }

    private suspend fun probeHttpPathNoCors(host: String, port: Int, path: String, timeoutMs: Int): Boolean {
        val controller = AbortController()
        val timerId = window.setTimeout(
            handler = { controller.abort() },
            timeout = timeoutMs
        )
        return try {
            val init = RequestInit(method = "GET")
            init.asDynamic().mode = "no-cors"
            init.asDynamic().signal = controller.signal
            val normalizedPath = if (path.startsWith("/")) path else "/$path"
            window.fetch("http://$host:$port$normalizedPath", init).await()
            true
        } catch (_: Throwable) {
            false
        } finally {
            window.clearTimeout(timerId)
        }
    }

    private suspend fun discoverLocalLanPrefixesViaWebRtc(timeoutMs: Int): Set<String> {
        val ctor = js("window.RTCPeerConnection || window.webkitRTCPeerConnection || window.mozRTCPeerConnection")
        if (jsTypeOf(ctor) == "undefined") return emptySet()

        val prefixes = LinkedHashSet<String>()
        val rtc = runCatching {
            js("new ctor({iceServers: []})")
        }.getOrNull() ?: return emptySet()

        fun collectPrefix(raw: String?) {
            if (raw.isNullOrBlank()) return
            val matches = Regex("(\\d{1,3}(?:\\.\\d{1,3}){3})").findAll(raw)
            matches.forEach { match ->
                val prefix = ipv4PrefixOfPrivateAddress(match.value)
                if (prefix != null) prefixes += prefix
            }
        }

        runCatching {
            rtc.onicecandidate = { event: dynamic ->
                val candidate = event?.candidate
                if (candidate != null) {
                    collectPrefix(candidate.candidate?.toString())
                    collectPrefix(candidate.address?.toString())
                }
            }
            rtc.createDataChannel("posroid-lan-probe")
        }

        runCatching {
            val offer = rtc.createOffer().await()
            rtc.setLocalDescription(offer).await()
            delay(timeoutMs.toLong())
        }

        runCatching { rtc.onicecandidate = null }
        runCatching { rtc.close() }
        return prefixes
    }

    private fun addPrefixRange(
        hosts: LinkedHashSet<String>,
        prefix: String,
        maxHosts: Int = 254
    ) {
        if (!isIpv4Prefix(prefix)) return
        val cap = maxHosts.coerceIn(1, 254)
        for (suffix in 1..cap) {
            hosts += "$prefix.$suffix"
        }
    }

    private fun saveOrderingTarget(host: String, port: Int) {
        runCatching {
            window.localStorage.setItem(ORDERING_TARGET_HOST_KEY, host.trim())
            window.localStorage.setItem(ORDERING_TARGET_PORT_KEY, port.toString())
        }
    }

    private fun loadOrderingTargetHost(): String? {
        return runCatching {
            window.localStorage.getItem(ORDERING_TARGET_HOST_KEY)
        }.getOrNull()?.trim()?.takeIf { isValidHost(it) }
    }

    private fun loadOrderingTargetPort(): Int? {
        return runCatching {
            window.localStorage.getItem(ORDERING_TARGET_PORT_KEY)?.toIntOrNull()
        }.getOrNull()?.takeIf { it in 1..65535 }
    }

    private fun loadOrderingConnectedKeys(): Set<String> {
        val raw = runCatching {
            window.localStorage.getItem(ORDERING_CONNECTED_KEYS)
        }.getOrNull().orEmpty()
        if (raw.isBlank()) return emptySet()
        return raw.split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()
    }

    private fun saveOrderingConnectedKeys(keys: Set<String>) {
        runCatching {
            window.localStorage.setItem(
                ORDERING_CONNECTED_KEYS,
                keys.sorted().joinToString(separator = ",")
            )
        }
    }

    private fun saveDiscoveryPrefix(prefix: String) {
        val clean = prefix.trim()
        if (!isIpv4Prefix(clean)) return
        runCatching { window.localStorage.setItem(DISCOVERY_PREFIX_KEY, clean) }
    }

    private fun loadDiscoveryPrefix(): String? {
        return runCatching { window.localStorage.getItem(DISCOVERY_PREFIX_KEY) }
            .getOrNull()
            ?.trim()
            ?.takeIf { isIpv4Prefix(it) }
    }

    private fun defaultDiscoveryPrefix(): String {
        val candidates = listOf(
            loadOrderingTargetHost(),
            CallingPlatform.bridgeStatus.value.targetHost,
            queryParam("orderingHost"),
            queryParam("ordering_host"),
            queryParam("callingHost"),
            queryParam("calling_host"),
            window.location.hostname
        )
        candidates.forEach { host ->
            val prefix = ipv4PrefixOfPrivateAddress(host.orEmpty())
            if (prefix != null) return prefix
        }
        return ""
    }

    private fun isValidHost(host: String): Boolean {
        return isValidOrderingDiscoveryHost(host)
    }

    private fun isIpv4Address(host: String): Boolean {
        return isOrderingIpv4Address(host)
    }

    private fun isIpv4Prefix(prefix: String): Boolean {
        return isOrderingIpv4Prefix(prefix)
    }

    private fun ipv4PrefixOfPrivateAddress(host: String): String? {
        return orderingIpv4PrefixOfPrivateAddress(host)
    }

    private fun isPrivateIpv4Address(host: String): Boolean {
        return orderingIpv4PrefixOfPrivateAddress(host) != null
    }

    private fun queryParam(name: String): String? {
        val raw = window.location.search.orEmpty().removePrefix("?").trim()
        if (raw.isBlank()) return null
        return raw.split("&")
            .asSequence()
            .mapNotNull { pair ->
                val idx = pair.indexOf('=')
                if (idx < 0) return@mapNotNull null
                val key = decodeURIComponent(pair.substring(0, idx).trim())
                val value = decodeURIComponent(pair.substring(idx + 1).trim())
                if (key.isBlank()) null else key to value
            }
            .firstOrNull { it.first == name }
            ?.second
            ?.takeIf { it.isNotBlank() }
    }

    private object WebPaymentDebugActions : PaymentDebugActions {
        override val alertSoundOptions: List<String> = listOf("OFF", "BEEP", "BEEP2", "ACK", "NACK")

        override fun loadAlertSoundId(): String = alertSoundId

        override fun saveAlertSoundId(id: String) {
            alertSoundId = id
        }

        override fun loadForceTestAmount(): Boolean = forceTestAmount

        override fun saveForceTestAmount(enabled: Boolean) {
            forceTestAmount = enabled
        }

        override suspend fun resetMenuPricesToDefault(): Result<Int> {
            return runCatching {
                val seed = MenuSeed.dishes
                val seedIds = seed.mapTo(LinkedHashSet()) { it.id }
                val currentIds = CashRegisterPlatform.dishes.value.mapTo(LinkedHashSet()) { it.id }

                seed.forEach { dish ->
                    CashRegisterPlatform.upsertDish(dish.copy(soldOut = false, discountedPrice = dish.priceEur))
                }

                (currentIds - seedIds).forEach { id ->
                    CashRegisterPlatform.deleteDish(id)
                }
                seed.size
            }
        }

        override suspend fun runMockPayment(amount: Double): String {
            val effective = if (forceTestAmount) 0.01 else amount
            val ref = "WEB_MOCK_${nowMillis()}"
            return "OK: MOCK accepted ref=$ref status=00 amount=$effective"
        }

        override fun playAlertSound() {
            // No-op in web build.
        }
    }

    private object WebPrinterDebugActions : PrinterDebugActions {
        override suspend fun print(kind: DebugPrinterKind, order: OrderPayload, callNumber: String?): Boolean {
            when (kind) {
                DebugPrinterKind.ORDER -> CashRegisterPlatform.printOrder(order)
                DebugPrinterKind.RECEIPT -> CashRegisterPlatform.printReceipt(order)
                DebugPrinterKind.KITCHEN -> CashRegisterPlatform.printKitchen(order)
            }
            return true
        }
    }
}

private external fun decodeURIComponent(encodedURI: String): String
