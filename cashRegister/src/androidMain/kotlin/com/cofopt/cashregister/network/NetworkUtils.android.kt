package com.cofopt.cashregister.network

import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface

actual fun getLocalIpv4Address(): String? {
    val candidates = runCatching { localIpv4Candidates() }.getOrDefault(emptyList())
    val currentNetworkAddress = currentNetworkIpv4Address()
    return selectPreferredLocalIpv4Address(candidates, currentNetworkAddress)
        ?: currentNetworkAddress?.takeIf(::isUsableIpv4Address)
}

internal data class LocalIpv4Candidate(
    val interfaceName: String,
    val address: String,
)

internal fun selectPreferredLocalIpv4Address(
    candidates: List<LocalIpv4Candidate>,
    currentNetworkAddress: String?,
): String? {
    val current = currentNetworkAddress?.trim()?.takeIf(::isUsableIpv4Address)
    return candidates.asSequence()
        .map { it.copy(interfaceName = it.interfaceName.trim(), address = it.address.trim()) }
        .filter { isUsableIpv4Address(it.address) }
        .distinctBy { it.address }
        .sortedWith(
            compareBy<LocalIpv4Candidate> { candidatePriority(it, current) }
                .thenBy { it.interfaceName.lowercase() }
                .thenBy { it.address }
        )
        .firstOrNull()
        ?.address
}

private fun localIpv4Candidates(): List<LocalIpv4Candidate> {
    val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
    return interfaces.asSequence()
        .filter { networkInterface ->
            runCatching { networkInterface.isUp && !networkInterface.isLoopback }
                .getOrDefault(false)
        }
        .flatMap { networkInterface ->
            val addresses = runCatching { networkInterface.inetAddresses.toList() }
                .getOrDefault(emptyList())
            addresses.asSequence()
                .filterIsInstance<Inet4Address>()
                .mapNotNull { address ->
                    address.hostAddress?.let { host ->
                        LocalIpv4Candidate(networkInterface.name.orEmpty(), host)
                    }
                }
        }
        .toList()
}

private fun currentNetworkIpv4Address(): String? {
    return runCatching {
        DatagramSocket().use { socket ->
            // UDP connect performs only route selection; no packet is sent.
            socket.connect(InetSocketAddress(DEFAULT_ROUTE_PROBE_IPV4, DEFAULT_ROUTE_PROBE_PORT))
            (socket.localAddress as? Inet4Address)?.hostAddress
        }
    }.getOrNull()?.takeIf(::isUsableIpv4Address)
}

private fun candidatePriority(candidate: LocalIpv4Candidate, currentNetworkAddress: String?): Int {
    val interfaceName = candidate.interfaceName.lowercase()
    return when {
        interfaceName.startsWith("wlan") || interfaceName.startsWith("wifi") -> 0
        candidate.address == currentNetworkAddress -> 1
        interfaceName.startsWith("eth") || interfaceName.startsWith("en") -> 2
        else -> 3
    }
}

internal fun isUsableIpv4Address(rawAddress: String): Boolean {
    val octets = rawAddress.trim().split('.')
    if (octets.size != 4) return false
    val values = octets.map { octet ->
        if (octet.isEmpty() || octet.length > 3) return false
        octet.toIntOrNull()?.takeIf { it in 0..255 } ?: return false
    }
    val first = values[0]
    val second = values[1]
    if (first == 0 || first == 127 || first >= 224) return false
    if (first == 169 && second == 254) return false
    return true
}

private const val DEFAULT_ROUTE_PROBE_IPV4 = "8.8.8.8"
private const val DEFAULT_ROUTE_PROBE_PORT = 53
