package com.labuda.app

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
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
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val PREFS = "labuda"
private const val KEY_SUB_URL = "subscription_url"
private const val KEY_PROFILES = "profiles"
private const val KEY_SELECTED_ID = "selected_profile_id"
private const val KEY_VPN_RUNNING = "vpn_running"
private const val KEY_VPN_ERROR = "vpn_error"
private const val KEY_DARK_THEME = "dark_theme"
private const val KEY_SUBSCRIPTIONS = "subscriptions_json"

data class VlessProfile(
    val id: String, val name: String, val uuid: String, val host: String, val port: Int,
    val security: String, val network: String, val type: String, val path: String, val sni: String,
    val fingerprint: String, val publicKey: String, val shortId: String, val raw: String,
    val latencyMs: Long? = null, val favorite: Boolean = false
)

data class SubscriptionInfo(
    val id: String, val url: String, val title: String, val comment: String = "",
    val totalBytes: Long? = null, val usedBytes: Long = 0L, val expireAt: Long? = null,
    val profiles: List<VlessProfile> = emptyList()
)

data class SubscriptionPayload(
    val profiles: List<VlessProfile>, val title: String, val comment: String,
    val totalBytes: Long?, val usedBytes: Long, val expireAt: Long?
)

data class VpnStatsSnapshot(
    val trafficBytes: Long = 0L, val rxBytes: Long = 0L, val txBytes: Long = 0L,
    val rxSpeed: Long = 0L, val txSpeed: Long = 0L, val comment: String = ""
)

class MainActivity : ComponentActivity() {
    private val vpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { if (it.resultCode == RESULT_OK) startVpnService() }
    private val qrImport = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()?.takeIf { s -> s.isNotBlank() }?.let { s -> qrResult = s }
        }
    }
    private var qrResult by mutableStateOf("")
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { LabudaApp(this) } }
    fun scanQr() { qrImport.launch(Intent(this, QrScannerActivity::class.java)) }
    fun consumeQrResult(): String = qrResult.also { qrResult = "" }
    fun startVpn() { val intent = VpnService.prepare(this); if (intent != null) vpnPermission.launch(intent) else startVpnService() }
    private fun startVpnService() { startService(Intent(this, LabudaVpnService::class.java).setAction(LabudaVpnService.ACTION_START)) }
    fun stopVpn() { startService(Intent(this, LabudaVpnService::class.java).setAction(LabudaVpnService.ACTION_STOP)) }
    fun switchVpn() { startService(Intent(this, LabudaVpnService::class.java).setAction(LabudaVpnService.ACTION_SWITCH)) }
}

object VlessParser {
    private val PATTERN = Regex("""vless://[^\s\"<>]+""", RegexOption.IGNORE_CASE)
    fun parseSubscription(input: String): List<VlessProfile> {
        val decoded = decode(input.trim())
        return PATTERN.findAll(decoded).map { it.value.trim().trimEnd(',', ';', '\r', '\n') }
            .mapNotNull { parseUri(it) }.distinctBy { it.raw }.mapIndexed { i, p -> p.copy(id = "${p.host}:${p.port}:$i") }.toList()
    }
    private fun decode(value: String): String {
        val v = value.replace("\\r", "\n").replace("\\n", "\n")
        if (v.contains("vless://", true)) return v
        val normalized = v.replace("\\s".toRegex(), "").replace('-', '+').replace('_', '/')
        return try { String(android.util.Base64.decode(normalized, android.util.Base64.DEFAULT), Charsets.UTF_8).takeIf { it.contains("vless://", true) } ?: v } catch (_: Exception) { v }
    }
    private fun parseUri(raw: String): VlessProfile? {
        return try {
            val uri = URI(raw)
            val user = uri.userInfo ?: return null
            val uuid = user.substringBefore(':')
            if (uuid.isBlank() || uri.host.isNullOrBlank()) return null
            val q = uri.rawQuery.orEmpty().split('&').mapNotNull { p -> p.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to URLDecoder.decode(it[1], "UTF-8") } }.toMap()
            VlessProfile(raw.hashCode().toString(), URLDecoder.decode(uri.fragment.orEmpty().ifBlank { uri.host }, "UTF-8"), uuid, uri.host!!,
                if (uri.port > 0) uri.port else 443, q["security"].orEmpty(), q["type"].orEmpty().ifBlank { q["network"].orEmpty().ifBlank { "tcp" } },
                q["type"].orEmpty().ifBlank { "tcp" }, q["path"].orEmpty(), q["sni"].orEmpty().ifBlank { q["host"].orEmpty() },
                q["fp"].orEmpty(), q["pbk"].orEmpty(), q["sid"].orEmpty(), raw)
        } catch (_: Exception) { null }
    }
}

