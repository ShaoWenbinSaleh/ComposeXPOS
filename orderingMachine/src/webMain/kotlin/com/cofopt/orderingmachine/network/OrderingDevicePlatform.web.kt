package com.cofopt.orderingmachine.network

import kotlin.random.Random

actual fun generateDeviceUuid(): String {
    val randomPart = Random.nextLong().toString(16)
    return "web-$randomPart"
}

actual fun platformDeviceName(): String = "Web Browser"
