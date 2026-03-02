package com.cofopt.callingmachine

import androidx.compose.ui.window.ComposeUIViewController
import com.cofopt.callingmachine.cmp.CallingMachineApp
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController = ComposeUIViewController {
    CallingMachineApp()
}
