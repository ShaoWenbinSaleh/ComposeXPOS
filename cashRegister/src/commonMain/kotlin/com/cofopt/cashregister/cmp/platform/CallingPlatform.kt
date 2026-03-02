package com.cofopt.cashregister.cmp.platform

import kotlinx.coroutines.flow.StateFlow

enum class ManualCallAddResult {
    Added,
    Duplicate,
    OutOfRange
}

expect object CallingPlatform {
    val preparing: StateFlow<List<Int>>
    val ready: StateFlow<List<Int>>

    fun clearPreparing()
    fun clearReady()
    fun markReady(number: Int)
    fun complete(number: Int)
    fun updateOrderStatusByCallNumber(callNumber: Int, status: String)

    fun addManualReady(number: Int): ManualCallAddResult
    fun addManualPreparing(number: Int): ManualCallAddResult

    fun sendAlert(number: Int)
}
