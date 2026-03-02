package com.cofopt.orderingmachine.ui.DebugScreen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
fun DebugScreen(
    onBack: () -> Unit,
) {
    val onExitApp = DebugScreenPlatform.rememberExitAppAction()
    val tabs = remember {
        listOf(
            DebugTabPage(title = "Payment Setting") { PaymentSettingTab() },
            DebugTabPage(title = "Printer Setting") { DebugScreenPlatform.PrinterSettingTabContent() },
            DebugTabPage(title = "System Setting") { SystemSettingTab() },
            DebugTabPage(title = "System Info") { DebugScreenPlatform.SystemInfoTabContent() },
        )
    }

    DebugScreenScaffold(
        onBack = onBack,
        tabs = tabs,
        onExitApp = onExitApp,
    )
}
