package com.cofopt.cashregister.network

import java.net.Inet4Address
import java.net.NetworkInterface

actual fun getLocalIpv4Address(): String? {
    return try {
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
        interfaces.toList().asSequence()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList().asSequence() }
            .filterIsInstance<Inet4Address>()
            .map { it.hostAddress }
            .firstOrNull { it != null && !it.startsWith("169.254") }
    } catch (_: Exception) {
        null
    }
}
