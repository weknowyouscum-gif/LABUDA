package com.labuda.app

import android.content.Context
import android.util.Base64
import androidx.compose.foundation.background
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.net.URLDecoder

private fun decodeMaybeBase64(raw: String): String {
    var value = raw.trim().trim('"', '\'')
    if (value.startsWith("base64:", ignoreCase = true)) {
        value = value.substringAfter(':').trim()
        runCatching {
            String(Base64.decode(value, Base64.DEFAULT), Charsets.UTF_8)
        }.getOrNull()?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
    }
    val compact = value.replace("\\s".toRegex(), "")
    if (compact.length >= 8 && compact.matches(Regex("^[A-Za-z0-9+/_=-]+$")) && !value.contains(' ')) {
        runCatching {
            String(Base64.decode(compact, Base64.DEFAULT), Charsets.UTF_8)
        }.getOrNull()?.trim()?.takeIf { decoded ->
            decoded.isNotBlank() && decoded.any { it.isLetter() } && decoded.none { it.code < 32 && it != '\n' && it != '\r' }
        }?.let { return it }
    }
    return value
}

fun normalizeSubscriptionTitle(title: String?): String {
    var value = decodeMaybeBase64(title.orEmpty())
    runCatching { value = URLDecoder.decode(value, "UTF-8") }
    value = value.replace(Regex("\\.(txt|conf|yaml|yml|json)$", RegexOption.IGNORE_CASE), "").trim()
    if (value.isBlank() || value.equals("subscription", true)) return "Подписка"
    return value
}

fun cleanServerName(name: String, subscriptionTitle: String): String {
    var value = decodeMaybeBase64(name)
    runCatching { value = URLDecoder.decode(value, "UTF-8") }
    value = value.trim()
    val title = normalizeSubscriptionTitle(subscriptionTitle)
    if (title.isNotBlank() && !title.equals("Подписка", true)) {
        val escaped = Regex.escape(title)
        val sep = "[\\s|:/\u2022\u00b7\\-_\u2014\u2013]+"
        value = value.replace(Regex("^$escaped$sep", RegexOption.IGNORE_CASE), "")
        value = value.replace(Regex("$sep$escaped$", RegexOption.IGNORE_CASE), "")
        value = value.replace(Regex(escaped, RegexOption.IGNORE_CASE), " ")
    }
    value = value.replace(Regex("$sep".replace("sep", "[\\s|:/\u2022\u00b7\\-_\u2014\u2013]+") , RegexOption.IGNORE_CASE), " ")
    value = value.replace(Regex("\\s+"), " ").trim(' ', '|', '-', ':', '/', '•', '·')
    return value.ifBlank { "Сервер" }
}

suspend fun importSubscription(context: Context, input: String): Result<SubscriptionPayload> = withContext(Dispatchers.IO) {
    runCatching {
        val source = input.trim()
        if (source.isBlank()) error("Пустой источник подписки")
        var title = ""
        var comment = ""
        var total: Long? = null
        var used = 0L
        var expire: Long? = null
        val content = if (source.startsWith("http://") || source.startsWith("https://")) {
            val c = URL(source).openConnection() as HttpURLConnection
            c.connectTimeout = 15000
            c.readTimeout = 20000
            c.requestMethod = "GET"
            val h = c.headerFields.entries.associate { (k, v) -> (k ?: "").lowercase() to v?.firstOrNull().orEmpty() }
            title = h["profile-title"].orEmpty().ifBlank {
                h["content-disposition"].orEmpty().substringAfter("filename=", "").trim('"', '\'')
            }
            comment = h["profile-comment"].orEmpty().ifBlank { h["profile-description"].orEmpty() }
            parseUserInfo(h["subscription-userinfo"].orEmpty())?.let {
                used = it.used; total = it.total; expire = it.expire
            }
            c.inputStream.bufferedReader().use { it.readText() }.also { c.disconnect() }
        } else source
        val profiles = VlessParser.parseSubscription(content)
        if (profiles.isEmpty()) error("В подписке не найдено корректных VLESS-конфигураций")
        SubscriptionPayload(profiles, normalizeSubscriptionTitle(title), comment, total, used, expire)
    }
}

private data class UserInfo(val used: Long, val total: Long?, val expire: Long?)

private fun parseUserInfo(v: String): UserInfo? {
    if (v.isBlank()) return null
    val m = v.split(';').mapNotNull {
        it.trim().split('=', limit = 2).takeIf { x -> x.size == 2 }?.let { x -> x[0].lowercase() to x[1].toLongOrNull() }
    }.toMap()
    return UserInfo((m["upload"] ?: 0) + (m["download"] ?: 0), m["total"]?.takeIf { it > 0 }, m["expire"]?.takeIf { it > 0 }?.times(1000))
}

fun ping(host: String, port: Int): Long? {
    val t = System.currentTimeMillis()
    return runCatching {
        Socket().use { it.connect(InetSocketAddress(host, port), 2500) }
        System.currentTimeMillis() - t
    }.getOrNull()
}

fun formatBytes(b: Long): String {
    if (b < 1024) return "$b Б"
    if (b < 1024 * 1024) return "%.1f КБ".format(b / 1024.0)
    if (b < 1024 * 1024 * 1024) return "%.1f МБ".format(b / 1024.0 / 1024)
    return "%.1f ГБ".format(b / 1024.0 / 1024 / 1024)
}

