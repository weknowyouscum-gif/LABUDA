from pathlib import Path

path = Path("app/src/main/java/com/labuda/app/MainActivity.kt")
s = path.read_text(encoding="utf-8")

repls = {
    '.put("title", "Подписка")': '.put("title", s.title.ifBlank { "LABUDA" })',
    'listOf(SubscriptionInfo(url.hashCode().toString(), url, "Подписка", profiles = profiles))': 'listOf(SubscriptionInfo(url.hashCode().toString(), url, "LABUDA", profiles = profiles))',
    'add(SubscriptionInfo(o.optString("id"), o.optString("url"), "Подписка", o.optString("comment"),': 'add(SubscriptionInfo(o.optString("id"), o.optString("url"), o.optString("title").ifBlank { "LABUDA" }, o.optString("comment"),',
    'subs = subs.map { s -> s.copy(title = "Подписка", profiles = s.profiles.map { it.copy(latencyMs = ping(it.host, it.port)) }) }': 'subs = subs.map { s -> s.copy(profiles = s.profiles.map { it.copy(latencyMs = ping(it.host, it.port)) }) }',
    'SubscriptionInfo(url.hashCode().toString(), url, "Подписка", p.comment,': 'SubscriptionInfo(url.hashCode().toString(), url, p.title.ifBlank { "LABUDA" }, p.comment,',
    's.copy(title="Подписка",comment=p.comment.ifBlank{s.comment},': 's.copy(title=p.title.ifBlank{s.title}.ifBlank{"LABUDA"},comment=p.comment.ifBlank{s.comment},',
    's.copy(title="Подписка",profiles=s.profiles.map': 's.copy(profiles=s.profiles.map',
    'Text("Подписка",fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))': 'Text(s.title.ifBlank { "LABUDA" },fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))',
    'importSubscription(activity, importUrl).onSuccess { p ->': 'importSubscription(activity, importUrl).onSuccessSuspend { p ->',
    'withContext(Dispatchers.IO){p.profiles.map{it.copy(latencyMs=ping(it.host,it.port))}}': 'runBlocking(Dispatchers.IO){p.profiles.map{it.copy(latencyMs=ping(it.host,it.port))}}',
}
for old, new in repls.items():
    s = s.replace(old, new)

if 'import kotlinx.coroutines.runBlocking' not in s:
    s = s.replace('import kotlinx.coroutines.launch\n', 'import kotlinx.coroutines.launch\nimport kotlinx.coroutines.runBlocking\n')

if 'private suspend inline fun <T> Result<T>.onSuccessSuspend' not in s:
    marker = '@Composable\nprivate fun ImportScreen'
    helper = '''private suspend inline fun <T> Result<T>.onSuccessSuspend(crossinline action: suspend (T) -> Unit): Result<T> {\n    if (isSuccess) action(getOrThrow())\n    return this\n}\n\n@Composable\nprivate fun ImportScreen'''
    s = s.replace(marker, helper, 1)

path.write_text(s, encoding="utf-8")
