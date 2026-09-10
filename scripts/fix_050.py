from pathlib import Path

MAIN = Path('app/src/main/java/com/labuda/app/MainActivity.kt')
ROUTING = Path('app/src/main/java/com/labuda/app/RoutingSettingsActivity.kt')

s = MAIN.read_text(encoding='utf-8')
if 'KEY_AUTO_BEST_SERVER' not in s:
    s = s.replace('private const val KEY_SUBSCRIPTIONS = "subscriptions_json"\n', 'private const val KEY_SUBSCRIPTIONS = "subscriptions_json"\nprivate const val KEY_AUTO_BEST_SERVER = "auto_best_server"\n')

old = '''{ if (connected) activity.stopVpn() else activity.startVpn() },'''
new = '''{ if (connected) activity.stopVpn() else {
                    val autoBest = prefs.getBoolean(KEY_AUTO_BEST_SERVER, false)
                    val best = if (autoBest) subs.flatMap { it.profiles }.filter { it.latencyMs != null }.minByOrNull { it.latencyMs!! } else null
                    val target = best ?: selected ?: subs.flatMap { it.profiles }.firstOrNull()
                    target?.let { selected = it; ProfileStore.select(activity, it); activity.startVpn() }
                } },'''
if old in s and 'val autoBest = prefs.getBoolean(KEY_AUTO_BEST_SERVER, false)' not in s:
    s = s.replace(old, new, 1)
MAIN.write_text(s, encoding='utf-8')

r = ROUTING.read_text(encoding='utf-8')
if 'KEY_AUTO_BEST_SERVER' not in r:
    r = r.replace('private const val KEY_ROUTING_APPS = "routing_apps"\n', 'private const val KEY_ROUTING_APPS = "routing_apps"\nprivate const val KEY_AUTO_BEST_SERVER = "auto_best_server"\n')
if 'var autoBest by remember' not in r:
    r = r.replace('var search by remember { mutableStateOf("") }\n', 'var search by remember { mutableStateOf("") }\n    var autoBest by remember { mutableStateOf(context.getSharedPreferences(ROUTING_PREFS, Context.MODE_PRIVATE).getBoolean(KEY_AUTO_BEST_SERVER, false)) }\n')

needle = '                Text("Режим маршрутизации", fontSize = 16.sp)\n'
block = '''                Text("Автовыбор лучшего сервера", fontSize = 16.sp)
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Автоматически выбирать сервер с минимальным пингом", modifier = Modifier.weight(1f), fontSize = 14.sp)
                        androidx.compose.material3.Switch(checked = autoBest, onCheckedChange = { autoBest = it })
                    }
                }
                Spacer(Modifier.height(8.dp))
'''
if 'Автовыбор лучшего сервера' not in r:
    r = r.replace(needle, block + needle, 1)

oldsave = 'Button(onClick = { RoutingStore.save(context, mode, selected); (context as Activity).finish() }'
newsave = 'Button(onClick = { RoutingStore.save(context, mode, selected); context.getSharedPreferences(ROUTING_PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_AUTO_BEST_SERVER, autoBest).apply(); (context as Activity).finish() }'
if oldsave in r:
    r = r.replace(oldsave, newsave, 1)
ROUTING.write_text(r, encoding='utf-8')
