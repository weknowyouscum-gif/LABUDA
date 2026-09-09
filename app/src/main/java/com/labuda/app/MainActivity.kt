package com.labuda.app

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
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
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.net.Socket

private const val PREFS = "labuda"
private const val KEY_SUB_URL = "subscription_url"
private const val KEY_PROFILES = "profiles"
private const val KEY_SELECTED_ID = "selected_profile_id"
private const val KEY_VPN_RUNNING = "vpn_running"
private const val KEY_VPN_ERROR = "vpn_error"

class MainActivity : ComponentActivity() {
    private val vpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) startVpnService()
    }

    private val qrImport = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val text = clipboard.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
            if (text.isNotBlank()) qrResult = text
        }
    }

    private var qrResult by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LabudaApp(this) }
    }

    fun scanQr() { qrImport.launch(Intent(this, QrScannerActivity::class.java)) }

    fun consumeQrResult(): String {
        val value = qrResult
        qrResult = ""
        return value
    }

    fun startVpn() {
        val intent = VpnService.prepare(this)
        if (intent != null) vpnPermission.launch(intent) else startVpnService()
    }

    private fun startVpnService() {
        startService(Intent(this, LabudaVpnService::class.java).setAction(LabudaVpnService.ACTION_START))
    }

    fun stopVpn() {
        startService(Intent(this, LabudaVpnService::class.java).setAction(LabudaVpnService.ACTION_STOP))
    }
}

data class VlessProfile(
    val id: String,
    val name: String,
    val uuid: String,
    val host: String,
    val port: Int,
    val security: String,
    val network: String,
    val type: String,
    val path: String,
    val sni: String,
    val fingerprint: String,
    val publicKey: String,
    val shortId: String,
    val raw: String,
    val latencyMs: Long? = null,
    val favorite: Boolean = false
)

object VlessParser {
    private val VLESS_PATTERN = Regex("vless://[^\\s\\\"<>]+", RegexOption.IGNORE_CASE)

    fun parseSubscription(input: String): List<VlessProfile> {
        val decoded = decodeSubscription(input.trim())
        return VLESS_PATTERN.findAll(decoded)
            .map { it.value.trim().trimEnd(',', ';', '\\r', '\\n') }
            .mapNotNull { parseUri(it) }
            .distinctBy { it.raw }
            .mapIndexed { index, profile -> profile.copy(id = "${profile.host}:${profile.port}:$index") }
            .toList()
    }

    private fun decodeSubscription(value: String): String {
        val normalizedValue = value.replace("\\\\r", "\\n").replace("\\\\n", "\\n")
        if (normalizedValue.contains("vless://", ignoreCase = true)) return normalizedValue
        val normalized = normalizedValue.replace("\\s".toRegex(), "").replace('-', '+').replace('_', '/')
        return try {
            val decoded = String(android.util.Base64.decode(normalized, android.util.Base64.DEFAULT), Charsets.UTF_8)
            if (decoded.contains("vless://", ignoreCase = true)) decoded else normalizedValue
        } catch (_: Exception) { normalizedValue }
    }

    private fun parseUri(raw: String): VlessProfile? {
        return try {
            val uri = URI(raw)
            val user = uri.userInfo ?: return null
            val uuid = user.substringBefore(':')
            if (uuid.isBlank() || uri.host.isNullOrBlank()) return null
            val query = uri.rawQuery.orEmpty().split('&').mapNotNull { part ->
                val pair = part.split('=', limit = 2)
                if (pair.size == 2) pair[0] to URLDecoder.decode(pair[1], "UTF-8") else null
            }.toMap()
            VlessProfile(
                id = raw.hashCode().toString(),
                name = URLDecoder.decode(uri.fragment.orEmpty().ifBlank { uri.host }, "UTF-8"),
                uuid = uuid,
                host = uri.host!!,
                port = if (uri.port > 0) uri.port else 443,
                security = query["security"].orEmpty(),
                network = query["type"].orEmpty().ifBlank { query["network"].orEmpty().ifBlank { "tcp" } },
                type = query["type"].orEmpty().ifBlank { "tcp" },
                path = query["path"].orEmpty(),
                sni = query["sni"].orEmpty().ifBlank { query["host"].orEmpty() },
                fingerprint = query["fp"].orEmpty(),
                publicKey = query["pbk"].orEmpty(),
                shortId = query["sid"].orEmpty(),
                raw = raw
            )
        } catch (_: Exception) { null }
    }
}

