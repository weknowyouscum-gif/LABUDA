package com.labuda.app

import android.util.Base64
import java.net.URLDecoder

private val ACCOUNT_ID = Regex("[A-Za-z][0-9]{8,}", RegexOption.IGNORE_CASE)
private val COUNTRIES = mapOf(
    "nl" to "Нидерланды", "de" to "Германия", "fi" to "Финляндия", "se" to "Швеция",
    "no" to "Норвегия", "dk" to "Дания", "fr" to "Франция", "gb" to "Великобритания",
    "uk" to "Великобритания", "us" to "США", "ca" to "Канада", "pl" to "Польша",
    "cz" to "Чехия", "at" to "Австрия", "ch" to "Швейцария", "it" to "Италия",
    "es" to "Испания", "tr" to "Турция", "ae" to "ОАЭ", "sg" to "Сингапур",
    "jp" to "Япония", "kr" to "Корея", "au" to "Австралия", "lv" to "Латвия",
    "lt" to "Литва", "ee" to "Эстония", "ua" to "Украина", "am" to "Армения",
    "ge" to "Грузия", "kz" to "Казахстан", "ru" to "Россия", "md" to "Молдова",
    "ro" to "Румыния", "bg" to "Болгария", "hu" to "Венгрия", "sk" to "Словакия"
)

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
    for (name in profiles.flatMap { listOf(it.name, remarkFromRaw(it.raw)) }) {
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

fun sharedRemark(profiles: List<VlessProfile>): String {
    val remarks = profiles.map { formatSubscriptionComment(remarkFromRaw(it.raw)) }.filter { it.isNotBlank() }
    return remarks.distinct().singleOrNull().orEmpty()
}

private fun inferCountry(host: String, sni: String): String? {
    val text = "$sni $host".lowercase()
    val tokens = text.split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() }
    for (token in tokens) {
        COUNTRIES[token]?.let { return it }
        val code = token.take(2)
        val rest = token.drop(2)
        if ((rest.isEmpty() || rest.all { it.isDigit() }) && COUNTRIES[code] != null) {
            val country = COUNTRIES[code]!!
            val num = rest.filter { it.isDigit() }
            return if (num.isBlank()) country else "$country-$num"
        }
    }
    return null
}

private fun stripLabel(value: String, label: String): String {
    if (label.isBlank() || label.equals("Подписка", true) || label.length < 3) return value
    val escaped = Regex.escape(label)
    val sep = "[\\s|:/\u2022\u00b7\\-_\u2014\u2013]+"
    return value.replace(Regex("^$escaped$sep", RegexOption.IGNORE_CASE), "")
        .replace(Regex("$sep$escaped$", RegexOption.IGNORE_CASE), "")
        .replace(Regex(escaped, RegexOption.IGNORE_CASE), " ")
}

fun formatServerName(
    name: String,
    subscriptionTitle: String,
    host: String = "",
    sni: String = "",
    comment: String = ""
): String {
    var value = name.trim()
    runCatching { if ('%' in value) value = URLDecoder.decode(value, "UTF-8") }
    value = stripLabel(value, formatSubscriptionTitle(subscriptionTitle))
    value = stripLabel(value, formatSubscriptionComment(comment))
    value = ACCOUNT_ID.replace(value, "")
    value = value.replace(Regex("[\\s|:/\u2022\u00b7]{2,}"), " ")
    value = value.replace(Regex("\\s+"), " ").trim(' ', '|', ':', '/', '•', '·')
    if (value.isNotBlank() && value.any { it.isLetter() } && !value.equals(formatSubscriptionComment(comment), true)) return value
    return inferCountry(host, sni) ?: value.ifBlank { "Сервер" }
}

fun normalizeSubscriptionTitle(title: String?): String = formatSubscriptionTitle(title)
fun cleanServerName(name: String, subscriptionTitle: String, host: String = "", sni: String = "", comment: String = ""): String =
    formatServerName(name, subscriptionTitle, host, sni, comment)
