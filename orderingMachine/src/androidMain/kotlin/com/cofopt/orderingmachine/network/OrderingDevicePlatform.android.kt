package com.cofopt.orderingmachine.network

import android.os.Build
import java.util.UUID

actual fun generateDeviceUuid(): String = UUID.randomUUID().toString()

actual fun platformDeviceName(): String {
    val model = Build.MODEL?.trim().orEmpty()
    val device = Build.DEVICE?.trim().orEmpty()
    return when {
        model.isNotBlank() -> model
        device.isNotBlank() -> device
        else -> "Unknown Android Device"
    }
}
