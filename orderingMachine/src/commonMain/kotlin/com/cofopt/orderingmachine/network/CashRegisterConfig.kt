package com.cofopt.orderingmachine.network

object CashRegisterConfig {
    private const val PREFS_NAME = "cash_register_config"
    private const val KEY_HOST = "host"
    private const val KEY_PORT = "port"

    fun host(context: OrderingPlatformContext): String {
        return OrderingPlatformPrefs.getString(context, PREFS_NAME, KEY_HOST, "")
    }

    fun port(context: OrderingPlatformContext): Int {
        return OrderingPlatformPrefs.getInt(context, PREFS_NAME, KEY_PORT, 8080)
    }

    fun save(context: OrderingPlatformContext, host: String, port: Int) {
        OrderingPlatformPrefs.putString(context, PREFS_NAME, KEY_HOST, host)
        OrderingPlatformPrefs.putInt(context, PREFS_NAME, KEY_PORT, port)
    }
}
