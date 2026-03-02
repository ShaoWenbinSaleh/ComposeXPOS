package com.cofopt.orderingmachine.ui.DebugScreen

import androidx.compose.runtime.Composable

expect object DebugScreenPlatform {
    @Composable
    fun PrinterSettingTabContent()

    @Composable
    fun SystemInfoTabContent()

    @Composable
    fun rememberExitAppAction(): (() -> Unit)?
}
