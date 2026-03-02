package com.cofopt.cashregister.cmp.debug

import androidx.compose.runtime.Composable

expect object DebugPlatformActions {
    @Composable
    fun rememberPaymentActions(): PaymentDebugActions

    @Composable
    fun rememberPrinterActions(): PrinterDebugActions

    @Composable
    fun CallingMachineTabContent()

    @Composable
    fun OrderingMachinesTabContent()
}
