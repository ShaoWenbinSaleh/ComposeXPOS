package com.cofopt.cashregister.cmp.debug

private val ORDERING_COMMON_LAN_PREFIXES = listOf(
    "192.168.1",
    "192.168.0",
    "192.168.31",
    "192.168.50",
    "192.168.68",
    "192.168.86",
    "192.168.100",
    "192.168.178",
    "10.0.0",
    "10.0.1",
    "172.20.10"
)

data class OrderingDiscoveryEndpoint(
    val host: String,
    val port: Int
)

fun orderingDiscoveryDefaultLanPrefixes(): List<String> = ORDERING_COMMON_LAN_PREFIXES

fun normalizeOrderingDiscoveryHost(raw: String?): String {
    var clean = raw.orEmpty().trim()
    if (clean.isBlank()) return ""

    clean = clean.removePrefix("http://").removePrefix("https://")
    clean = clean.substringBefore('/')
    if (clean.startsWith("[") && clean.contains("]")) {
        clean = clean.substringAfter('[').substringBefore(']')
    } else {
        val firstColon = clean.indexOf(':')
        val lastColon = clean.lastIndexOf(':')
        if (firstColon > 0 && firstColon == lastColon) {
            val maybePort = clean.substringAfter(':').toIntOrNull()
            if (maybePort != null) clean = clean.substringBefore(':')
        }
    }
    return clean.trim()
}

fun isValidOrderingDiscoveryHost(host: String): Boolean {
    val clean = host.trim()
    if (clean.isBlank()) return false
    if (clean.contains(' ')) return false
    if (clean.contains('/')) return false
    if (clean == "0.0.0.0") return false
    return true
}

fun isOrderingLoopbackHost(host: String): Boolean {
    return when (host.trim().lowercase()) {
        "localhost", "127.0.0.1", "::1" -> true
        else -> false
    }
}

fun orderingDiscoveryHostPriority(host: String, currentHost: String? = null): Int {
    val normalized = normalizeOrderingDiscoveryHost(host).lowercase()
    val current = normalizeOrderingDiscoveryHost(currentHost).lowercase()
    return when {
        isOrderingLoopbackHost(normalized) -> 0
        current.isNotBlank() && normalized == current -> 1
        else -> 2
    }
}

fun isOrderingIpv4Address(host: String): Boolean {
    val parts = host.trim().split('.')
    if (parts.size != 4) return false
    return parts.all { part ->
        val value = part.toIntOrNull() ?: return@all false
        value in 0..255
    }
}

fun isOrderingIpv4Prefix(prefix: String): Boolean {
    val parts = prefix.trim().split('.')
    if (parts.size != 3) return false
    return parts.all { part ->
        val value = part.toIntOrNull() ?: return@all false
        value in 0..255
    }
}

fun orderingIpv4PrefixOfPrivateAddress(host: String): String? {
    val clean = normalizeOrderingDiscoveryHost(host)
    if (!isPrivateOrderingIpv4Address(clean)) return null
    return clean.substringBeforeLast('.', "").takeIf { isOrderingIpv4Prefix(it) }
}

private fun isPrivateOrderingIpv4Address(host: String): Boolean {
    if (!isOrderingIpv4Address(host)) return false
    val parts = host.split('.')
    val a = parts[0].toIntOrNull() ?: return false
    val b = parts[1].toIntOrNull() ?: return false
    return when {
        a == 10 -> true
        a == 192 && b == 168 -> true
        a == 172 && b in 16..31 -> true
        else -> false
    }
}

