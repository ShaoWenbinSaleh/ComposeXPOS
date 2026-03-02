package com.cofopt.cashregister.cmp.utils

import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
actual fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

actual fun startOfTodayMillis(now: Long): Long {
    // Fallback for iOS target in this open-source build.
    return now - (now % 86_400_000L)
}

actual fun formatOrderTime(millis: Long): String = millis.toString()
