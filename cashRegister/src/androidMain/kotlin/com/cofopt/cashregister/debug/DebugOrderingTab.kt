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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cofopt.cashregister.cmp.debug.orderingConnectionAdd
import com.cofopt.cashregister.cmp.debug.buildOrderingPrimaryPortCandidates
import com.cofopt.cashregister.cmp.debug.buildOrderingWebFallbackPortCandidates
import com.cofopt.cashregister.cmp.debug.orderingConnectionIsConnected
import com.cofopt.cashregister.cmp.debug.orderingConnectionPrimaryKey
import com.cofopt.cashregister.cmp.debug.orderingConnectionRemove
import com.cofopt.cashregister.cmp.debug.normalizeOrderingDiscoveryHost
import com.cofopt.cashregister.cmp.debug.orderingIpv4PrefixOfPrivateAddress
import com.cofopt.cashregister.cmp.debug.sanitizeOrderingConnectionUuid
import com.cofopt.cashregister.cmp.debug.isValidOrderingDiscoveryHost
import com.cofopt.cashregister.network.ComposeXPOSDiscoveredService
import com.cofopt.cashregister.network.ComposeXPOSNsdAdvertiser
import com.cofopt.cashregister.network.ComposeXPOSNsdBrowser
import com.cofopt.cashregister.network.getLocalIpv4Address
import com.cofopt.cashregister.utils.tr
import com.cofopt.shared.network.OrderingCashRegisterConfigRequest
import com.cofopt.shared.network.OrderingCashRegisterConfigResponse
import com.cofopt.shared.network.POSROID_LINK_SHARED_KEY
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.serialization.json.Json

