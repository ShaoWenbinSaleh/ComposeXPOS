package com.cofopt.cashregister.cmp.utils

import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private const val MILLIS_PER_DAY = 86_400_000L
private const val SECONDS_PER_DAY = 86_400L

@OptIn(ExperimentalTime::class)
actual fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

actual fun startOfTodayMillis(now: Long): Long {
    val days = floorDiv(now, MILLIS_PER_DAY)
    return days * MILLIS_PER_DAY
}

actual fun formatOrderTime(millis: Long): String {
    val totalSeconds = floorDiv(millis, 1000L)
    val secondOfDay = floorMod(totalSeconds, SECONDS_PER_DAY).toInt()

    val hour = secondOfDay / 3600
    val minute = (secondOfDay % 3600) / 60

    val daysSinceEpoch = floorDiv(millis, MILLIS_PER_DAY)
    val date = civilFromEpochDays(daysSinceEpoch)

    val y = date.year.toString().padStart(4, '0')
    val m = date.month.toString().padStart(2, '0')
    val d = date.day.toString().padStart(2, '0')
    val h = hour.toString().padStart(2, '0')
    val min = minute.toString().padStart(2, '0')
    return "$y-$m-$d $h:$min"
}

private fun floorDiv(a: Long, b: Long): Long {
    var q = a / b
    if ((a xor b) < 0 && q * b != a) q--
    return q
}

private fun floorMod(a: Long, b: Long): Long = a - floorDiv(a, b) * b

private data class CivilDate(val year: Int, val month: Int, val day: Int)

private fun civilFromEpochDays(daysSinceEpoch: Long): CivilDate {
    // civil_from_days algorithm, epoch day 0 = 1970-01-01.
    var z = daysSinceEpoch + 719_468L
    val era = if (z >= 0) z / 146_097L else (z - 146_096L) / 146_097L
    val doe = z - era * 146_097L
    val yoe = (doe - doe / 1_460L + doe / 36_524L - doe / 146_096L) / 365L
    var y = yoe + era * 400L
    val doy = doe - (365L * yoe + yoe / 4L - yoe / 100L)
    val mp = (5L * doy + 2L) / 153L
    val d = doy - (153L * mp + 2L) / 5L + 1L
    val m = mp + if (mp < 10L) 3L else -9L
    if (m <= 2L) y += 1L
    return CivilDate(year = y.toInt(), month = m.toInt(), day = d.toInt())
}
