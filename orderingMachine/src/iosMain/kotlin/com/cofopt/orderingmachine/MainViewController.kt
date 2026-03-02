package com.cofopt.orderingmachine

import androidx.compose.ui.window.ComposeUIViewController
import com.cofopt.orderingmachine.cmp.OrderingMachineApp
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController = ComposeUIViewController {
    OrderingMachineApp()
}
