package com.cofopt.cashregister.cmp.utils

expect fun nowMillis(): Long

expect fun startOfTodayMillis(now: Long): Long

expect fun formatOrderTime(millis: Long): String
