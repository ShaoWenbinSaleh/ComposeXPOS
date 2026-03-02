package com.cofopt.orderingmachine.ui.DebugScreen

import android.app.Activity
import android.os.Process
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

actual object DebugScreenPlatform {
    @Composable
    actual fun PrinterSettingTabContent() {
        PrinterSettingTab()
    }

    @Composable
    actual fun SystemInfoTabContent() {
        SystemInfoTab()
    }

    @Composable
    actual fun rememberExitAppAction(): (() -> Unit)? {
        val context = LocalContext.current
        return remember(context) {
            {
                (context as? Activity)?.finishAffinity()
                Process.killProcess(Process.myPid())
            }
        }
    }
}