object ProfileStore {
    fun save(context: Context, subscription: String, profiles: List<VlessProfile>, selectedId: String? = null) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val editor = prefs.edit()
            .putString(KEY_SUB_URL, subscription)
            .putString(KEY_PROFILES, profiles.joinToString("\n") { it.raw })
        selectedId?.let { editor.putString(KEY_SELECTED_ID, it) }
        editor.apply()
    }

    fun addSubscription(context: Context, subscription: String, newProfiles: List<VlessProfile>, selectedId: String? = null) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val oldProfiles = profiles(context)
        val mergedProfiles = (oldProfiles + newProfiles).distinctBy { it.raw }
        val oldSubscriptions = prefs.getStringSet("subscription_urls", emptySet()).orEmpty()
        val mergedSubscriptions = LinkedHashSet<String>().apply {
            addAll(oldSubscriptions)
            val old = prefs.getString(KEY_SUB_URL, "").orEmpty().trim()
            if (old.isNotBlank()) add(old)
            if (subscription.isNotBlank()) add(subscription.trim())
        }
        prefs.edit()
            .putString(KEY_SUB_URL, subscription.trim())
            .putStringSet("subscription_urls", mergedSubscriptions)
            .putString(KEY_PROFILES, mergedProfiles.joinToString("\n") { it.raw })
            .apply()
        selectedId?.let { select(context, newProfiles.firstOrNull { p -> p.id == it } ?: newProfiles.firstOrNull() ?: return) }
    }

    fun subscription(context: Context): String = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(KEY_SUB_URL, "").orEmpty()

    fun profiles(context: Context): List<VlessProfile> = VlessParser.parseSubscription(
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_PROFILES, "").orEmpty()
    )

    fun selectedProfile(context: Context): VlessProfile? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val profiles = profiles(context)
        val id = prefs.getString(KEY_SELECTED_ID, null)
        return profiles.firstOrNull { it.id == id } ?: profiles.firstOrNull()
    }

    fun select(context: Context, profile: VlessProfile) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_SELECTED_ID, profile.id).apply()
    }
}

data class VpnStatsSnapshot(
    val trafficBytes: Long = 0L,
    val rxSpeed: Long = 0L,
    val txSpeed: Long = 0L,
    val comment: String = "Отключено"
)