fun buildOrderingDiscoveryPrefixes(
    preferredPrefix: String? = null,
    knownHosts: Collection<String?> = emptyList(),
    includeCommonFallback: Boolean = true
): List<String> {
    val prefixes = LinkedHashSet<String>()

    fun addPrefix(raw: String?) {
        val value = raw.orEmpty().trim()
        if (isOrderingIpv4Prefix(value)) prefixes += value
    }

    fun addFromHost(rawHost: String?) {
        val prefix = orderingIpv4PrefixOfPrivateAddress(rawHost.orEmpty()) ?: return
        prefixes += prefix
    }

    addPrefix(preferredPrefix)
    knownHosts.forEach { addFromHost(it) }

    if ("192.168.1" in prefixes) prefixes += "192.168.0"
    if ("192.168.0" in prefixes) prefixes += "192.168.1"
    if ("10.0.0" in prefixes) prefixes += "10.0.1"
    if ("10.0.1" in prefixes) prefixes += "10.0.0"

    if (includeCommonFallback && prefixes.isEmpty()) {
        ORDERING_COMMON_LAN_PREFIXES.forEach { prefixes += it }
    }
    return prefixes.toList()
}

fun buildOrderingLanHostCandidates(
    localIp: String? = null,
    seeds: Collection<String?> = emptyList(),
    preferredPrefix: String? = null,
    maxHostsPerPrefix: Int = 254,
    includeCommonFallback: Boolean = true
): List<String> {
    val hosts = LinkedHashSet<String>()

    fun addHost(raw: String?) {
        val host = normalizeOrderingDiscoveryHost(raw)
        if (!isValidOrderingDiscoveryHost(host)) return
        hosts += host
    }

    seeds.forEach { addHost(it) }
    addHost(localIp)

    val cap = maxHostsPerPrefix.coerceIn(1, 254)
    val knownHosts = ArrayList<String?>(seeds.size + 1).apply {
        addAll(seeds)
        add(localIp)
    }
    val prefixes = buildOrderingDiscoveryPrefixes(
        preferredPrefix = preferredPrefix,
        knownHosts = knownHosts,
        includeCommonFallback = includeCommonFallback
    )
    prefixes.forEach { prefix ->
        for (i in 1..cap) {
            hosts += "$prefix.$i"
        }
    }
    return hosts.toList()
}

fun buildOrderingPrimaryPortCandidates(
    preferredPort: Int? = null,
    savedPort: Int? = null,
    defaultPort: Int = 19081
): List<Int> {
    val ports = LinkedHashSet<Int>()
    fun addPort(value: Int?) {
        if (value != null && value in 1..65535) ports += value
    }

    addPort(preferredPort)
    addPort(savedPort)
    addPort(defaultPort)
    return ports.toList()
}

fun buildOrderingWebFallbackPortCandidates(
    primaryPorts: Collection<Int>,
    preferredPort: Int? = null,
    defaultPort: Int = 19081
): List<Int> {
    val ports = LinkedHashSet<Int>()
    fun addPort(value: Int?) {
        if (value != null && value in 1..65535) ports += value
    }

    if (preferredPort != null && preferredPort in 1..65535) {
        for (delta in -3..3) addPort(preferredPort + delta)
    }
    for (delta in -3..3) addPort(defaultPort + delta)

    listOf(19080, 19082, 8080, 8081, 8082, 3000, 3001, 4173, 5173, 5174, 9000).forEach { addPort(it) }
    return ports.filterNot { it in primaryPorts }
}

fun buildLayeredOrderingEndpointCandidates(
    primaryHosts: List<String>,
    primaryPorts: List<Int>,
    fallbackHosts: List<String>,
    fallbackPorts: List<Int>,
    maxTargets: Int = 3200
): List<OrderingDiscoveryEndpoint> {
    if (primaryHosts.isEmpty() || primaryPorts.isEmpty()) return emptyList()

    val seen = LinkedHashSet<String>()
    val targets = ArrayList<OrderingDiscoveryEndpoint>(
        minOf(maxTargets, primaryHosts.size * (primaryPorts.size + fallbackPorts.size))
    )

    fun append(hosts: List<String>, ports: List<Int>) {
        hosts.forEach { host ->
            ports.forEach { port ->
                val key = "$host:$port"
                if (!seen.add(key)) return@forEach
                targets += OrderingDiscoveryEndpoint(host = host, port = port)
                if (targets.size >= maxTargets) return
            }
            if (targets.size >= maxTargets) return
        }
    }

    append(primaryHosts, primaryPorts)
    if (targets.size < maxTargets) {
        append(fallbackHosts, fallbackPorts)
    }
    return targets
}
