package com.labuda.app

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL

private fun httpHeaders(c: HttpURLConnection): Map<String, String> =
    c.headerFields.entries.associate { (k, v) -> (k ?: "").lowercase() to v?.firstOrNull().orEmpty() }

private fun bodyMeta(content: String): Map<String, String> {
    val out = mutableMapOf<String, String>()
    val keys = setOf("profile-title", "subscription-name", "profile-comment", "profile-description", "subscription-comment", "subscription-userinfo")
    content.lineSequence().take(25).forEach { raw ->
        var line = raw.trim()
        if (line.startsWith("#")) line = line.removePrefix("#").trim()
        if (line.startsWith("//")) line = line.removePrefix("//").trim()
        val i = line.indexOf(':')
        if (i <= 0) return@forEach
        val key = line.substring(0, i).trim().lowercase()
        val value = line.substring(i + 1).trim()
        if (key in keys && value.isNotBlank()) out.putIfAbsent(key, value)
    }
    return out
}

private fun metaValue(maps: List<Map<String, String>>, keys: List<String>): String {
    for (key in keys) for (m in maps) { val v = m[key]?.trim().orEmpty(); if (v.isNotBlank()) return v }
    return ""
}

suspend fun importSubscription(context: Context, input: String): Result<SubscriptionPayload> = withContext(Dispatchers.IO) {
    runCatching {
        val source = input.trim()
        if (source.isBlank()) error("Пустой источник подписки")
        var headers = emptyMap<String, String>()
        val content = if (source.startsWith("http://") || source.startsWith("https://")) {
            val c = URL(source).openConnection() as HttpURLConnection
            c.connectTimeout = 15000; c.readTimeout = 20000; c.requestMethod = "GET"
            headers = httpHeaders(c)
            c.inputStream.bufferedReader().use { it.readText() }.also { c.disconnect() }
        } else source
        val maps = listOf(headers, bodyMeta(content))
        val title = metaValue(maps, listOf("profile-title", "subscription-name")).ifBlank {
            headers["content-disposition"].orEmpty().substringAfter("filename=", "").trim('"', '\'')
        }
        val comment = metaValue(maps, listOf("profile-comment", "profile-description", "subscription-comment"))
        var total: Long? = null; var used = 0L; var expire: Long? = null
        parseUserInfo(metaValue(maps, listOf("subscription-userinfo")))?.let { used = it.used; total = it.total; expire = it.expire }
        val profiles = VlessParser.parseSubscription(content)
        if (profiles.isEmpty()) error("В подписке не найдено корректных VLESS-конфигураций")
        SubscriptionPayload(profiles, normalizeSubscriptionTitle(title), formatSubscriptionComment(comment), total, used, expire)
    }
}

private data class UserInfo(val used: Long, val total: Long?, val expire: Long?)
private fun parseUserInfo(v: String): UserInfo? {
    if (v.isBlank()) return null
    val m = v.split(';').mapNotNull { it.trim().split('=', limit = 2).takeIf { x -> x.size == 2 }?.let { x -> x[0].lowercase() to x[1].toLongOrNull() } }.toMap()
    return UserInfo((m["upload"] ?: 0) + (m["download"] ?: 0), m["total"]?.takeIf { it > 0 }, m["expire"]?.takeIf { it > 0 }?.times(1000))
}
fun ping(host: String, port: Int): Long? {
    val t = System.currentTimeMillis()
    return runCatching { Socket().use { it.connect(InetSocketAddress(host, port), 2500) }; System.currentTimeMillis() - t }.getOrNull()
}
fun formatBytes(b: Long): String {
    if (b < 1024) return "$b Б"
    if (b < 1024 * 1024) return "%.1f КБ".format(b / 1024.0)
    if (b < 1024 * 1024 * 1024) return "%.1f МБ".format(b / 1024.0 / 1024)
    return "%.1f ГБ".format(b / 1024.0 / 1024 / 1024)
}
fun formatExpiry(ms: Long): String = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault()).format(java.util.Date(ms))

@Composable
private fun NeonRing(connected: Boolean, size: Int = 132, textSize: Int = 36) {
    val ring = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Box(
        Modifier.size(size.dp).shadow(if (connected) 28.dp else 8.dp, CircleShape).border(4.dp, ring, CircleShape).padding(7.dp).border(2.dp, ring.copy(alpha = 0.4f), CircleShape),
        contentAlignment = Alignment.Center
    ) { Text("LBD", fontSize = textSize.sp, fontWeight = FontWeight.Black, color = ring, letterSpacing = 2.sp) }
}