@Composable
private fun LabudaApp(activity: MainActivity) {
    var profiles by remember { mutableStateOf(ProfileStore.profiles(activity)) }
    var subscriptionUrl by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(ProfileStore.selectedProfile(activity)) }
    var showImport by remember { mutableStateOf(profiles.isEmpty()) }
    var connected by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var vpnStats by remember { mutableStateOf(VpnStatsSnapshot()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val qr = activity.consumeQrResult()
        if (qr.isNotBlank()) {
            subscriptionUrl = qr
            message = "QR распознан. Нажми «Импортировать всю подписку»."
            showImport = true
        }
        if (profiles.isNotEmpty()) profiles = profiles.map { it.copy(latencyMs = ping(it.host, it.port)) }
        if (profiles.isNotEmpty() && selected == null) selected = profiles.first()
    }

    LaunchedEffect(Unit) {
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        while (true) {
            connected = prefs.getBoolean(KEY_VPN_RUNNING, false)
            val vpnError = prefs.getString(KEY_VPN_ERROR, null)
            if (vpnError != null && !connected) message = vpnError
            vpnStats = VpnStatsSnapshot(
                trafficBytes = prefs.getLong(VpnStats.KEY_RX, 0L) + prefs.getLong(VpnStats.KEY_TX, 0L),
                rxSpeed = prefs.getLong(VpnStats.KEY_RX_SPEED, 0L),
                txSpeed = prefs.getLong(VpnStats.KEY_TX_SPEED, 0L),
                comment = prefs.getString(VpnStats.KEY_COMMENT, if (connected) "Подключено" else "Отключено").orEmpty()
            )
            delay(500)
        }
    }

    MaterialTheme {
        Surface(Modifier.fillMaxSize(), color = Color(0xFFF7F7F7)) {
            if (showImport) {
                ImportScreen(
                    url = subscriptionUrl,
                    onUrlChange = { subscriptionUrl = it },
                    busy = busy,
                    message = message,
                    onQr = { activity.scanQr() },
                    onClipboard = {
                        val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val text = clipboard.primaryClip?.getItemAt(0)?.coerceToText(activity)?.toString().orEmpty()
                        if (text.isNotBlank()) subscriptionUrl = text
                    },
                    onImport = {
                        busy = true
                        message = ""
                        scope.launch {
                            val result = importSubscription(activity, subscriptionUrl)
                            busy = false
                            result.onSuccess { list ->
                                val newSubscription = subscriptionUrl.trim()
                                val merged = (profiles + list).distinctBy { it.raw }
                                profiles = merged
                                selected = list.firstOrNull() ?: selected ?: merged.firstOrNull()
                                ProfileStore.addSubscription(activity, newSubscription, list, selected?.id)
                                subscriptionUrl = ""
                                showImport = false
                                message = "Добавлено серверов: ${list.size}. Старые подписки сохранены."
                            }.onFailure { message = it.message ?: "Не удалось импортировать подписку" }
                        }
                    }
                )
            } else {
                MainScreen(
                    profiles = profiles,
                    selected = selected,
                    connected = connected,
                    busy = busy,
                    message = message,
                    vpnStats = vpnStats,
                    onSelect = {
                        if (connected) activity.stopVpn()
                        selected = it
                        ProfileStore.select(activity, it)
                        connected = false
                    },
                    onConnect = {
                        val target = selected ?: profiles.firstOrNull()
                        if (connected) {
                            activity.stopVpn()
                        } else if (target != null) {
                            if (selected == null) selected = target
                            ProfileStore.select(activity, target)
                            activity.startVpn()
                        }
                    },
                    onRefresh = {
                        val source = ProfileStore.subscription(activity)
                        if (source.isBlank()) return@MainScreen
                        busy = true
                        scope.launch {
                            val result = importSubscription(activity, source)
                            busy = false
                            result.onSuccess { list ->
                                val refreshed = list
                                profiles = refreshed
                                selected = refreshed.firstOrNull { it.id == selected?.id } ?: refreshed.firstOrNull()
                                ProfileStore.save(activity, source, refreshed, selected?.id)
                                message = "Подписка обновлена: ${list.size} серверов"
                            }.onFailure { message = it.message ?: "Ошибка обновления" }
                        }
                    },
                    onImport = { subscriptionUrl = ""; showImport = true },
                    onFavorite = { profile ->
                        profiles = profiles.map { if (it.id == profile.id) it.copy(favorite = !it.favorite) else it }
                        ProfileStore.save(activity, ProfileStore.subscription(activity), profiles, selected?.id)
                    }
                )
            }
        }
    }
}

@Composable
private fun ImportScreen(
    url: String,
    onUrlChange: (String) -> Unit,
    busy: Boolean,
    message: String,
    onQr: () -> Unit,
    onClipboard: () -> Unit,
    onImport: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("LBD", fontSize = 56.sp, fontWeight = FontWeight.Black, letterSpacing = (-4).sp)
        Text("LABUDA", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(32.dp))
        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(20.dp)) {
                Text("Добавить подписку", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(value = url, onValueChange = onUrlChange, modifier = Modifier.fillMaxWidth(), label = { Text("URL подписки или VLESS") }, singleLine = true)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onQr, Modifier.weight(1f)) { Icon(Icons.Outlined.QrCodeScanner, null); Spacer(Modifier.size(6.dp)); Text("QR") }
                    Button(onClick = onClipboard, Modifier.weight(1f)) { Icon(Icons.Filled.ContentPaste, null); Spacer(Modifier.size(6.dp)); Text("Буфер") }
                }
                Spacer(Modifier.height(12.dp))
                Button(onClick = onImport, Modifier.fillMaxWidth(), enabled = !busy && url.isNotBlank()) { Text(if (busy) "Загрузка…" else "Импортировать всю подписку") }
                if (message.isNotBlank()) Text(message, Modifier.padding(top = 10.dp), color = Color.Gray)
            }
        }
    }
}

