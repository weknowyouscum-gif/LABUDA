from pathlib import Path

path = Path("app/src/main/java/com/labuda/app/MainActivity.kt")
s = path.read_text(encoding="utf-8")

helper = '''
private fun normalizeServerName(name: String, subscriptionTitle: String): String {
    var result = name.trim()
    val titles = listOf(subscriptionTitle, "Подписка")
        .map { it.trim() }
        .filter { it.isNotBlank() && it.length > 1 }
        .distinctBy { it.lowercase() }
    val separators = listOf(" | ", " • ", " · ", " / ", " - ", " – ", " — ", ":")
    for (title in titles) {
        for (separator in separators) {
            result = result.removeSuffix(separator + title).trim()
            result = result.removePrefix(title + separator).trim()
        }
        if (result.endsWith(" $title", ignoreCase = true)) {
            result = result.dropLast(title.length + 1).trim()
        }
        if (result.startsWith("$title ", ignoreCase = true)) {
            result = result.drop(title.length + 1).trim()
        }
    }
    return result.trim().trim('|', '•', '·', ':', '/', '-', '–', '—').trim()
}

private fun normalizeServerNames(profiles: List<VlessProfile>, subscriptionTitle: String): List<VlessProfile> =
    profiles.map { it.copy(name = normalizeServerName(it.name, subscriptionTitle)) }
'''

if "private fun normalizeServerName(" not in s:
    marker = "\nprivate fun parseUserInfo"
    if marker not in s:
        raise SystemExit("parseUserInfo marker not found")
    s = s.replace(marker, "\n" + helper + marker, 1)

old = 'val profiles=VlessParser.parseSubscription(content);if(profiles.isEmpty())'
new = 'val profiles=normalizeServerNames(VlessParser.parseSubscription(content), title);if(profiles.isEmpty())'
if old not in s:
    raise SystemExit("subscription parse expression not found")
s = s.replace(old, new, 1)

path.write_text(s, encoding="utf-8")
