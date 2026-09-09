package com.labuda.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.Base64
import java.util.concurrent.Executors
import java.net.InetSocketAddress
import java.net.Socket

private const val PREFS = "labuda"
private const val KEY_SUB_URL = "subscription_url"
private const val KEY_PROFILES = "profiles"

class MainActivity : ComponentActivity() {
    private val vpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) startService(Intent(this, LabudaVpnService::class.java).setAction(LabudaVpnService.ACTION_START))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LabudaApp(this) }
    }

    fun startVpn() {
        val intent = VpnService.prepare(this)
        if (intent != null) vpnPermission.launch(intent) else {
            startService(Intent(this, LabudaVpnService::class.java).setAction(LabudaVpnService.ACTION_START))
        }
    }

    fun stopVpn() = startService(Intent(this, LabudaVpnService::class.java).setAction(LabudaVpnService.ACTION_STOP))
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
    fun parseSubscription(input: String): List<VlessProfile> {
        val decoded = decodeSubscription(input.trim())
        return decoded.lines()
            .map { it.trim() }
            .filter { it.startsWith("vless://", true) }
            .mapNotNull { parseUri(it) }
            .distinctBy { it.raw }
            .mapIndexed { index, p -> p.copy(id = "${p.host}:${p.port}:$index") }
    }

    private fun decodeSubscription(value: String): String {
        if (value.startsWith("vless://", true)) return value
        val normalized = value.replace("\\s".toRegex(), "").replace('-', '+').replace('_', '/')
        return try {
            String(android.util.Base64.decode(normalized, android.util.Base64.DEFAULT), Charsets.UTF_8)
        } catch (_: Exception) {
            value
        }
    }

    private fun parseUri(raw: String): VlessProfile? = try {
        val uri = URI(raw)
        val user = uri.userInfo ?: return null
        val uuid = user.substringBefore(':')
        if (uuid.isBlank() || uri.host.isNullOrBlank()) return null
        val q = uri.rawQuery.orEmpty().split('&').mapNotNull {
            val p = it.split('=', limit = 2); if (p.size == 2) p[0] to java.net.URLDecoder.decode(p[1], "UTF-8") else null
        }.toMap()
        VlessProfile(
            id = raw.hashCode().toString(),
            name = java.net.URLDecoder.decode(uri.fragment.orEmpty().ifBlank { uri.host }, "UTF-8"),
            uuid = uuid,
            host = uri.host!!,
            port = if (uri.port > 0) uri.port else 443,
            security = q["security"].orEmpty(),
            network = q["type"].orEmpty().ifBlank { q["network"].orEmpty().ifBlank { "tcp" } },
            type = q["type"].orEmpty().ifBlank { "tcp" },
            path = q["path"].orEmpty(),
            sni = q["sni"].orEmpty().ifBlank { q["host"].orEmpty() },
            fingerprint = q["fp"].orEmpty(),
            publicKey = q["pbk"].orEmpty(),
            shortId = q["sid"].orEmpty(),
            raw = raw
        )
    } catch (_: Exception) { null }
}

object ProfileStore {
    fun save(context: Context, subscription: String, profiles: List<VlessProfile>) {
        val encoded = profiles.joinToString("\n") { it.raw }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_SUB_URL, subscription)
            .putString(KEY_PROFILES, encoded)
            .apply()
    }
    fun subscription(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_SUB_URL, "").orEmpty()
    fun profiles(context: Context) = VlessParser.parseSubscription(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_PROFILES, "").orEmpty())
}