@Composable
private fun MainScreen(
    profiles: List<VlessProfile>, selected: VlessProfile?, connected: Boolean, busy: Boolean, message: String,
    vpnStats: VpnStatsSnapshot,
    onSelect: (VlessProfile) -> Unit, onConnect: () -> Unit, onRefresh: () -> Unit, onImport: () -> Unit, onFavorite: (VlessProfile) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("LABUDA", fontSize = 28.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
            IconButton(onClick = onRefresh, enabled = !busy) { Icon(Icons.Filled.Refresh, "Обновить") }
            IconButton(onClick = onImport) { Icon(Icons.Filled.Add, "Добавить") }
        }
        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(150.dp).background(if (connected) Color(0xFF111111) else Color(0xFFEAEAEA), CircleShape), contentAlignment = Alignment.Center) {
                    Text("LBD", fontSize = 42.sp, fontWeight = FontWeight.Black, color = if (connected) Color.White else Color.Black)
                }
                Spacer(Modifier.height(12.dp))
                Text(if (connected) "Лабуда подключена" else "Лабуда отключена", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(selected?.name ?: "Выберите сервер", color = Color.Gray)
                Spacer(Modifier.height(14.dp))
                Button(onClick = onConnect, Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), enabled = selected != null || profiles.isNotEmpty()) { Text(if (connected) "Отключить" else "Подключить", fontSize = 17.sp) }
            }
        }
        Spacer(Modifier.height(12.dp))
        VpnStatsCard(vpnStats)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Серверы (${profiles.size})", fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (busy) Text("Обновление…", color = Color.Gray)
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(profiles, key = { it.id }) { profile ->
                Card(Modifier.fillMaxWidth().clickable { onSelect(profile) }, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = if (selected?.id == profile.id) Color(0xFFEDEDED) else Color.White)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(profile.name, fontWeight = FontWeight.Bold)
                            Text("${profile.host}:${profile.port} • ${profile.network.uppercase()}", color = Color.Gray, fontSize = 13.sp)
                            profile.latencyMs?.let { Text("${it} ms", color = Color.Gray, fontSize = 12.sp) }
                        }
                        IconButton(onClick = { onFavorite(profile) }) { Icon(if (profile.favorite) Icons.Filled.Star else Icons.Filled.Speed, "Избранное") }
                    }
                }
            }
        }
        if (message.isNotBlank()) Text(message, Modifier.padding(vertical = 8.dp), color = Color.Gray)
    }
}

@Composable
private fun VpnStatsCard(stats: VpnStatsSnapshot) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(18.dp)) {
            Text("Статистика VPN", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text("Трафик: ${formatBytes(stats.trafficBytes)}", fontSize = 16.sp)
            Spacer(Modifier.height(5.dp))
            Text("Комментарий из VPN: ${stats.comment.ifBlank { "—" }}", color = Color.Gray)
            Spacer(Modifier.height(5.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Вход: ${formatSpeed(stats.rxSpeed)}", fontSize = 14.sp)
                Text("Выход: ${formatSpeed(stats.txSpeed)}", fontSize = 14.sp)
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes Б"
    if (bytes < 1024L * 1024L) return "%.1f КБ".format(bytes / 1024.0)
    if (bytes < 1024L * 1024L * 1024L) return "%.1f МБ".format(bytes / (1024.0 * 1024.0))
    return "%.2f ГБ".format(bytes / (1024.0 * 1024.0 * 1024.0))
}

private fun formatSpeed(bytesPerSecond: Long): String {
    if (bytesPerSecond < 0L) return "—"
    return "${formatBytes(bytesPerSecond)}/с"
}

private suspend fun importSubscription(context: Context, input: String): Result<List<VlessProfile>> = withContext(Dispatchers.IO) {
    runCatching {
        val source = input.trim()
        if (source.isBlank()) error("Пустой источник подписки")
        val content = if (source.startsWith("http://") || source.startsWith("https://")) {
            val connection = URL(source).openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.requestMethod = "GET"
            connection.inputStream.bufferedReader().use { it.readText() }.also { connection.disconnect() }
        } else source
        val profiles = VlessParser.parseSubscription(content)
        if (profiles.isEmpty()) error("В подписке не найдено корректных VLESS-конфигураций")
        profiles
    }
}

private fun ping(host: String, port: Int): Long? {
    val start = System.currentTimeMillis()
    return runCatching {
        Socket().use { socket -> socket.connect(InetSocketAddress(host, port), 2500) }
        System.currentTimeMillis() - start
    }.getOrNull()
}