fun formatExpiry(ms: Long): String =
    java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault()).format(java.util.Date(ms))

@Composable
fun ImportScreen(
    url: String,
    onUrl: (String) -> Unit,
    busy: Boolean,
    message: String,
    onScanQr: () -> Unit,
    onPickQrImage: () -> Unit,
    onClipboard: () -> Unit,
    onBack: (() -> Unit)?,
    onImport: () -> Unit
) {
    var showQrChoice by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
                Icon(Icons.Filled.ArrowBack, "Назад")
            }
        }
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("LBD", fontSize = 56.sp, fontWeight = FontWeight.Black)
            Text("LABUDA", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(32.dp))
            Card(shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(20.dp)) {
                    Text("Добавить подписку", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(url, onUrl, Modifier.fillMaxWidth(), label = { Text("URL подписки или VLESS") }, singleLine = true)
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button({ showQrChoice = true }, Modifier.weight(1f)) { Text("QR") }
                        Button(onClipboard, Modifier.weight(1f)) {
                            Icon(Icons.Filled.ContentPaste, null)
                            Spacer(Modifier.size(6.dp))
                            Text("Буфер")
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(onImport, Modifier.fillMaxWidth(), enabled = !busy && url.isNotBlank()) {
                        Text(if (busy) "Загрузка…" else "Импортировать подписку")
                    }
                    if (message.isNotBlank()) Text(message, Modifier.padding(top = 10.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (showQrChoice) {
            AlertDialog(
                onDismissRequest = { showQrChoice = false },
                title = { Text("QR-код") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button({ showQrChoice = false; onScanQr() }, Modifier.fillMaxWidth()) { Text("Сканировать QR-код") }
                        Button({ showQrChoice = false; onPickQrImage() }, Modifier.fillMaxWidth()) { Text("Вставить картинку QR-кода") }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton({ showQrChoice = false }) { Text("Отмена") } }
            )
        }
    }
}

@Composable
fun MainScreen(
    subs: List<SubscriptionInfo>,
    selected: VlessProfile?,
    connected: Boolean,
    busy: Boolean,
    message: String,
    stats: VpnStatsSnapshot,
    dark: Boolean,
    onDark: (Boolean) -> Unit,
    onSettings: () -> Unit,
    onSelect: (VlessProfile) -> Unit,
    onConnect: () -> Unit,
    onRefresh: () -> Unit,
    onImport: () -> Unit,
    onFavorite: (VlessProfile) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp).padding(top = 32.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("LABUDA", fontSize = 24.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.DarkMode, "Тёмная тема", Modifier.size(18.dp))
            Switch(dark, onDark)
            IconButton(onSettings) { Icon(Icons.Filled.Settings, "Настройки") }
            IconButton(onRefresh, enabled = !busy) { Icon(Icons.Filled.Refresh, "Обновить") }
            IconButton(onImport) { Icon(Icons.Filled.Add, "Добавить") }
        }
        Spacer(Modifier.height(5.dp))
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(82.dp).background(MaterialTheme.colorScheme.onSurfaceVariant, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("LBD", fontSize = 28.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.surface)
                }
                Spacer(Modifier.height(5.dp))
                Text(if (connected) "Лабуда подключена" else "Лабуда отключена", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Button(
                    onConnect,
                    Modifier.fillMaxWidth().height(42.dp),
                    shape = RoundedCornerShape(14.dp),
                    enabled = selected != null || subs.any { it.profiles.isNotEmpty() }
                ) { Text(if (connected) "Отключить" else "Подключить", fontSize = 15.sp) }
            }
        }
        Spacer(Modifier.height(5.dp))
        VpnStatsCard(stats)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Подписки (${subs.size})", fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (busy) Text("Обновление…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(4.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            subs.forEach { s ->
                item(key = "sub-${s.id}") { SubscriptionHeader(s) }
                items(s.profiles, key = { "${s.id}:${it.raw}" }) { p -> ServerCard(p, selected, onSelect, onFavorite) }
            }
        }
        if (message.isNotBlank()) Text(message, Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SubscriptionHeader(s: SubscriptionInfo) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(normalizeSubscriptionTitle(s.title), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(if (s.totalBytes == null) "\u221e" else "Осталось ${formatBytes((s.totalBytes - s.usedBytes).coerceAtLeast(0))}", fontSize = 12.sp)
            }
            Text("Окончание: ${s.expireAt?.let { formatExpiry(it) } ?: "Без срока"}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (s.comment.isNotBlank()) Text(s.comment, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun ServerCard(p: VlessProfile, selected: VlessProfile?, onSelect: (VlessProfile) -> Unit, onFavorite: (VlessProfile) -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable { onSelect(p) },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = if (selected?.id == p.id) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface)
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(p.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(p.latencyMs?.let { "$it мс" } ?: "\u2014", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
    }
}

@Composable
private fun VpnStatsCard(s: VpnStatsSnapshot) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 7.dp)) {
            Text("Статистика", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text("Трафик: ${formatBytes(s.trafficBytes)}", fontSize = 13.sp)
            if (s.comment.isNotBlank()) Text("Комментарий: ${s.comment}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 1)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Вход: ${formatBytes(s.rxBytes)}", fontSize = 12.sp)
                Text("Выход: ${formatBytes(s.txBytes)}", fontSize = 12.sp)
            }
        }
    }
}
