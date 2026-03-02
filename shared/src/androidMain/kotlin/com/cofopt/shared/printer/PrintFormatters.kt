package com.cofopt.shared.printer

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatEur(amount: Double): String = "EUR ${String.format(Locale.US, "%.2f", amount)}"

fun formatEuro(amount: Double): String = "€ ${String.format(Locale.US, "%.2f", amount)}"

fun formatOrderTime(timestamp: Long): String {
    val date = Date(timestamp)
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    return formatter.format(date)
}

fun formatOrderTimeHm(timestamp: Long): String {
    val date = Date(timestamp)
    val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    return formatter.format(date)
}
