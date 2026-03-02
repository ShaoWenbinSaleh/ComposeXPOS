package com.cofopt.orderingmachine.network

import kotlinx.browser.window

actual object OrderingPlatformPrefs {
    private fun storageKey(prefsName: String, key: String): String = "ordering.$prefsName.$key"

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
        return getString(context, prefsName, key, "").toIntOrNull() ?: defaultValue
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
