package com.cofopt.callingmachine

fun callingStatusText(
    serverStartError: String?,
    connectionCount: Int,
    lastCloseInfo: String?,
    wsPort: Int
): String {
    return when {
        serverStartError != null -> "WS start failed: $serverStartError   (port=$wsPort)"
        connectionCount > 0 -> "Connected: $connectionCount"
        !lastCloseInfo.isNullOrBlank() -> "Waiting for CashRegister…   lastClose=$lastCloseInfo"
        else -> "Waiting for CashRegister…"
    }
}
