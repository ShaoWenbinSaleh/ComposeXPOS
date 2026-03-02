package com.cofopt.cashregister.cmp.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

actual object CallingPlatform {
    private val preparingState = MutableStateFlow(emptyList<Int>())
    private val readyState = MutableStateFlow(emptyList<Int>())

    actual val preparing: StateFlow<List<Int>> = preparingState
    actual val ready: StateFlow<List<Int>> = readyState

    actual fun clearPreparing() {
        preparingState.value = emptyList()
    }

    actual fun clearReady() {
        readyState.value = emptyList()
    }

    actual fun markReady(number: Int) {
        preparingState.value = preparingState.value.filterNot { it == number }
        if (!readyState.value.contains(number)) {
            readyState.value = readyState.value + number
        }
    }

    actual fun complete(number: Int) {
        readyState.value = readyState.value.filterNot { it == number }
    }

    actual fun updateOrderStatusByCallNumber(callNumber: Int, status: String) {
        when (status.trim().uppercase()) {
            "READY" -> markReady(callNumber)
            "COMPLETED" -> complete(callNumber)
            else -> Unit
        }
    }

    actual fun addManualReady(number: Int): ManualCallAddResult {
        if (number !in 1..999) return ManualCallAddResult.OutOfRange
        if (readyState.value.contains(number)) return ManualCallAddResult.Duplicate
        preparingState.value = preparingState.value.filterNot { it == number }
        readyState.value = readyState.value + number
        return ManualCallAddResult.Added
    }

    actual fun addManualPreparing(number: Int): ManualCallAddResult {
        if (number !in 1..999) return ManualCallAddResult.OutOfRange
        if (preparingState.value.contains(number)) return ManualCallAddResult.Duplicate
        readyState.value = readyState.value.filterNot { it == number }
        preparingState.value = preparingState.value + number
        return ManualCallAddResult.Added
    }

    actual fun sendAlert(number: Int) {
        // iOS open-source build does not ship a CallingMachine bridge by default.
    }
}
