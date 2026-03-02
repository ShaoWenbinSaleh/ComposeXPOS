package com.cofopt.callingmachine

import android.content.Context

object CashRegisterPeerConfig {
    private const val PREF = "calling_machine_peer"
    private const val KEY_LAST_CASHREGISTER_IP = "last_cashregister_ip"
    private const val KEY_CASHREGISTER_PORT = "cashregister_port"
    private const val DEFAULT_CASHREGISTER_PORT = 8080

    fun lastCashRegisterIp(context: Context): String {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY_LAST_CASHREGISTER_IP, "") ?: ""
    }

    fun saveLastCashRegisterIp(context: Context, ip: String) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_CASHREGISTER_IP, ip.trim())
            .apply()
    }

    fun cashRegisterPort(context: Context): Int {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getInt(KEY_CASHREGISTER_PORT, DEFAULT_CASHREGISTER_PORT)
    }
}
