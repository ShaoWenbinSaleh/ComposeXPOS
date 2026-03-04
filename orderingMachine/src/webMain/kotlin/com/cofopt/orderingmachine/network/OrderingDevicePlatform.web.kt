package com.cofopt.orderingmachine.network

import kotlinx.browser.window
import kotlin.random.Random

private fun webInstanceSuffix(): String {
    val instanceId = window.location.pathname.trim('/').substringBefore('/').ifBlank { "orderingMachine" }
    return when (instanceId) {
        "orderingMachine-1" -> "B"
        else -> "A"
    }
}

actual fun generateDeviceUuid(): String {
    val randomPart = Random.nextLong().toString(16)
    return "web-${webInstanceSuffix()}-$randomPart"
}

actual fun platformDeviceName(): String = "Web Browser ${webInstanceSuffix()}"
