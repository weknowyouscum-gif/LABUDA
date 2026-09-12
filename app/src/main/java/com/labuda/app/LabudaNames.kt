package com.labuda.app

import android.util.Base64
import java.net.URLDecoder

private fun padBase64(value: String): String {
    val compact = value.replace("\\s".toRegex(), "")
    val pad = (4 - compact.length % 4) % 4
    return compact + "=".repeat(pad)
}

internal fun decodeMaybeBase64(raw: String): String {
    var value = raw.trim().trim('"', '\'')
    if (value.startsWith("base64:", ignoreCase = true)) {
        value = value.substringAfter(':').trim()
    }
    if (value.isBlank()) return value
    val padded = padBase64(value)
    val decoded = runCatching {
        String(Base64.decode(padded, Base64.DEFAULT), Charsets.UTF_8).trim()
    }.getOrNull()
    if (!decoded.isNullOrBlank() && decoded.any { it.isLetter() } && decoded.none { it.code in 1..8 }) {
        return decoded
    }
    return raw.trim()
}

fun formatSubscriptionTitle(title: String?): String {
    var value = decodeMaybeBase64(title.orEmpty())
    runCatching { value = URLDecoder.decode(value, "UTF-8") }
    value = value.replace(Regex("\\.(txt|conf|yaml|yml|json)$", RegexOption.IGNORE_CASE), "").trim()
    if (value.isBlank() || value.equals("subscription", true)) return "Подписка"
    return value
}

fun formatServerName(name: String, subscriptionTitle: String): String {
    var value = decodeMaybeBase64(name)
    runCatching { value = URLDecoder.decode(value, "UTF-8") }
    value = value.trim()
    val title = formatSubscriptionTitle(subscriptionTitle)
    if (title.isNotBlank() && !title.equals("Подписка", true)) {
        val escaped = Regex.escape(title)
        val sep = "[\\s|:/\u2022\u00b7\\-_\u2014\u2013]+"
        value = value.replace(Regex("^$escaped$sep", RegexOption.IGNORE_CASE), "")
        value = value.replace(Regex("$sep$escaped$", RegexOption.IGNORE_CASE), "")
        value = value.replace(Regex(escaped, RegexOption.IGNORE_CASE), " ")
    }
    val slash = value.split(Regex("\\s*/\\s*"))
    if (slash.size >= 2) {
        val right = slash.last().trim()
        val looksLikeCode = right.matches(Regex("[A-Za-z]\\d{5,}")) || right.matches(Regex("[A-Z0-9_-]{8,}"))
        if (looksLikeCode) value = slash.dropLast(1).joinToString(" / ")
    }
    value = value.replace(Regex("[\\s|:/\u2022\u00b7]{2,}"), " ")
    value = value.replace(Regex("\\s+"), " ").trim(' ', '|', ':', '/', '•', '·')
    return value.ifBlank { "Сервер" }
}

fun normalizeSubscriptionTitle(title: String?): String = formatSubscriptionTitle(title)
fun cleanServerName(name: String, subscriptionTitle: String): String = formatServerName(name, subscriptionTitle)
