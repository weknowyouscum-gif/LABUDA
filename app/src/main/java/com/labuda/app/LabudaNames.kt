package com.labuda.app

import android.util.Base64
import java.net.URLDecoder

private val ACCOUNT_ID = Regex("[A-Za-z][0-9]{8,}", RegexOption.IGNORE_CASE)

private fun looksLikeBase64(value: String): Boolean {
    val compact = value.replace("\\s".toRegex(), "")
    if (compact.length < 12) return false
    if (compact.any { it.code > 127 }) return false
    if (!compact.matches(Regex("^[A-Za-z0-9+/_-]+=*$"))) return false
    return compact.length % 4 == 0
}

private fun padBase64(value: String): String {
    val compact = value.replace("\\s".toRegex(), "")
    val pad = (4 - compact.length % 4) % 4
    return compact + "=".repeat(pad)
}

internal fun decodeMaybeBase64(raw: String): String {
    var value = raw.trim().trim('"', '\'')
    val prefixed = value.startsWith("base64:", ignoreCase = true)
    if (prefixed) value = value.substringAfter(':').trim()
    if (value.isBlank()) return raw.trim()
    if (!prefixed && !looksLikeBase64(value)) return raw.trim()
    val decoded = runCatching {
        String(Base64.decode(padBase64(value), Base64.DEFAULT), Charsets.UTF_8).trim()
    }.getOrNull() ?: return raw.trim()
    if (decoded.isBlank() || decoded.contains('\uFFFD')) return raw.trim()
    if (decoded.none { it.isLetter() }) return raw.trim()
    if (decoded.any { it.code in 1..8 }) return raw.trim()
    return decoded
}

fun formatSubscriptionTitle(title: String?): String {
    var value = decodeMaybeBase64(title.orEmpty())
    runCatching { if ('%' in value) value = URLDecoder.decode(value, "UTF-8") }
    value = value.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()
    value = value.replace(Regex("\\.(txt|conf|yaml|yml|json)$", RegexOption.IGNORE_CASE), "").trim()
    if (value.isBlank() || value.equals("subscription", true)) return "Подписка"
    return value
}

fun formatSubscriptionComment(comment: String?): String {
    var value = decodeMaybeBase64(comment.orEmpty())
    runCatching { if ('%' in value) value = URLDecoder.decode(value, "UTF-8") }
    value = value.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()
    if (value.equals("Подписка", true) || value.equals("subscription", true)) return ""
    return value
}

fun extractSubscriptionNumber(profiles: List<VlessProfile>): String {
    for (name in profiles.map { it.name }) {
        ACCOUNT_ID.find(name)?.value?.let { return it }
    }
    return ""
}

fun remarkFromRaw(raw: String): String {
    val i = raw.indexOf('#')
    if (i < 0 || i == raw.lastIndex) return ""
    val frag = raw.substring(i + 1).trim()
    return runCatching { URLDecoder.decode(frag, "UTF-8") }.getOrDefault(frag).trim()
}

fun formatServerName(name: String, subscriptionTitle: String, host: String = "", sni: String = ""): String {
    var value = name.trim()
    runCatching { if ('%' in value) value = URLDecoder.decode(value, "UTF-8") }
    val title = formatSubscriptionTitle(subscriptionTitle)
    if (title.isNotBlank() && !title.equals("Подписка", true) && title.length >= 3) {
        val escaped = Regex.escape(title)
        val sep = "[\\s|:/\u2022\u00b7\\-_\u2014\u2013]+"
        value = value.replace(Regex("^$escaped$sep", RegexOption.IGNORE_CASE), "")
        value = value.replace(Regex("$sep$escaped$", RegexOption.IGNORE_CASE), "")
    }
    value = ACCOUNT_ID.replace(value, "")
    value = value.replace(Regex("[\\s|:/\u2022\u00b7]{2,}"), " ")
    value = value.replace(Regex("\\s+"), " ").trim(' ', '|', ':', '/', '•', '·')
    return value.ifBlank { "Сервер" }
}

fun normalizeSubscriptionTitle(title: String?): String = formatSubscriptionTitle(title)
fun cleanServerName(name: String, subscriptionTitle: String, host: String = "", sni: String = ""): String =
    formatServerName(name, subscriptionTitle, host, sni)
