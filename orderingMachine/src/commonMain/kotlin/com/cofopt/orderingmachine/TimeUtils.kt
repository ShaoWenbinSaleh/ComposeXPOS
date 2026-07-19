package com.cofopt.orderingmachine

import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource

private val appStart = TimeSource.Monotonic.markNow()

fun currentTimeMillis(): Long = appStart.elapsedNow().inWholeMilliseconds

/** Wall-clock time for protocol timestamps and durable identifiers. */
@OptIn(ExperimentalTime::class)
fun currentEpochMillis(): Long = Clock.System.now().toEpochMilliseconds()
