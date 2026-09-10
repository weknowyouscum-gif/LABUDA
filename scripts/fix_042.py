from pathlib import Path

main = Path("app/src/main/java/com/labuda/app/MainActivity.kt")
text = main.read_text(encoding="utf-8")

# Keep the anti-duplicate subscription guard from 0.42.
needle = '''    fun subscription(context: Context): String = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)\n        .getString(KEY_SUB_URL, "").orEmpty()\n'''
insert = needle + '''\n    fun hasSubscription(context: Context, value: String): Boolean {\n        val candidate = value.trim()\n        if (candidate.isBlank()) return false\n        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)\n        val urls = prefs.getStringSet("subscription_urls", emptySet()).orEmpty()\n        return urls.any { it.trim() == candidate } || prefs.getString(KEY_SUB_URL, "").orEmpty().trim() == candidate\n    }\n'''
if "fun hasSubscription(context: Context" not in text and needle in text:
    text = text.replace(needle, insert, 1)

# Re-ping servers periodically so the displayed latency remains useful after network changes.
marker = '''    LaunchedEffect(Unit) { while (true) { connected = prefs.getBoolean(KEY_VPN_RUNNING, false);'''
if "delay(30000)" not in text:
    ping_effect = '''    LaunchedEffect(Unit) {\n        while (true) {\n            delay(30000)\n            subs = subs.map { s -> s.copy(profiles = s.profiles.map { it.copy(latencyMs = ping(it.host, it.port)) }) }\n            selected = selected?.let { old -> subs.flatMap { it.profiles }.firstOrNull { it.raw == old.raw } }\n        }\n    }\n\n'''
    if marker in text:
        text = text.replace(marker, ping_effect + marker, 1)

# Show the subscription's server-side comment in the statistics card as well.
old_call = 'MainScreen(subs, selected, connected, busy, message, stats, dark, { dark = it;'
new_call = 'MainScreen(subs, selected, connected, busy, message, stats, subs.firstOrNull { s -> s.profiles.any { it.raw == selected?.raw } }?.comment.orEmpty(), dark, { dark = it;'
if old_call in text and "stats, subs.firstOrNull" not in text:
    text = text.replace(old_call, new_call, 1)

old_signature = 'private fun MainScreen(subs:List<SubscriptionInfo>,selected:VlessProfile?,connected:Boolean,busy:Boolean,message:String,stats:VpnStatsSnapshot,dark:Boolean,'
new_signature = 'private fun MainScreen(subs:List<SubscriptionInfo>,selected:VlessProfile?,connected:Boolean,busy:Boolean,message:String,stats:VpnStatsSnapshot,subscriptionComment:String,dark:Boolean,'
if old_signature in text:
    text = text.replace(old_signature, new_signature, 1)

old_stats_call = 'VpnStatsCard(stats)'
new_stats_call = 'VpnStatsCard(stats, subscriptionComment)'
if old_stats_call in text:
    text = text.replace(old_stats_call, new_stats_call, 1)

old_stats_fn = '@Composable private fun VpnStatsCard(s:VpnStatsSnapshot){Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp)){Column(Modifier.padding(horizontal=12.dp,vertical=7.dp)){Text("Статистика",fontSize=15.sp,fontWeight=FontWeight.Bold);Text("Трафик: ${formatBytes(s.trafficBytes)}",fontSize=13.sp);if(s.comment.isNotBlank())Text("Комментарий: ${s.comment}",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=12.sp,maxLines=1);Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("Вход: ${formatBytes(s.rxBytes)}",fontSize=12.sp);Text("Выход: ${formatBytes(s.txBytes)}",fontSize=12.sp)}}}}'
new_stats_fn = '@Composable private fun VpnStatsCard(s:VpnStatsSnapshot,subscriptionComment:String){Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp)){Column(Modifier.padding(horizontal=12.dp,vertical=7.dp)){Text("Статистика",fontSize=15.sp,fontWeight=FontWeight.Bold);Text("Трафик: ${formatBytes(s.trafficBytes)}",fontSize=13.sp);val comment=subscriptionComment.ifBlank{s.comment};if(comment.isNotBlank())Text("Комментарий: $comment",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=12.sp,maxLines=1);Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("Вход: ${formatBytes(s.rxBytes)}",fontSize=12.sp);Text("Выход: ${formatBytes(s.txBytes)}",fontSize=12.sp)}}}}'
if old_stats_fn in text:
    text = text.replace(old_stats_fn, new_stats_fn, 1)

main.write_text(text, encoding="utf-8")
print("LABUDA 0.42 subscription comment and periodic ping refresh applied")
