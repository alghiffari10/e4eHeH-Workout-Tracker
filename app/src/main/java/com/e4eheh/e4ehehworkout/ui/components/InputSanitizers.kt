package com.e4eheh.e4ehehworkout.ui.components

internal fun sanitizeNonNegativeInt(raw: String): String =
    raw.filter { it.isDigit() }.take(6)

internal fun sanitizeNonNegativeDecimal(raw: String): String {
    val builder = StringBuilder()
    var seenDecimal = false
    for (ch in raw) {
        when {
            ch.isDigit() -> builder.append(ch)
            ch == '.' && !seenDecimal -> {
                seenDecimal = true
                builder.append(ch)
            }
        }
        if (builder.length >= 9) break
    }
    return builder.toString()
}

internal fun sanitizeDateText(raw: String): String =
    raw.filter { it.isDigit() || it == '-' }.take(10)
