package com.cofopt.orderingmachine.network

import platform.Foundation.NSUserDefaults

actual object OrderingPlatformPrefs {
    private val defaults: NSUserDefaults
        get() = NSUserDefaults.standardUserDefaults

    private fun storageKey(prefsName: String, key: String): String = "ordering.$prefsName.$key"

    actual fun getString(
        context: OrderingPlatformContext,
        prefsName: String,
        key: String,
        defaultValue: String,
    ): String {
        return defaults.stringForKey(storageKey(prefsName, key)) ?: defaultValue
    }

    actual fun getInt(
        context: OrderingPlatformContext,
        prefsName: String,
        key: String,
        defaultValue: Int,
    ): Int {
        val fullKey = storageKey(prefsName, key)
        if (defaults.objectForKey(fullKey) == null) return defaultValue
        return defaults.integerForKey(fullKey).toInt()
    }

    actual fun getBoolean(
        context: OrderingPlatformContext,
        prefsName: String,
        key: String,
        defaultValue: Boolean,
    ): Boolean {
        val fullKey = storageKey(prefsName, key)
        if (defaults.objectForKey(fullKey) == null) return defaultValue
        return defaults.boolForKey(fullKey)
    }

    actual fun putString(
        context: OrderingPlatformContext,
        prefsName: String,
        key: String,
        value: String,
    ) {
        defaults.setObject(value, forKey = storageKey(prefsName, key))
    }

    actual fun putInt(
        context: OrderingPlatformContext,
        prefsName: String,
        key: String,
        value: Int,
    ) {
        defaults.setInteger(value.toLong(), forKey = storageKey(prefsName, key))
    }

    actual fun putBoolean(
        context: OrderingPlatformContext,
        prefsName: String,
        key: String,
        value: Boolean,
    ) {
        defaults.setBool(value, forKey = storageKey(prefsName, key))
    }
}
