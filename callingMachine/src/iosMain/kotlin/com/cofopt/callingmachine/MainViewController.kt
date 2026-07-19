package com.cofopt.callingmachine

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import com.cofopt.callingmachine.cmp.CallingMachineApp
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController = ComposeUIViewController {
    val bridge = remember { IosCallingMachineBridge() }
    val preparing by bridge.preparing.collectAsState()
    val ready by bridge.ready.collectAsState()
    val preparingLabel by bridge.preparingLabel.collectAsState()
    val readyLabel by bridge.readyLabel.collectAsState()
    val connected by bridge.connected.collectAsState()
    val statusText by bridge.statusText.collectAsState()
    val alertNumber by bridge.alertNumber.collectAsState()
    val alertNonce by bridge.alertNonce.collectAsState()
    val newPreparingNumbers by bridge.newPreparingNumbers.collectAsState()

    DisposableEffect(bridge) {
        bridge.start()
        onDispose { bridge.close() }
    }

    CallingMachineApp(
        preparing = preparing,
        ready = ready,
        preparingLabel = preparingLabel,
        readyLabel = readyLabel,
        statusText = statusText,
        isConnected = connected,
        localIp = "-",
        alertOverlayNumber = alertNumber,
        alertOverlayNonce = alertNonce,
        isPreparingNumber = { it in newPreparingNumbers },
    )
}