object ProfileStore {
    fun saveAll(context: Context, profiles: List<VlessProfile>) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_PROFILES, profiles.joinToString("\n") { it.raw }).apply()
    fun profiles(context: Context): List<VlessProfile> = VlessParser.parseSubscription(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_PROFILES, "").orEmpty())
    fun selectedProfile(context: Context): VlessProfile? { val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE); val id = p.getString(KEY_SELECTED_ID, null); return profiles(context).firstOrNull { it.id == id } ?: profiles(context).firstOrNull() }
    fun select(context: Context, profile: VlessProfile) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_SELECTED_ID, profile.id).apply()
}

object SubscriptionStore {
    fun load(context: Context): List<SubscriptionInfo> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE); val raw = prefs.getString(KEY_SUBSCRIPTIONS, null)
        if (!raw.isNullOrBlank()) return runCatching { decode(JSONArray(raw)) }.getOrDefault(emptyList())
        val url = prefs.getString(KEY_SUB_URL, "").orEmpty(); val profiles = VlessParser.parseSubscription(prefs.getString(KEY_PROFILES, "").orEmpty())
        return if (profiles.isEmpty()) emptyList() else listOf(SubscriptionInfo(url.hashCode().toString(), url, "Подписка", profiles = profiles))
    }
    fun save(context: Context, list: List<SubscriptionInfo>) {
        val arr = JSONArray(); list.forEach { s ->
            val title = s.title.trim().takeIf { it.isNotBlank() } ?: "Подписка"
            val o = JSONObject().put("id", s.id).put("url", s.url).put("title", title).put("comment", s.comment)
                .put("total", s.totalBytes ?: -1L).put("used", s.usedBytes).put("expire", s.expireAt ?: -1L)
            val a = JSONArray(); s.profiles.forEach { a.put(it.raw) }; o.put("profiles", a); arr.put(o)
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_SUBSCRIPTIONS, arr.toString()).apply()
        ProfileStore.saveAll(context, list.flatMap { it.profiles })
    }
    private fun decode(arr: JSONArray): List<SubscriptionInfo> = buildList {
        for (i in 0 until arr.length()) { val o = arr.getJSONObject(i); val a = o.optJSONArray("profiles") ?: JSONArray(); val p = buildList { for (j in 0 until a.length()) VlessParser.parseSubscription(a.optString(j)).firstOrNull()?.let { add(it) } }
            add(SubscriptionInfo(o.optString("id"), o.optString("url"), o.optString("title").ifBlank { "Подписка" }, o.optString("comment"), o.optLong("total", -1).takeIf { it >= 0 }, o.optLong("used", 0), o.optLong("expire", -1).takeIf { it > 0 }, p)) }
    }
}

