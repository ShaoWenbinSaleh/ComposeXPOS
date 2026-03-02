package com.cofopt.cashregister.cmp.debug

fun sanitizeOrderingConnectionUuid(raw: String): String {
    return raw.trim().filter { !it.isISOControl() }
}

fun orderingConnectionPrimaryKey(host: String, port: Int, uuid: String = ""): String {
    val cleanHost = normalizeOrderingConnectionHost(host)
    val cleanUuid = sanitizeOrderingConnectionUuid(uuid).lowercase()
    if (cleanHost.isBlank() || port !in 1..65535) return ""
    return if (cleanUuid.isNotBlank()) {
        "uuidhost:$cleanUuid@$cleanHost:$port"
    } else {
        "hostport:$cleanHost:$port"
    }
}

fun orderingConnectionIsConnected(
    connectedKeys: Set<String>,
    host: String,
    port: Int,
    uuid: String = ""
): Boolean {
    if (connectedKeys.isEmpty() || port !in 1..65535) return false
    val keySet = connectedKeys.asSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { it.lowercase() }
        .toSet()

    val cleanHost = normalizeOrderingConnectionHost(host)
    if (cleanHost.isBlank()) return false
    val cleanUuid = sanitizeOrderingConnectionUuid(uuid).lowercase()

    val endpointLegacyWeb = "$cleanHost:$port"
    val endpointHostPort = "hostport:$cleanHost:$port"
    if (endpointLegacyWeb in keySet || endpointHostPort in keySet) return true

    if (cleanUuid.isNotBlank()) {
        if ("uuid:$cleanUuid" in keySet) return true
        if (keySet.any { it.startsWith("uuidhost:$cleanUuid@") }) return true
    }
    return false
}

fun orderingConnectionAdd(
    connectedKeys: Set<String>,
    host: String,
    port: Int,
    uuid: String = ""
): Set<String> {
    val key = orderingConnectionPrimaryKey(host, port, uuid)
    if (key.isBlank()) return connectedKeys
    return connectedKeys + key
}

fun orderingConnectionRemove(
    connectedKeys: Set<String>,
    host: String,
    port: Int,
    uuid: String = ""
): Set<String> {
    if (connectedKeys.isEmpty()) return connectedKeys
    if (port !in 1..65535) return connectedKeys

    val cleanHost = normalizeOrderingConnectionHost(host)
    if (cleanHost.isBlank()) return connectedKeys
    val cleanUuid = sanitizeOrderingConnectionUuid(uuid).lowercase()

    val endpointLegacyWeb = "$cleanHost:$port"
    val endpointHostPort = "hostport:$cleanHost:$port"
    val endpointSuffix = "@$cleanHost:$port"

    return connectedKeys.filterNot { raw ->
        val key = raw.trim().lowercase()
        if (key.isBlank()) return@filterNot true
        if (key == endpointLegacyWeb || key == endpointHostPort) return@filterNot true
        if (key.startsWith("uuidhost:") && key.endsWith(endpointSuffix)) return@filterNot true
        if (cleanUuid.isNotBlank() && (key == "uuid:$cleanUuid" || key.startsWith("uuidhost:$cleanUuid@"))) return@filterNot true
        false
    }.toSet()
}

private fun normalizeOrderingConnectionHost(host: String): String {
    return host.trim().lowercase()
}
