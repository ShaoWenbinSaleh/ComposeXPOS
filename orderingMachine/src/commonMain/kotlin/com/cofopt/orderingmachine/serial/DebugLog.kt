package com.cofopt.orderingmachine.serial

import kotlin.time.TimeSource

object DebugLog {
    private const val MAX_LINES = 300
    private val appStart = TimeSource.Monotonic.markNow()

    private val lines = ArrayDeque<String>(MAX_LINES)

    fun clear() {
        lines.clear()
    }

    fun add(message: String) {
        val line = "${appStart.elapsedNow().inWholeMilliseconds} $message"
        if (lines.size >= MAX_LINES) {
            lines.removeFirst()
        }
        lines.addLast(line)
        println("SepayECR: $message")
    }

    fun dump(maxChars: Int = 6000): String {
        val joined = lines.joinToString("\n")
        return if (joined.length <= maxChars) joined else joined.takeLast(maxChars)
    }

    fun toHex(bytes: ByteArray, length: Int = bytes.size, maxBytes: Int = 64): String {
        val n = minOf(length, bytes.size, maxBytes)
        val sb = StringBuilder(n * 2 + 16)
        for (i in 0 until n) {
            val v = bytes[i].toInt() and 0xFF
            if (i > 0) sb.append(' ')
            sb.append(v.toString(16).padStart(2, '0'))
        }
        if (length > maxBytes) sb.append(" …(+${length - maxBytes})")
        return sb.toString()
    }
}
