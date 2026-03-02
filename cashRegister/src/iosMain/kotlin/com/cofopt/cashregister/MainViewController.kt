package com.cofopt.cashregister

import androidx.compose.ui.window.ComposeUIViewController
import com.cofopt.cashregister.cmp.CashRegisterApp
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController = ComposeUIViewController {
    CashRegisterApp()
}
