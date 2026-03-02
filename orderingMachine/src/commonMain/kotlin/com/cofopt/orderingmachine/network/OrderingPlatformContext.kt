package com.cofopt.orderingmachine.network

import androidx.compose.runtime.Composable

expect abstract class OrderingPlatformContext

@Composable
expect fun rememberOrderingPlatformContext(): OrderingPlatformContext
