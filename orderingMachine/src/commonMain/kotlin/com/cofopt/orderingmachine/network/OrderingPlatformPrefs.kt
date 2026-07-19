package com.cofopt.orderingmachine.network

expect object OrderingPlatformPrefs {
    fun getString(context: OrderingPlatformContext, prefsName: String, key: String, defaultValue: String): String

    fun getInt(context: OrderingPlatformContext, prefsName: String, key: String, defaultValue: Int): Int

    fun getBoolean(context: OrderingPlatformContext, prefsName: String, key: String, defaultValue: Boolean): Boolean

    fun putString(context: OrderingPlatformContext, prefsName: String, key: String, value: String)

    /** Synchronously commits communication-critical state before network I/O begins. */
    fun putStringDurable(context: OrderingPlatformContext, prefsName: String, key: String, value: String): Boolean

    /** Returns logical preference keys (not platform-qualified keys), or null when storage cannot be read. */
    fun getStringsWithPrefix(
        context: OrderingPlatformContext,
        prefsName: String,
        keyPrefix: String,
    ): Map<String, String>?

    /** Synchronously removes communication-critical state. */
    fun removeStringDurable(context: OrderingPlatformContext, prefsName: String, key: String): Boolean

    fun putInt(context: OrderingPlatformContext, prefsName: String, key: String, value: Int)

    fun putBoolean(context: OrderingPlatformContext, prefsName: String, key: String, value: Boolean)
}