@androidx.compose.runtime.Composable
private fun LabudaApp(activity: MainActivity) {
    var profiles by remember { mutableStateOf(ProfileStore.profiles(activity)) }
    var subscriptionUrl by remember { mutableStateOf(ProfileStore.subscription(activity)) }
    var showImport by remember { mutableStateOf(profiles.isEmpty()) }
    var selected by remember { mutableStateOf<VlessProfile?>(null) }
    var connected by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        if (profiles.isNotEmpty()) {
            profiles = profiles.map { p -> p.copy(latencyMs = ping(p.host, p.port)) }
        }
    }

    MaterialTheme {
        Surface(Modifier.fillMaxSize(), color = Color(0xFFF7F7F7)) {
            if (showImport) {
                ImportScreen(
                    url = subscriptionUrl,
                    onUrlChange = { subscriptionUrl = it },
                    onImport = {
                        busy = true
                        message = ""
                        kotlinx.coroutines.GlobalScope.launch(Dispatchers.Main) {
                            val result = importSubscription(activity, subscriptionUrl)
                            busy = false
                            result.onSuccess { list ->
                                profiles = list
                                ProfileStore.save(activity, subscriptionUrl, list)
                                showImport = false
                                message = "Импортировано серверов: ${list.size}"
                            }.onFailure { message = it.message ?: "Не удалось импортировать подписку" }
                        }
                    },
                    onQr = { activity.startActivityForResult(Intent(activity, QrScannerActivity::class.java), 700) },
                    busy = busy,
                    message = message
                )
            } else {
                MainScreen(
                    profiles = profiles,
                    selected = selected,
                    connected = connected,
                    onSelect = { selected = it },
                    onConnect = {
                        if (connected) { activity.stopVpn(); connected = false }
                        else { activity.startVpn(); connected = true }
                    },
                    onRefresh = {
                        busy = true
                        kotlinx.coroutines.GlobalScope.launch(Dispatchers.Main) {
                            val result = importSubscription(activity, subscriptionUrl)
                            busy = false
                            result.onSuccess { list -> profiles = list; ProfileStore.save(activity, subscriptionUrl, list) }
                                .onFailure { message = it.message ?: "Ошибка обновления" }
                        }
                    },
                    onImport = { showImport = true },
                    onFavorite = { p -> profiles = profiles.map { if (it.id == p.id) it.copy(favorite = !it.favorite) else it }; ProfileStore.save(activity, subscriptionUrl, profiles) },
                    busy = busy,
                    message = message
                )
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun ImportScreen(url: String, onUrlChange: (String) -> Unit, onImport: () -> Unit, onQr: () -> Unit, busy: Boolean, message: String) {
    var clipboardText by remember { mutableStateOf("") }
    val context = androidx.compose.ui.platform.LocalContext.current
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("LBD", fontSize = 56.sp, fontWeight = FontWeight.Black, letterSpacing = (-4).sp)
        Text("LABUDA", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("VLESS subscription client", color = Color.Gray)
        Spacer(Modifier.height(32.dp))
        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(20.dp)) {
                Text("Добавить подписку", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(url, onUrlChange, Modifier.fillMaxWidth(), label = { Text("URL подписки или VLESS") }, singleLine = true)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onQr, Modifier.weight(1f)) { Icon(Icons.Outlined.QrCodeScanner, null); Spacer(Modifier.size(6.dp)); Text("QR") }
                    Button(onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboardText = cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                        if (clipboardText.isNotBlank()) onUrlChange(clipboardText)
                    }, Modifier.weight(1f)) { Icon(Icons.Filled.ContentPaste, null); Spacer(Modifier.size(6.dp)); Text("Буфер") }
                }
                Spacer(Modifier.height(12.dp))
                Button(onClick = onImport, Modifier.fillMaxWidth(), enabled = !busy && url.isNotBlank()) { Text(if (busy) "Загрузка…" else "Импортировать всю подписку") }
                if (message.isNotBlank()) Text(message, Modifier.padding(top = 10.dp), color = Color.Gray)
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun MainScreen(profiles: List<VlessProfile>, selected: VlessProfile?, connected: Boolean, onSelect: (VlessProfile) -> Unit, onConnect: () -> Unit, onRefresh: () -> Unit, onImport: () -> Unit, onFavorite: (VlessProfile) -> Unit, busy: Boolean, message: String) {
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("LABUDA", fontSize = 28.sp, fontWeight = FontWeight.Black); Text("Private • Fast • Simple", color = Color.Gray) }
            IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, "Обновить") }
            IconButton(onClick = onImport) { Icon(Icons.Filled.Add, "Добавить") }
            IconButton(onClick = {}) { Icon(Icons.Filled.Settings, "Настройки") }
        }
        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(150.dp).background(if (connected) Color(0xFF111111) else Color(0xFFEAEAEA), CircleShape), contentAlignment = Alignment.Center) {
                    Text(if (connected) "LBD" else "LBD", fontSize = 42.sp, fontWeight = FontWeight.Black, color = if (connected) Color.White else Color.Black)
                }
                Spacer(Modifier.height(12.dp))
                Text(if (connected) "Лабуда подключена" else "Лабуда отключена", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(selected?.name ?: "Выберите сервер", color = Color.Gray)
                Spacer(Modifier.height(14.dp))
                Button(onClick = onConnect, Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) { Text(if (connected) "Отключить" else "Подключить", fontSize = 17.sp) }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Серверы (${profiles.size})", fontSize = 19.sp, fontWeight = FontWeight.Bold, Modifier.weight(1f))
            if (busy) Text("Обновление…", color = Color.Gray)
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            items(profiles, key = { it.id }) { profile ->
                Card(Modifier.fillMaxWidth().clickable { onSelect(profile) }, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = if (selected?.id == profile.id) Color(0xFFE9E9E9) else Color.White)) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(profile.name, fontWeight = FontWeight.Bold)
                            Text("${profile.host}:${profile.port} • ${profile.security.ifBlank { "auto" }} • ${profile.network}", color = Color.Gray, fontSize = 12.sp)
                        }
                        Icon(Icons.Filled.Speed, null, tint = Color.Gray)
                        Text(profile.latencyMs?.let { " ${it} ms" } ?: " —", fontSize = 12.sp)
                        IconButton(onClick = { onFavorite(profile) }) { Icon(Icons.Filled.Star, null, tint = if (profile.favorite) Color.Black else Color.LightGray) }
                    }
                }
            }
        }
        if (message.isNotBlank()) Text(message, Modifier.padding(vertical = 8.dp), color = Color.Gray)
    }
}

private suspend fun importSubscription(context: Context, source: String): Result<List<VlessProfile>> = withContext(Dispatchers.IO) {
    try {
        val value = source.trim()
        val body = if (value.startsWith("http://") || value.startsWith("https://")) {
            val connection = URL(value).openConnection() as HttpURLConnection
            connection.connectTimeout = 12_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("User-Agent", "LABUDA/0.1")
            connection.inputStream.bufferedReader().use { it.readText() }
        } else value
        val list = VlessParser.parseSubscription(body)
        if (list.isEmpty()) error("Подписка загружена, но VLESS-профили не найдены")
        Result.success(list)
    } catch (e: Exception) { Result.failure(e) }
}

private fun ping(host: String, port: Int): Long? = try {
    val start = System.nanoTime()
    Socket().use { it.connect(InetSocketAddress(host, port), 2500) }
    (System.nanoTime() - start) / 1_000_000
} catch (_: Exception) { null }
