package com.cofopt.orderingmachine.network

expect object OrderingPlatformPrefs {
    fun getString(context: OrderingPlatformContext, prefsName: String, key: String, defaultValue: String): String

    fun getInt(context: OrderingPlatformContext, prefsName: String, key: String, defaultValue: Int): Int

    fun getBoolean(context: OrderingPlatformContext, prefsName: String, key: String, defaultValue: Boolean): Boolean

    fun putString(context: OrderingPlatformContext, prefsName: String, key: String, value: String)

    fun putInt(context: OrderingPlatformContext, prefsName: String, key: String, value: Int)

    fun putBoolean(context: OrderingPlatformContext, prefsName: String, key: String, value: Boolean)
}
