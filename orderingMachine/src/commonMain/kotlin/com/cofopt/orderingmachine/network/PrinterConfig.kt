package com.cofopt.orderingmachine.network

object PrinterConfig {
    private const val PREFS_NAME = "printer_config"
    private const val KEY_MODE = "mode"
    private const val KEY_IP = "ip"
    private const val KEY_PORT = "port"

    fun mode(context: OrderingPlatformContext): String {
        return OrderingPlatformPrefs.getString(context, PREFS_NAME, KEY_MODE, "SUNMI")
    }

    fun ip(context: OrderingPlatformContext): String {
        return OrderingPlatformPrefs.getString(context, PREFS_NAME, KEY_IP, "192.168.1.100")
    }

    fun port(context: OrderingPlatformContext): Int {
        return OrderingPlatformPrefs.getInt(context, PREFS_NAME, KEY_PORT, 9100)
    }

    fun saveMode(context: OrderingPlatformContext, mode: String) {
        OrderingPlatformPrefs.putString(context, PREFS_NAME, KEY_MODE, mode)
    }

    fun saveIpConfig(context: OrderingPlatformContext, ip: String, port: Int) {
        OrderingPlatformPrefs.putString(context, PREFS_NAME, KEY_IP, ip)
        OrderingPlatformPrefs.putInt(context, PREFS_NAME, KEY_PORT, port)
    }
}