@Composable
internal fun OrderingMachinesTab() {
    val context = LocalContext.current
    val nsdBrowser = remember(context) { ComposeXPOSNsdBrowser(context) }
    val discoveredServices by nsdBrowser.services.collectAsState()
    var lanDiscoveredServices by remember { mutableStateOf<List<ComposeXPOSDiscoveredService>>(emptyList()) }
    val nsdOrderingServices = discoveredServices.filter { it.role == "OrderingMachine" }
    val orderingServices = remember(nsdOrderingServices, lanDiscoveredServices) {
        mergeOrderingServices(nsdOrderingServices, lanDiscoveredServices)
    }
    var connectedKeys by remember { mutableStateOf(CashRegisterDebugConfig.orderingMachineConnectedKeys(context)) }
    var statusText by remember { mutableStateOf<String?>(null) }
    var manualHost by remember { mutableStateOf(CashRegisterDebugConfig.orderingMachineIp(context).ifBlank { "" }) }
    var manualPort by remember {
        mutableStateOf(
            CashRegisterDebugConfig.orderingMachinePort(context)
                .takeIf { it in 1..65535 }
                ?.toString()
                ?: "19081"
        )
    }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        nsdBrowser.start()
        val preferredHost = normalizeOrderingHostInput(manualHost).takeIf { it.isNotBlank() }
        val preferredPort = manualPort.toIntOrNull()
        lanDiscoveredServices = withContext(Dispatchers.IO) {
            discoverOrderingMachinesOverLan(
                context = context,
                preferredHost = preferredHost,
                preferredPort = preferredPort
            )
        }
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
                    text = tr("Ordering Machines", "点餐机", "Bestelmachines"),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                OutlinedButton(
                    onClick = {
                        nsdBrowser.start()
                        val preferredHost = normalizeOrderingHostInput(manualHost).takeIf { it.isNotBlank() }
                        val preferredPort = manualPort.toIntOrNull()
                        statusText = "Discovering services..."
                        scope.launch {
                            val lan = withContext(Dispatchers.IO) {
                                discoverOrderingMachinesOverLan(
                                    context = context,
                                    preferredHost = preferredHost,
                                    preferredPort = preferredPort
                                )
                            }
                            lanDiscoveredServices = lan
                            statusText = if (lan.isEmpty()) {
                                "LAN scan finished, no extra instances found"
                            } else {
                                "LAN scan found ${lan.size} OrderingMachine instance(s)"
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(tr("Discover OrderingMachine", "发现点餐机", "Ontdek bestelmachine"))
                }

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
                        val host = normalizeOrderingHostInput(manualHost)
                        val port = manualPort.toIntOrNull()
                        if (host.isBlank() || port == null || port !in 1..65535) {
                            statusText = "ERROR: Invalid manual host or port"
                            return@Button
                        }
                        CashRegisterDebugConfig.saveOrderingMachine(context, host, port)
                        val cashHost = getLocalIpv4Address()?.trim().orEmpty()
                        if (cashHost.isBlank()) {
                            statusText = "OrderingMachine target saved, but local CashRegister IP is unavailable"
                            return@Button
                        }
                        statusText = "Pushing CashRegister config to OrderingMachine..."
                        scope.launch {
                            val pushResult = withContext(Dispatchers.IO) {
                                pushCashRegisterConfigToOrderingMachine(
                                    context = context,
                                    orderingHost = host,
                                    orderingPort = port,
                                    orderingUuid = "",
                                    cashRegisterHost = cashHost,
                                    cashRegisterPort = 8080
                                )
                            }
                            statusText = pushResult
                            if (pushResult.startsWith("OrderingMachine configured")) {
                                val updated = orderingConnectionAdd(
                                    connectedKeys = connectedKeys,
                                    host = host,
                                    port = port
                                )
                                connectedKeys = updated
                                CashRegisterDebugConfig.saveOrderingMachineConnectedKeys(context, updated)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(tr("Use Manual Target", "使用手动目标", "Gebruik handmatig doel"))
                }

                if (orderingServices.isEmpty()) {
                    Text(
                        text = tr("No OrderingMachine discovered yet", "尚未发现点餐机", "Nog geen bestelmachine gevonden"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF6A7078)
                    )
                } else {
                    orderingServices.forEach { svc ->
                        val isConnected = orderingConnectionIsConnected(
                            connectedKeys = connectedKeys,
                            host = svc.host,
                            port = svc.port,
                            uuid = svc.deviceUuid.orEmpty(),
                        )
                        OrderingDiscoveredServiceCard(
                            service = svc,
                            isConnected = isConnected,
                            onUseTarget = { host, port, uuid ->
                                CashRegisterDebugConfig.saveOrderingMachine(context, host, port)
                                if (!svc.supportsCashRegisterConfigPush) {
                                    val updated = orderingConnectionAdd(
                                        connectedKeys = connectedKeys,
                                        host = host,
                                        port = port,
                                        uuid = uuid
                                    )
                                    connectedKeys = updated
                                    CashRegisterDebugConfig.saveOrderingMachineConnectedKeys(context, updated)
                                    statusText = "OrderingMachine web instance selected: $host:$port (no /cashregister API)"
                                    return@OrderingDiscoveredServiceCard
                                }
                                val cashHost = getLocalIpv4Address()?.trim().orEmpty()
                                if (cashHost.isBlank()) {
                                    statusText = "OrderingMachine target saved, but local CashRegister IP is unavailable"
                                    return@OrderingDiscoveredServiceCard
                                }
                                statusText = "Pushing CashRegister config to OrderingMachine..."
                                scope.launch {
                                    val pushResult = withContext(Dispatchers.IO) {
                                        pushCashRegisterConfigToOrderingMachine(
                                            context = context,
                                            orderingHost = host,
                                            orderingPort = port,
                                            orderingUuid = uuid,
                                            cashRegisterHost = cashHost,
                                            cashRegisterPort = 8080
                                        )
                                    }
                                    statusText = pushResult
                                    if (pushResult.startsWith("OrderingMachine configured")) {
                                        val updated = orderingConnectionAdd(
                                            connectedKeys = connectedKeys,
                                            host = host,
                                            port = port,
                                            uuid = uuid
                                        )
                                        connectedKeys = updated
                                        CashRegisterDebugConfig.saveOrderingMachineConnectedKeys(context, updated)
                                    }
                                }
                            },
                            onDisconnect = { host, port, uuid ->
                                val updated = orderingConnectionRemove(
                                    connectedKeys = connectedKeys,
                                    host = host,
                                    port = port,
                                    uuid = uuid
                                )
                                connectedKeys = updated
                                CashRegisterDebugConfig.saveOrderingMachineConnectedKeys(context, updated)
                                statusText = "Disconnected from OrderingMachine: $host:$port"
                            },
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
internal fun OrderingDiscoveredServiceCard(
    service: ComposeXPOSDiscoveredService,
    isConnected: Boolean,
    onUseTarget: (String, Int, String) -> Unit,
    onDisconnect: (String, Int, String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(0.45f),
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
            if (!service.supportsCashRegisterConfigPush) {
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
                        onClick = {
                            val uuid = service.deviceUuid.orEmpty()
                            onDisconnect(service.host, service.port, uuid)
                        }
                    ) {
                        Text(tr("Disconnect", "断开连接", "Verbreken"))
                    }
                }
                Button(
                    onClick = {
                        val uuid = service.deviceUuid.orEmpty()
                        onUseTarget(service.host, service.port, uuid)
                    },
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

private val linkJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

internal fun queryOrderingMachineCashRegisterConfig(orderingHost: String, orderingPort: Int): Pair<String, Int>? {
    if (orderingHost.isBlank() || orderingPort <= 0) return null
    return runCatching {
        val conn = (URL("http://${orderingHost.trim()}:$orderingPort/cashregister").openConnection() as HttpURLConnection)
        try {
            conn.requestMethod = "GET"
            conn.connectTimeout = 1500
            conn.readTimeout = 1500
            conn.setRequestProperty("Accept", "application/json")
            val code = conn.responseCode
            if (code !in 200..299) return@runCatching null
            val raw = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val payload = runCatching {
                linkJson.decodeFromString<OrderingCashRegisterConfigResponse>(raw)
            }.getOrNull() ?: return@runCatching null
            val host = payload.host?.trim().orEmpty()
            val port = payload.port ?: -1
            if (payload.configured == false || host.isBlank() || port <= 0) return@runCatching null
            host to port
        } finally {
            conn.disconnect()
        }
    }.getOrNull()
}

internal fun pushCashRegisterConfigToOrderingMachine(
    context: android.content.Context,
    orderingHost: String,
    orderingPort: Int,
    orderingUuid: String,
    cashRegisterHost: String,
    cashRegisterPort: Int
): String {
    if (orderingHost.isBlank() || orderingPort <= 0) {
        return "ERROR: Invalid OrderingMachine target"
    }
    val localIp = getLocalIpv4Address()?.trim().orEmpty()
    if (localIp.isNotBlank() && orderingHost.trim() == localIp) {
        return "ERROR: Invalid target (self host): $orderingHost:$orderingPort"
    }
    if (cashRegisterHost.isBlank() || cashRegisterPort <= 0) {
        return "ERROR: Invalid CashRegister endpoint"
    }
    val request = OrderingCashRegisterConfigRequest(
        host = cashRegisterHost.trim(),
        port = cashRegisterPort,
        sharedKey = null
    )
    val requestBody = linkJson.encodeToString(request)
    return runCatching {
        val conn = (URL("http://${orderingHost.trim()}:$orderingPort/cashregister").openConnection() as HttpURLConnection)
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 1800
            conn.readTimeout = 1800
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("X-Posroid-Key", POSROID_LINK_SHARED_KEY)
            conn.outputStream.use { os ->
                os.write(requestBody.toByteArray(Charsets.UTF_8))
            }
            val code = conn.responseCode
            val responseText = runCatching {
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            }.getOrDefault("")

            if (code !in 200..299) {
                val message = parseOrderingLinkMessage(responseText)
                return@runCatching "OrderingMachine target saved, remote apply failed: HTTP $code${message?.let { " ($it)" } ?: ""}"
            }

            val payload = runCatching {
                linkJson.decodeFromString<OrderingCashRegisterConfigResponse>(responseText)
            }.getOrNull()
            if (payload?.status?.equals("ok", ignoreCase = true) != true) {
                val message = payload?.message ?: parseOrderingLinkMessage(responseText) ?: "invalid_response"
                return@runCatching "OrderingMachine target saved, remote apply failed: $message"
            }

            if (orderingUuid.isNotBlank()) {
                CashRegisterDebugConfig.saveOrderingMachineKnownUuid(
                    context = context,
                    host = orderingHost.trim(),
                    port = orderingPort,
                    uuid = orderingUuid
                )
            }
            val savedHost = payload.host?.takeIf { it.isNotBlank() } ?: request.host
            val savedPort = payload.port ?: request.port
            "OrderingMachine configured: $savedHost:$savedPort"
        } finally {
            conn.disconnect()
        }
    }.getOrElse { e ->
        "OrderingMachine target saved, remote apply failed: ${e.javaClass.simpleName}: ${e.message}"
    }
}

private fun parseOrderingLinkMessage(raw: String): String? {
    if (raw.isBlank()) return null
    return runCatching {
        linkJson.decodeFromString<OrderingCashRegisterConfigResponse>(raw).message
    }.getOrNull()?.takeIf { it.isNotBlank() }
}

internal fun orderingMachineKey(host: String, port: Int, uuid: String): String {
    return orderingConnectionPrimaryKey(host = host, port = port, uuid = uuid)
}

internal fun isOrderingMachineConnected(
    connectedKeys: Set<String>,
    host: String,
    port: Int,
    uuid: String
): Boolean {
    return orderingConnectionIsConnected(connectedKeys = connectedKeys, host = host, port = port, uuid = uuid)
}

internal fun sanitizeUuid(raw: String): String {
    return sanitizeOrderingConnectionUuid(raw)
}

private fun mergeOrderingServices(
    nsdServices: List<ComposeXPOSDiscoveredService>,
    lanServices: List<ComposeXPOSDiscoveredService>
): List<ComposeXPOSDiscoveredService> {
    if (nsdServices.isEmpty()) {
        return lanServices.sortedWith(compareBy<ComposeXPOSDiscoveredService> { it.host }.thenBy { it.port })
    }
    if (lanServices.isEmpty()) {
        return nsdServices.sortedWith(compareBy<ComposeXPOSDiscoveredService> { it.host }.thenBy { it.port })
    }

    val merged = LinkedHashMap<String, ComposeXPOSDiscoveredService>()
    (nsdServices + lanServices).forEach { svc ->
        val key = "${svc.host.trim()}:${svc.port}"
        val existing = merged[key]
        if (existing == null) {
            merged[key] = svc
            return@forEach
        }
        if (!existing.supportsCashRegisterConfigPush && svc.supportsCashRegisterConfigPush) {
            merged[key] = svc
            return@forEach
        }
        if (existing.deviceUuid.isNullOrBlank() && !svc.deviceUuid.isNullOrBlank()) {
            merged[key] = svc
        }
    }
    return merged.values
        .sortedWith(compareBy<ComposeXPOSDiscoveredService> { it.host }.thenBy { it.port })
}

private suspend fun discoverOrderingMachinesOverLan(
    context: android.content.Context,
    preferredHost: String? = null,
    preferredPort: Int? = null
): List<ComposeXPOSDiscoveredService> {
    val localIp = getLocalIpv4Address()?.trim().orEmpty()
    val preferred = normalizeOrderingHostInput(preferredHost).takeIf { it.isNotBlank() }
    val seedHosts = linkedSetOf<String>().apply {
        preferred?.let { add(it) }
        add(normalizeOrderingHostInput(CashRegisterDebugConfig.orderingMachineIp(context)))
        add(localIp)
        add("10.0.2.2")
        add("10.0.3.2")
        add("127.0.0.1")
        add("localhost")
    }
    val hosts = buildOrderingLanHostCandidates(localIp = localIp, seeds = seedHosts)
    if (hosts.isEmpty()) return emptyList()

    val primaryPorts = buildOrderingPrimaryPorts(context, preferredPort)
    val healthServices = scanOrderingTargets(
        hosts = hosts,
        ports = primaryPorts,
        concurrency = 48
    ) { host, port ->
        if (!probeOrderingHealth(host, port)) return@scanOrderingTargets null
        ComposeXPOSDiscoveredService(
            role = "OrderingMachine",
            serviceName = "POSROID-OrderingMachine",
            host = host,
            port = port,
            supportsCashRegisterConfigPush = true
        )
    }

    val merged = LinkedHashMap<String, ComposeXPOSDiscoveredService>()
    healthServices.forEach { svc ->
        merged["${svc.host}:${svc.port}"] = svc
    }

    val fallbackHosts = hosts.sortedBy { host ->
        when {
            host == preferred -> -1
            host == "localhost" || host == "127.0.0.1" -> 0
            host == localIp -> 1
            else -> 2
        }
    }
    val fallbackPorts = buildOrderingWebPorts(primaryPorts, preferredPort)
    val webServices = scanOrderingTargets(
        hosts = fallbackHosts,
        ports = fallbackPorts,
        concurrency = 32
    ) { host, port ->
        val key = "$host:$port"
        if (merged.containsKey(key)) return@scanOrderingTargets null
        if (!probeOrderingWebInstance(host, port)) return@scanOrderingTargets null
        ComposeXPOSDiscoveredService(
            role = "OrderingMachine",
            serviceName = "OrderingMachine Web",
            host = host,
            port = port,
            supportsCashRegisterConfigPush = false
        )
    }

    webServices.forEach { svc ->
        merged.putIfAbsent("${svc.host}:${svc.port}", svc)
    }
    return merged.values
        .sortedWith(compareBy<ComposeXPOSDiscoveredService> { it.host }.thenBy { it.port })
}

private suspend fun scanOrderingTargets(
    hosts: List<String>,
    ports: List<Int>,
    concurrency: Int,
    probe: suspend (host: String, port: Int) -> ComposeXPOSDiscoveredService?
): List<ComposeXPOSDiscoveredService> = coroutineScope {
    if (hosts.isEmpty() || ports.isEmpty()) return@coroutineScope emptyList()
    val semaphore = Semaphore(concurrency.coerceIn(4, 64))
    val jobs = ArrayList<kotlinx.coroutines.Deferred<ComposeXPOSDiscoveredService?>>(hosts.size * ports.size)
    hosts.forEach { host ->
        ports.forEach { port ->
            jobs += async {
                semaphore.withPermit {
                    probe(host, port)
                }
            }
        }
    }
    jobs.awaitAll().filterNotNull()
}

private fun buildOrderingPrimaryPorts(context: android.content.Context, preferredPort: Int?): List<Int> {
    val savedPort = CashRegisterDebugConfig.orderingMachinePort(context)
    return buildOrderingPrimaryPortCandidates(
        preferredPort = preferredPort,
        savedPort = savedPort.takeIf { it in 1..65535 },
        defaultPort = 19081
    )
}

private fun buildOrderingWebPorts(primaryPorts: List<Int>, preferredPort: Int?): List<Int> {
    return buildOrderingWebFallbackPortCandidates(
        primaryPorts = primaryPorts,
        preferredPort = preferredPort,
        defaultPort = 19081
    )
}

private fun buildOrderingLanHostCandidates(
    localIp: String,
    seeds: Set<String>
): List<String> {
    val localPrefix = orderingIpv4PrefixOfPrivateAddress(localIp)
    return com.cofopt.cashregister.cmp.debug.buildOrderingLanHostCandidates(
        localIp = localIp,
        seeds = seeds,
        preferredPrefix = localPrefix,
        maxHostsPerPrefix = if (localPrefix != null) 254 else 192,
        includeCommonFallback = localPrefix == null
    )
}

private fun isValidOrderingScanHost(host: String): Boolean {
    return isValidOrderingDiscoveryHost(host)
}

private fun normalizeOrderingHostInput(raw: String?): String {
    return normalizeOrderingDiscoveryHost(raw)
}

private fun probeOrderingHealth(host: String, port: Int): Boolean {
    val conn = runCatching {
        URL("http://$host:$port/health").openConnection() as HttpURLConnection
    }.getOrNull() ?: return false
    return try {
        conn.requestMethod = "GET"
        conn.connectTimeout = 450
        conn.readTimeout = 450
        conn.setRequestProperty("Accept", "text/plain")
        val code = conn.responseCode
        if (code !in 200..299) return false
        val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        body.trim().equals("ok", ignoreCase = true)
    } catch (_: Exception) {
        false
    } finally {
        runCatching { conn.disconnect() }
    }
}

private fun probeOrderingWebInstance(host: String, port: Int): Boolean {
    return probeOrderingDiscoveryJson(host, port) ||
        probeOrderingWebMarkerAtPath(host, port, "/") ||
        probeOrderingWebMarkerAtPath(host, port, "/index.html")
}

private fun probeOrderingDiscoveryJson(host: String, port: Int): Boolean {
    val conn = runCatching {
        URL("http://$host:$port/posroid-ordering.json").openConnection() as HttpURLConnection
    }.getOrNull() ?: return false
    return try {
        conn.requestMethod = "GET"
        conn.connectTimeout = 420
        conn.readTimeout = 420
        conn.setRequestProperty("Accept", "application/json,text/plain,*/*")
        val code = conn.responseCode
        if (code !in 200..299) return false
        val payload = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }.lowercase()
        payload.contains("\"orderingmachine\"") &&
            payload.contains("\"platform\"") &&
            payload.contains("\"web\"")
    } catch (_: Exception) {
        false
    } finally {
        runCatching { conn.disconnect() }
    }
}

private fun probeOrderingWebMarkerAtPath(host: String, port: Int, path: String): Boolean {
    val conn = runCatching {
        URL("http://$host:$port$path").openConnection() as HttpURLConnection
    }.getOrNull() ?: return false
    return try {
        conn.instanceFollowRedirects = true
        conn.requestMethod = "GET"
        conn.connectTimeout = 420
        conn.readTimeout = 420
        conn.setRequestProperty("Accept", "text/html,application/xhtml+xml")
        val code = conn.responseCode
        if (code !in 200..399) return false
        val contentType = conn.contentType.orEmpty().lowercase()
        if (contentType.isNotBlank() && !contentType.contains("text/html")) return false
        val snippet = conn.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
            CharArray(4096).let { buffer ->
                val read = reader.read(buffer)
                if (read <= 0) "" else String(buffer, 0, read)
            }
        }.lowercase()
        snippet.contains("orderingmachine web") ||
            snippet.contains("loading orderingmachine web") ||
            snippet.contains("orderingmachine.js")
    } catch (_: Exception) {
        false
    } finally {
        runCatching { conn.disconnect() }
    }
}
