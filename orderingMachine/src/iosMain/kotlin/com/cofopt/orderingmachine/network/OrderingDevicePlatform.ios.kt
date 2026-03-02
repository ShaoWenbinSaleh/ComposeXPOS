package com.cofopt.orderingmachine.network

import kotlin.random.Random

actual fun generateDeviceUuid(): String {
    return "ios-${Random.nextLong().toString(16)}"
}

actual fun platformDeviceName(): String = "iOS Device"
