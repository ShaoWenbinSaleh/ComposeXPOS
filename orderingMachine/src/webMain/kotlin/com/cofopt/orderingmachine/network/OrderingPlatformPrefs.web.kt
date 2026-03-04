package com.cofopt.orderingmachine.network

import kotlinx.browser.window

actual object OrderingPlatformPrefs {
    private fun instanceId(): String {
        val path = window.location.pathname.trim('/')
        return path.substringBefore('/').ifBlank { "root" }
    }

    private fun storageKey(prefsName: String, key: String): String {
        return "ordering.${instanceId()}.$prefsName.$key"
    }

    private fun defaultCashRegisterPortByInstance(defaultValue: Int): Int {
        return when (instanceId()) {
            "orderingMachine" -> 8080
            "orderingMachine-1" -> 8081
            else -> defaultValue
        }
    }

    actual fun getString(
        context: OrderingPlatformContext,
        prefsName: String,
        key: String,
        defaultValue: String,
    ): String {
        val value = runCatching {
            window.localStorage.getItem(storageKey(prefsName, key))
        }.getOrNull()
        return value ?: defaultValue
    }

    actual fun getInt(
        context: OrderingPlatformContext,
        prefsName: String,
        key: String,
        defaultValue: Int,
    ): Int {
        val configured = getString(context, prefsName, key, "").toIntOrNull()
        if (configured != null) return configured
        if (prefsName == "cash_register_config" && key == "port") {
            return defaultCashRegisterPortByInstance(defaultValue)
        }
        return defaultValue
    }

    actual fun getBoolean(
        context: OrderingPlatformContext,
        prefsName: String,
        key: String,
        defaultValue: Boolean,
    ): Boolean {
        return when (getString(context, prefsName, key, "")) {
            "true" -> true
            "false" -> false
            else -> defaultValue
        }
    }

    actual fun putString(
        context: OrderingPlatformContext,
        prefsName: String,
        key: String,
        value: String,
    ) {
        runCatching {
            window.localStorage.setItem(storageKey(prefsName, key), value)
        }
    }

    actual fun putInt(
        context: OrderingPlatformContext,
        prefsName: String,
        key: String,
        value: Int,
    ) {
        putString(context, prefsName, key, value.toString())
    }

    actual fun putBoolean(
        context: OrderingPlatformContext,
        prefsName: String,
        key: String,
        value: Boolean,
    ) {
        putString(context, prefsName, key, value.toString())
    }
}
