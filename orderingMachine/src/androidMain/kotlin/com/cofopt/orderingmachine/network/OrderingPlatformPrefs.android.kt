package com.cofopt.orderingmachine.network

import android.content.Context

actual object OrderingPlatformPrefs {
    private fun prefs(context: OrderingPlatformContext, prefsName: String) =
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    actual fun getString(
        context: OrderingPlatformContext,
        prefsName: String,
        key: String,
        defaultValue: String,
    ): String {
        return prefs(context, prefsName).getString(key, defaultValue) ?: defaultValue
    }

    actual fun getInt(
        context: OrderingPlatformContext,
        prefsName: String,
        key: String,
        defaultValue: Int,
    ): Int {
        return prefs(context, prefsName).getInt(key, defaultValue)
    }

    actual fun getBoolean(
        context: OrderingPlatformContext,
        prefsName: String,
        key: String,
        defaultValue: Boolean,
    ): Boolean {
        return prefs(context, prefsName).getBoolean(key, defaultValue)
    }

    actual fun putString(
        context: OrderingPlatformContext,
        prefsName: String,
        key: String,
        value: String,
    ) {
        prefs(context, prefsName).edit().putString(key, value).apply()
    }

    actual fun putStringDurable(
        context: OrderingPlatformContext,
        prefsName: String,
        key: String,
        value: String,
    ): Boolean = prefs(context, prefsName).edit().putString(key, value).commit()

    actual fun getStringsWithPrefix(
        context: OrderingPlatformContext,
        prefsName: String,
        keyPrefix: String,
    ): Map<String, String>? = runCatching {
        val matching = prefs(context, prefsName).all.filterKeys { it.startsWith(keyPrefix) }
        if (matching.values.any { it !is String }) error("non_string_preference_in_string_namespace")
        matching.mapValues { (_, value) -> value as String }
    }.getOrNull()

    actual fun removeStringDurable(
        context: OrderingPlatformContext,
        prefsName: String,
        key: String,
    ): Boolean = prefs(context, prefsName).edit().remove(key).commit()

    actual fun putInt(
        context: OrderingPlatformContext,
        prefsName: String,
        key: String,
        value: Int,
    ) {
        prefs(context, prefsName).edit().putInt(key, value).apply()
    }

    actual fun putBoolean(
        context: OrderingPlatformContext,
        prefsName: String,
        key: String,
        value: Boolean,
    ) {
        prefs(context, prefsName).edit().putBoolean(key, value).apply()
    }
}
