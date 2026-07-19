package com.cofopt.cashregister

import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.window.ComposeUIViewController
import com.cofopt.cashregister.cmp.CashRegisterApp
import com.cofopt.cashregister.cmp.platform.CallingPlatform
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController = ComposeUIViewController {
    DisposableEffect(Unit) {
        CallingPlatform.startCallingBridge()
        onDispose { CallingPlatform.stopCallingBridge() }
    }
    CashRegisterApp()
}
