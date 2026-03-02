package com.cofopt.cashregister.cmp.utils

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

actual fun nowMillis(): Long = System.currentTimeMillis()

actual fun startOfTodayMillis(now: Long): Long {
    return try {
        Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    } catch (_: Exception) {
        now
    }
}

actual fun formatOrderTime(millis: Long): String {
    return try {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(millis))
    } catch (_: Exception) {
        millis.toString()
    }
}
