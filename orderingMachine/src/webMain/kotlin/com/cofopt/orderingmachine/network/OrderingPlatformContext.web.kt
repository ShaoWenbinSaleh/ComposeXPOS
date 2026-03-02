package com.cofopt.orderingmachine.network

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

actual abstract class OrderingPlatformContext

private class WebOrderingPlatformContext : OrderingPlatformContext()

@Composable
actual fun rememberOrderingPlatformContext(): OrderingPlatformContext {
    return remember { WebOrderingPlatformContext() }
}
