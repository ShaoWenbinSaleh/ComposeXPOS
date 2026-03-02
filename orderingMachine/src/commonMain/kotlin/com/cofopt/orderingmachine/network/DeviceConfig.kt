package com.cofopt.orderingmachine.network

object DeviceConfig {
    private const val PREFS_NAME = "device_config"
    private const val KEY_DEVICE_UUID = "device_uuid"

    fun deviceName(context: OrderingPlatformContext): String {
        return deviceUuid(context)
    }

    fun deviceUuid(context: OrderingPlatformContext): String {
        val stored = OrderingPlatformPrefs
            .getString(context, PREFS_NAME, KEY_DEVICE_UUID, "")
            .trim()
        if (stored.isNotBlank()) {
            return stored
        }

        val generated = generateDeviceUuid()
        OrderingPlatformPrefs.putString(context, PREFS_NAME, KEY_DEVICE_UUID, generated)
        return generated
    }

    fun androidDeviceName(): String {
        return platformDeviceName()
    }
}
