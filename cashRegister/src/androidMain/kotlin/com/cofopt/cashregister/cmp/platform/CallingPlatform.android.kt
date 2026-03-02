package com.cofopt.cashregister.cmp.platform

import com.cofopt.cashregister.calling.CallingRepository
import com.cofopt.cashregister.network.CallingMachineBridge
import com.cofopt.cashregister.network.OrdersRepository
import kotlinx.coroutines.flow.StateFlow

actual object CallingPlatform {
    actual val preparing: StateFlow<List<Int>> = CallingRepository.preparing
    actual val ready: StateFlow<List<Int>> = CallingRepository.ready
    private var bridge: CallingMachineBridge? = null

    fun bindBridge(instance: CallingMachineBridge?) {
        bridge = instance
    }

    actual fun clearPreparing() {
        CallingRepository.clearPreparing()
    }

    actual fun clearReady() {
        CallingRepository.clearReady()
    }

    actual fun markReady(number: Int) {
        CallingRepository.markReady(number)
    }

    actual fun complete(number: Int) {
        CallingRepository.complete(number)
    }

    actual fun updateOrderStatusByCallNumber(callNumber: Int, status: String) {
        OrdersRepository.updateOrderStatusByCallNumber(callNumber, status)
    }

    actual fun addManualReady(number: Int): ManualCallAddResult {
        return when (CallingRepository.addManualReady(number)) {
            CallingRepository.ManualReadyAddResult.Added -> ManualCallAddResult.Added
            CallingRepository.ManualReadyAddResult.Duplicate -> ManualCallAddResult.Duplicate
            CallingRepository.ManualReadyAddResult.OutOfRange -> ManualCallAddResult.OutOfRange
        }
    }

    actual fun addManualPreparing(number: Int): ManualCallAddResult {
        return when (CallingRepository.addManualPreparing(number)) {
            CallingRepository.ManualReadyAddResult.Added -> ManualCallAddResult.Added
            CallingRepository.ManualReadyAddResult.Duplicate -> ManualCallAddResult.Duplicate
            CallingRepository.ManualReadyAddResult.OutOfRange -> ManualCallAddResult.OutOfRange
        }
    }

    actual fun sendAlert(number: Int) {
        bridge?.sendAlert(number)
    }
}
