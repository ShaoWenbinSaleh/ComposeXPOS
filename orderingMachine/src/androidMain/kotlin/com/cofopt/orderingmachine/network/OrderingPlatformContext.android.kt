package com.cofopt.orderingmachine.network

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

actual typealias OrderingPlatformContext = Context

@Composable
actual fun rememberOrderingPlatformContext(): OrderingPlatformContext = LocalContext.current
