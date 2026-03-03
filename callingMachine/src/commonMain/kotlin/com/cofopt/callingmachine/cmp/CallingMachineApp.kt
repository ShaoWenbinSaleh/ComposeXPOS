package com.cofopt.callingmachine.cmp

import androidx.compose.runtime.Composable

@Composable
fun CallingMachineApp(
    preparing: List<Int>,
    ready: List<Int>,
    preparingLabel: String,
    readyLabel: String,
    statusText: String,
    isConnected: Boolean,
    localIp: String = "-",
    alertOverlayNumber: Int?,
    alertOverlayNonce: Int,
    isPreparingNumber: (Int) -> Boolean = { false },
) {
    CallingMachineScreen(
        preparing = preparing,
        ready = ready,
        preparingLabel = preparingLabel,
        readyLabel = readyLabel,
        statusText = statusText,
        isConnected = isConnected,
        localIp = localIp,
        alertOverlayNumber = alertOverlayNumber,
        alertOverlayNonce = alertOverlayNonce,
        isPreparingNumber = isPreparingNumber
    )
}

@Composable
fun CallingMachineApp() {
    CallingMachineApp(
        preparing = emptyList(),
        ready = emptyList(),
        preparingLabel = "Preparing",
        readyLabel = "Ready",
        statusText = "Waiting for source...",
        isConnected = false,
        localIp = "-",
        alertOverlayNumber = null,
        alertOverlayNonce = 0
    )
}
