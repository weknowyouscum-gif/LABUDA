from pathlib import Path

main = Path("app/src/main/java/com/labuda/app/MainActivity.kt")
text = main.read_text(encoding="utf-8")

# Keep the existing build-time Kotlin escape fix.
old = ".trimEnd(',', ';', '\\\\r', '\\\\n')"
new = ".trimEnd(',', ';', '\\r', '\\n')"
if old in text:
    text = text.replace(old, new, 1)

# Routing button in the top-right of the main screen.
text = text.replace(
    'import androidx.compose.material.icons.filled.Refresh\n',
    'import androidx.compose.material.icons.filled.Refresh\nimport androidx.compose.material.icons.filled.Settings\n',
    1,
)
text = text.replace(
    '                    onImport = { subscriptionUrl = ""; showImport = true },\n                    onFavorite = { profile ->',
    '                    onImport = { subscriptionUrl = ""; showImport = true },\n                    onRouting = { activity.startActivity(Intent(activity, RoutingSettingsActivity::class.java)) },\n                    onFavorite = { profile ->',
    1,
)
text = text.replace(
    '    onSelect: (VlessProfile) -> Unit, onConnect: () -> Unit, onRefresh: () -> Unit, onImport: () -> Unit, onFavorite: (VlessProfile) -> Unit\n',
    '    onSelect: (VlessProfile) -> Unit, onConnect: () -> Unit, onRefresh: () -> Unit, onImport: () -> Unit, onRouting: () -> Unit, onFavorite: (VlessProfile) -> Unit\n',
    1,
)
text = text.replace(
    '            Text("LABUDA", fontSize = 28.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))\n            IconButton(onClick = onRefresh, enabled = !busy) { Icon(Icons.Filled.Refresh, "Обновить") }\n            IconButton(onClick = onImport) { Icon(Icons.Filled.Add, "Добавить") }',
    '            Text("LABUDA", fontSize = 24.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))\n            IconButton(onClick = onRouting) { Icon(Icons.Filled.Settings, "Маршрутизация") }\n            IconButton(onClick = onRefresh, enabled = !busy) { Icon(Icons.Filled.Refresh, "Обновить") }\n            IconButton(onClick = onImport) { Icon(Icons.Filled.Add, "Добавить") }',
    1,
)

# Make the connection card much shorter so the server list gets most of the screen.
text = text.replace('Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {', 'Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {', 1)
text = text.replace('Row(Modifier.fillMaxWidth().padding(top = 16.dp)', 'Row(Modifier.fillMaxWidth().padding(top = 6.dp)', 1)
text = text.replace('        Spacer(Modifier.height(12.dp))\n        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp)', '        Spacer(Modifier.height(4.dp))\n        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)', 1)
text = text.replace('Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally)', 'Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally)', 1)
text = text.replace('Box(Modifier.size(150.dp).background(', 'Box(Modifier.size(82.dp).background(', 1)
text = text.replace('Text("LBD", fontSize = 42.sp', 'Text("LBD", fontSize = 28.sp', 1)
text = text.replace('Spacer(Modifier.height(12.dp))\n                Text(if (connected)', 'Spacer(Modifier.height(5.dp))\n                Text(if (connected)', 1)
text = text.replace('fontSize = 22.sp, fontWeight = FontWeight.Bold)', 'fontSize = 17.sp, fontWeight = FontWeight.Bold)', 1)
text = text.replace('Text(selected?.name ?: "Выберите сервер", color = Color.Gray)\n                Spacer(Modifier.height(14.dp))', 'Text(selected?.name ?: "Выберите сервер", color = Color.Gray, fontSize = 12.sp)\n                Spacer(Modifier.height(6.dp))', 1)
text = text.replace('Button(onClick = onConnect, Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)', 'Button(onClick = onConnect, Modifier.fillMaxWidth().height(42.dp), shape = RoundedCornerShape(14.dp)', 1)
text = text.replace('fontSize = 17.sp)', 'fontSize = 15.sp)', 1)
text = text.replace('        Spacer(Modifier.height(12.dp))\n        VpnStatsCard(vpnStats)\n        Spacer(Modifier.height(16.dp))', '        Spacer(Modifier.height(5.dp))\n        VpnStatsCard(vpnStats)\n        Spacer(Modifier.height(6.dp))', 1)
text = text.replace('            Text("Серверы (${profiles.size})", fontSize = 19.sp', '            Text("Серверы (${profiles.size})", fontSize = 17.sp', 1)
text = text.replace('        Spacer(Modifier.height(8.dp))\n        LazyColumn', '        Spacer(Modifier.height(4.dp))\n        LazyColumn', 1)