@Composable
fun ImportScreen(
    url: String, onUrl: (String) -> Unit, busy: Boolean, message: String,
    onScanQr: () -> Unit, onPickQrImage: () -> Unit, onClipboard: () -> Unit,
    onBack: (() -> Unit)?, onImport: () -> Unit
) {
    var showQrChoice by remember { mutableStateOf(false) }
    val purple = MaterialTheme.colorScheme.primary
    val pill = RoundedCornerShape(18.dp)
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (onBack != null) IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
            Icon(Icons.Filled.ArrowBack, "Назад", tint = purple)
        }
        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            NeonRing(true, 140, 38)
            Spacer(Modifier.height(10.dp))
            Text("LABUDA", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = purple, letterSpacing = 8.sp)
            Spacer(Modifier.height(22.dp))
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, purple.copy(alpha = 0.55f))) {
                Column(Modifier.padding(20.dp)) {
                    Text("Добавить подписку", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = purple, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        url, onUrl, Modifier.fillMaxWidth(),
                        label = { Text("URL подписки или VLESS") }, singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = purple, unfocusedBorderColor = purple.copy(alpha = 0.45f), focusedLabelColor = purple, cursorColor = purple)
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton({ showQrChoice = true }, Modifier.weight(1f).height(48.dp), shape = pill, border = BorderStroke(1.dp, purple), colors = ButtonDefaults.outlinedButtonColors(contentColor = purple)) { Text("QR") }
                        OutlinedButton(onClipboard, Modifier.weight(1f).height(48.dp), shape = pill, border = BorderStroke(1.dp, purple), colors = ButtonDefaults.outlinedButtonColors(contentColor = purple)) {
                            Icon(Icons.Filled.ContentPaste, null, Modifier.size(16.dp)); Spacer(Modifier.size(6.dp)); Text("Буфер")
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(onImport, Modifier.fillMaxWidth().height(52.dp), enabled = !busy && url.isNotBlank(), shape = RoundedCornerShape(26.dp), colors = ButtonDefaults.buttonColors(containerColor = purple, contentColor = Color.White)) {
                        Text(if (busy) "Загрузка…" else "Импортировать подписку", fontWeight = FontWeight.Bold)
                    }
                    if (message.isNotBlank()) Text(message, Modifier.padding(top = 10.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (showQrChoice) AlertDialog(
            onDismissRequest = { showQrChoice = false }, title = { Text("QR-код", color = purple) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({ showQrChoice = false; onScanQr() }, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = purple)) { Text("Сканировать QR-код") }
                Button({ showQrChoice = false; onPickQrImage() }, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = purple)) { Text("Вставить картинку QR-кода") }
            } }, confirmButton = {}, dismissButton = { TextButton({ showQrChoice = false }) { Text("Отмена", color = purple) } }
        )
    }
}

@Composable
fun MainScreen(
    subs: List<SubscriptionInfo>, selected: VlessProfile?, connected: Boolean, busy: Boolean, message: String,
    stats: VpnStatsSnapshot, dark: Boolean, onDark: (Boolean) -> Unit, onSettings: () -> Unit,
    onSelect: (VlessProfile) -> Unit, onConnect: () -> Unit, onRefresh: () -> Unit, onImport: () -> Unit, onFavorite: (VlessProfile) -> Unit
) {
    val purple = MaterialTheme.colorScheme.primary
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 16.dp).padding(top = 18.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.DarkMode, null, Modifier.size(18.dp), tint = purple)
            Switch(dark, onDark, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = purple))
            Text("LABUDA", fontSize = 22.sp, fontWeight = FontWeight.Black, color = purple, letterSpacing = 3.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            IconButton(onSettings) { Icon(Icons.Filled.Settings, "Настройки", tint = purple) }
            IconButton(onRefresh, enabled = !busy) { Icon(Icons.Filled.Refresh, "Обновить", tint = purple) }
            IconButton(onImport) { Icon(Icons.Filled.Add, "Добавить", tint = purple) }
        }
        Spacer(Modifier.height(8.dp))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            NeonRing(connected)
            Spacer(Modifier.height(12.dp))
            Text(if (connected) "Лабуда подключена" else "Лабуда отключена", fontSize = 18.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(12.dp))
            Button(
                onConnect, Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(28.dp),
                enabled = selected != null || subs.any { it.profiles.isNotEmpty() },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = Color.White),
                border = BorderStroke(2.dp, purple)
            ) { Text(if (connected) "Отключить" else "Подключить", fontSize = 17.sp, fontWeight = FontWeight.Bold) }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatChip("ТРАФИК", formatBytes(stats.trafficBytes), Modifier.weight(1f))
            StatChip("ВХОД", formatBytes(stats.rxBytes), Modifier.weight(1f))
            StatChip("ВЫХОД", formatBytes(stats.txBytes), Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        if (busy) Text("Обновление…", color = purple, fontSize = 12.sp)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            subs.forEach { s ->
                item(key = "sub-${s.id}") { SubscriptionHeader(s) }
                items(s.profiles, key = { "${s.id}:${it.raw}" }) { p -> ServerCard(p, s.title, selected, onSelect) }
            }
        }
        if (message.isNotBlank()) Text(message, Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StatChip(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 0.6.sp)
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SubscriptionHeader(s: SubscriptionInfo) {
    val number = extractSubscriptionNumber(s.profiles)
    val title = normalizeSubscriptionTitle(s.title)
    val comment = formatSubscriptionComment(s.comment).ifBlank { if (title != number && title != "Подписка") title else "" }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (number.isNotBlank()) number else title, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1)
                if (comment.isNotBlank()) Text(comment, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, maxLines = 1)
            }
            Text(if (s.totalBytes == null) "\u221e" else formatBytes((s.totalBytes - s.usedBytes).coerceAtLeast(0)), color = MaterialTheme.colorScheme.primary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ServerCard(p: VlessProfile, subscriptionTitle: String, selected: VlessProfile?, onSelect: (VlessProfile) -> Unit) {
    val on = selected?.raw == p.raw || selected?.id == p.id
    Card(
        Modifier.fillMaxWidth().clickable { onSelect(p) }, shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(if (on) 2.dp else 1.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(formatServerName(p.name, subscriptionTitle), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1)
            Text(p.latencyMs?.let { "$it мс" } ?: "\u2014", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}
