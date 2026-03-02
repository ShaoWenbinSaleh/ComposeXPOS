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
        alertOverlayNumber = null,
        alertOverlayNonce = 0
    )
}