# Compact four-line statistics: total traffic, optional comment, inbound bytes, outbound bytes.
old_stats = '''@Composable\nprivate fun VpnStatsCard(stats: VpnStatsSnapshot) {\n    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {\n        Column(Modifier.padding(18.dp)) {\n            Text("Статистика VPN", fontSize = 18.sp, fontWeight = FontWeight.Bold)\n            Spacer(Modifier.height(10.dp))\n            Text("Трафик: ${formatBytes(stats.trafficBytes)}", fontSize = 16.sp)\n            Spacer(Modifier.height(5.dp))\n            Text("Комментарий из VPN: ${stats.comment.ifBlank { "—" }}", color = Color.Gray)\n            Spacer(Modifier.height(5.dp))\n            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {\n                Text("Вход: ${formatSpeed(stats.rxSpeed)}", fontSize = 14.sp)\n                Text("Выход: ${formatSpeed(stats.txSpeed)}", fontSize = 14.sp)\n            }\n        }\n    }\n}\n'''
new_stats = '''@Composable\nprivate fun VpnStatsCard(stats: VpnStatsSnapshot) {\n    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {\n        Column(Modifier.padding(horizontal = 12.dp, vertical = 7.dp)) {\n            Text("Статистика", fontSize = 15.sp, fontWeight = FontWeight.Bold)\n            Text("Трафик: ${formatBytes(stats.trafficBytes)}", fontSize = 13.sp)\n            if (stats.comment.isNotBlank()) Text("Комментарий: ${stats.comment}", color = Color.Gray, fontSize = 12.sp, maxLines = 1)\n            Text("Вход: ${formatBytes(stats.rxBytes)}", fontSize = 12.sp)\n            Text("Выход: ${formatBytes(stats.txBytes)}", fontSize = 12.sp)\n        }\n    }\n}\n'''
if old_stats not in text:
    raise SystemExit("Expected VpnStatsCard block not found")
text = text.replace(old_stats, new_stats, 1)

# Persist/load the actual inbound/outbound byte counters and hide generated status text as a comment.
text = text.replace(
    'data class VpnStatsSnapshot(\n    val trafficBytes: Long = 0L,\n    val rxSpeed: Long = 0L,\n    val txSpeed: Long = 0L,\n    val comment: String = "Отключено"\n)',
    'data class VpnStatsSnapshot(\n    val trafficBytes: Long = 0L,\n    val rxBytes: Long = 0L,\n    val txBytes: Long = 0L,\n    val rxSpeed: Long = 0L,\n    val txSpeed: Long = 0L,\n    val comment: String = ""\n)',
    1,
)
text = text.replace(
    '                trafficBytes = prefs.getLong(VpnStats.KEY_RX, 0L) + prefs.getLong(VpnStats.KEY_TX, 0L),\n                rxSpeed = prefs.getLong(VpnStats.KEY_RX_SPEED, 0L),\n                txSpeed = prefs.getLong(VpnStats.KEY_TX_SPEED, 0L),\n                comment = prefs.getString(VpnStats.KEY_COMMENT, if (connected) "Подключено" else "Отключено").orEmpty()',
    '                trafficBytes = prefs.getLong(VpnStats.KEY_RX, 0L) + prefs.getLong(VpnStats.KEY_TX, 0L),\n                rxBytes = prefs.getLong(VpnStats.KEY_RX, 0L),\n                txBytes = prefs.getLong(VpnStats.KEY_TX, 0L),\n                rxSpeed = prefs.getLong(VpnStats.KEY_RX_SPEED, 0L),\n                txSpeed = prefs.getLong(VpnStats.KEY_TX_SPEED, 0L),\n                comment = prefs.getString(VpnStats.KEY_COMMENT, "").orEmpty().let { value ->\n                    if (value.startsWith("Подключено") || value.startsWith("Отключено") || value.startsWith("Ошибка VPN") || value.startsWith("Подключение")) "" else value\n                }',
    1,
)

# The list is already on the same screen; keep it persistent and make each row more compact.
text = text.replace('Card(Modifier.fillMaxWidth().clickable { onSelect(profile) }, shape = RoundedCornerShape(18.dp)', 'Card(Modifier.fillMaxWidth().clickable { onSelect(profile) }, shape = RoundedCornerShape(14.dp)', 1)
text = text.replace('Row(Modifier.fillMaxWidth().padding(14.dp)', 'Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp)', 1)

main.write_text(text, encoding="utf-8")
print("Applied LABUDA 0.41 compact one-screen UI and routing button")