@Composable
private fun LabudaApp(activity: MainActivity) {
    val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    var dark by remember { mutableStateOf(prefs.getBoolean(KEY_DARK_THEME, false)) }
    var subs by remember { mutableStateOf(SubscriptionStore.load(activity)) }
    var selected by remember { mutableStateOf(ProfileStore.selectedProfile(activity)) }
    var importUrl by remember { mutableStateOf("") }; var showImport by remember { mutableStateOf(subs.isEmpty()) }
    var connected by remember { mutableStateOf(false) }; var busy by remember { mutableStateOf(false) }; var message by remember { mutableStateOf("") }
    var stats by remember { mutableStateOf(VpnStatsSnapshot()) }; val scope = rememberCoroutineScope()
    fun save() = SubscriptionStore.save(activity, subs)

    LaunchedEffect(Unit) {
        val qr = activity.consumeQrResult(); if (qr.isNotBlank()) { importUrl = qr; showImport = true }
        val updated = withContext(Dispatchers.IO) {
            subs.map { s -> s.copy(profiles = s.profiles.map { it.copy(latencyMs = ping(it.host, it.port)) }) }
        }
        subs = updated
        selected = selected?.let { old -> subs.flatMap { it.profiles }.firstOrNull { it.raw == old.raw } } ?: subs.flatMap { it.profiles }.firstOrNull(); save()
    }
    LaunchedEffect(Unit) { while (true) { connected = prefs.getBoolean(KEY_VPN_RUNNING, false); val rx=prefs.getLong(VpnStats.KEY_RX,0); val tx=prefs.getLong(VpnStats.KEY_TX,0); val storedComment=prefs.getString(VpnStats.KEY_COMMENT, "").orEmpty(); val selectedComment=subs.firstOrNull{s->s.profiles.any{it.raw==selected?.raw}}?.comment.orEmpty(); stats = VpnStatsSnapshot(rx + tx, rx, tx, prefs.getLong(VpnStats.KEY_RX_SPEED,0), prefs.getLong(VpnStats.KEY_TX_SPEED,0), storedComment.ifBlank{selectedComment}); delay(500) } }

    MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
        Surface(Modifier.fillMaxSize()) {
            if (showImport) ImportScreen(importUrl, { importUrl = it }, busy, message, { activity.scanQr() }, {
                val cm = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager; importUrl = cm.primaryClip?.getItemAt(0)?.coerceToText(activity)?.toString().orEmpty()
            }) {
                busy = true; message = ""; scope.launch {
                    importSubscription(activity, importUrl).onSuccessSuspend { p ->
                        val url = importUrl.trim()
                        if (subs.any { it.url.trim() == url }) { message = "Подписка уже добавлена" } else {
                            val title = cleanSubscriptionTitle(p.title, p.profiles.firstOrNull()?.name)
                            val s = SubscriptionInfo(url.hashCode().toString(), url, title, p.comment, p.totalBytes, p.usedBytes, p.expireAt,
                                p.profiles.map { it.copy(latencyMs = null) })
                            val pinged = withContext(Dispatchers.IO) { s.profiles.map { it.copy(latencyMs = ping(it.host, it.port)) } }
                            val ready = s.copy(profiles = pinged)
                            subs = subs + ready; selected = ready.profiles.firstOrNull(); selected?.let { ProfileStore.select(activity, it) }; save(); importUrl = ""; showImport = false; message = "Добавлено серверов: ${ready.profiles.size}"
                        }
                    }.onFailure { message = it.message ?: "Не удалось импортировать подписку" }; busy = false
                }
            } else MainScreen(subs, selected, connected, busy, message, stats, dark, { dark = it; prefs.edit().putBoolean(KEY_DARK_THEME,it).apply() },
                { activity.startActivity(Intent(activity, RoutingSettingsActivity::class.java)) },
                { p ->
                    selected = p
                    ProfileStore.select(activity,p)
                    if (connected) activity.switchVpn()
                },
                { if (connected) activity.stopVpn() else activity.startVpn() },
                {
                    busy=true; scope.launch { subs = subs.map { s -> importSubscription(activity,s.url).getOrNull()?.let { p -> s.copy(title=cleanSubscriptionTitle(p.title, p.profiles.firstOrNull()?.name),comment=p.comment.ifBlank{s.comment},totalBytes=p.totalBytes?:s.totalBytes,usedBytes=p.usedBytes,expireAt=p.expireAt?:s.expireAt,profiles=runBlocking(Dispatchers.IO){p.profiles.map{it.copy(latencyMs=ping(it.host,it.port))}}) } ?: s }; selected=selected?.let{x->subs.flatMap{it.profiles}.firstOrNull{it.raw==x.raw}}?:subs.flatMap{it.profiles}.firstOrNull(); save(); busy=false; message="Подписки обновлены" }
                }, { importUrl=""; showImport=true }, { p -> subs=subs.map{s->s.copy(profiles=s.profiles.map{if(it.raw==p.raw)it.copy(favorite=!it.favorite)else it})};save() })
        }
    }
}

private fun cleanSubscriptionTitle(title: String?, fallback: String?): String {
    val value = title.orEmpty().trim()
    if (value.isBlank()) return fallback.orEmpty().ifBlank { "Подписка" }
    val compact = value.replace("\\s".toRegex(), "")
    val looksBase64 = compact.length >= 8 && compact.matches(Regex("[A-Za-z0-9+/=_-]+"))
    if (looksBase64) {
        val decoded = runCatching {
            String(android.util.Base64.decode(compact.replace('-', '+').replace('_', '/'), android.util.Base64.DEFAULT), Charsets.UTF_8)
        }.getOrNull()?.trim()
        if (!decoded.isNullOrBlank() && decoded.any { it.isLetterOrDigit() } && !decoded.contains("vless://", true)) {
            return decoded
        }
    }
    return value
}

private suspend inline fun <T> Result<T>.onSuccessSuspend(crossinline action: suspend (T) -> Unit): Result<T> {
    if (isSuccess) action(getOrThrow())
    return this
}

@Composable
private fun ImportScreen(url:String,onUrl:(String)->Unit,busy:Boolean,message:String,onQr:()->Unit,onClipboard:()->Unit,onImport:()->Unit) { /* existing implementation */ }

private fun ping(host: String, port: Int): Long? = try {
    val socket = Socket(); val started = System.currentTimeMillis(); socket.connect(InetSocketAddress(host, port), 3000); val result = System.currentTimeMillis() - started; socket.close(); result
} catch (_: Exception) { null }
