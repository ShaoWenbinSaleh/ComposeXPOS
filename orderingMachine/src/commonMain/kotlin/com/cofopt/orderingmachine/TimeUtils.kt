package com.cofopt.orderingmachine

import kotlin.time.TimeSource

private val appStart = TimeSource.Monotonic.markNow()

fun currentTimeMillis(): Long = appStart.elapsedNow().inWholeMilliseconds
