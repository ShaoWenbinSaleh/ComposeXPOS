package com.cofopt.shared.printer

private fun isWideCharacter(ch: Char): Boolean {
    val code = ch.code
    return code in 0x1100..0x11FF ||
        code in 0x2E80..0xA4CF ||
        code in 0xAC00..0xD7A3 ||
        code in 0xF900..0xFAFF ||
        code in 0xFE10..0xFE19 ||
        code in 0xFE30..0xFE6F ||
        code in 0xFF00..0xFF60 ||
        code in 0xFFE0..0xFFE6
}

fun centered(text: String, width: Int): String {
    val w = displayWidth(text)
    val leftPad = ((width - w) / 2).coerceAtLeast(0)
    val rightPad = (width - w - leftPad).coerceAtLeast(0)
    return " ".repeat(leftPad) + text + " ".repeat(rightPad)
}

fun displayWidth(text: String): Int {
    var w = 0
    text.forEach { ch ->
        w += if (isWideCharacter(ch)) 2 else 1
    }
    return w
}

fun truncateByDisplayWidth(text: String, maxWidth: Int): String {
    if (maxWidth <= 0) return ""
    val sb = StringBuilder()
    var w = 0
    text.forEach { ch ->
        val cw = displayWidth(ch.toString())
        if (w + cw > maxWidth) return@forEach
        sb.append(ch)
        w += cw
    }
    return sb.toString()
}

fun padRightByDisplayWidth(text: String, width: Int): String {
    val t = truncateByDisplayWidth(text, width)
    val pad = (width - displayWidth(t)).coerceAtLeast(0)
    return t + " ".repeat(pad)
}

fun padLeftByDisplayWidth(text: String, width: Int): String {
    val t = truncateByDisplayWidth(text, width)
    val pad = (width - displayWidth(t)).coerceAtLeast(0)
    return " ".repeat(pad) + t
}

fun wrapTextByDisplayWidth(text: String, width: Int): List<String> {
    if (width <= 0) return emptyList()
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return emptyList()

    val hasSpaces = trimmed.any { it.isWhitespace() }
    if (!hasSpaces) {
        val out = mutableListOf<String>()
        var remaining = trimmed
        while (remaining.isNotEmpty()) {
            val part = truncateByDisplayWidth(remaining, width)
            if (part.isEmpty()) break
            out += part
            remaining = remaining.drop(part.length)
        }
        return out
    }

    val words = trimmed.split(Regex("\\s+")).filter { it.isNotBlank() }
    val out = mutableListOf<String>()
    var current = ""
    fun flush() {
        if (current.isNotBlank()) out += current
        current = ""
    }

    for (w in words) {
        if (current.isEmpty()) {
            if (displayWidth(w) <= width) {
                current = w
            } else {
                var rem = w
                while (rem.isNotEmpty()) {
                    val part = truncateByDisplayWidth(rem, width)
                    if (part.isEmpty()) break
                    out += part
                    rem = rem.drop(part.length)
                }
            }
        } else {
            val cand = "$current $w"
            if (displayWidth(cand) <= width) {
                current = cand
            } else {
                flush()
                if (displayWidth(w) <= width) {
                    current = w
                } else {
                    var rem = w
                    while (rem.isNotEmpty()) {
                        val part = truncateByDisplayWidth(rem, width)
                        if (part.isEmpty()) break
                        out += part
                        rem = rem.drop(part.length)
                    }
                }
            }
        }
    }

    flush()
    return out
}

fun appendWrappedPrefixedLines(
    content: StringBuilder,
    prefix: String,
    text: String,
    width: Int
) {
    val prefixW = displayWidth(prefix)
    val available = (width - prefixW).coerceAtLeast(0)
    val lines = wrapTextByDisplayWidth(text, available)
    val indent = " ".repeat(prefixW)

    if (lines.isEmpty()) {
        content.append(padRightByDisplayWidth(prefix, width)).append("\n")
        return
    }

    lines.forEachIndexed { idx, line ->
        val pre = if (idx == 0) prefix else indent
        content.append(padRightByDisplayWidth(pre + line, width)).append("\n")
    }
}

fun appendBilingualItem(
    content: StringBuilder,
    quantity: Int,
    nameEn: String,
    nameZh: String,
    customizations: List<Pair<String, String>>,
    width: Int
) {
    val prefix = "$quantity x "
    val itemIndent = " ".repeat(displayWidth(prefix))
    appendWrappedPrefixedLines(
        content = content,
        prefix = prefix,
        text = nameEn,
        width = width
    )

    appendWrappedPrefixedLines(
        content = content,
        prefix = itemIndent,
        text = nameZh,
        width = width
    )

    if (customizations.isNotEmpty()) {
        customizations.forEach { (en, zh) ->
            appendWrappedPrefixedLines(
                content = content,
                prefix = itemIndent + "+ ",
                text = en,
                width = width
            )
            appendWrappedPrefixedLines(
                content = content,
                prefix = itemIndent + "  ",
                text = zh,
                width = width
            )
        }
    }

    content.append("\n")
}
