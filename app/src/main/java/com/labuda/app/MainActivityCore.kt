package com.labuda.app

import android.Manifest
import android.app.StatusBarManager
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder

class MainActivity : ComponentActivity() {
    private val vpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) startVpnService()
    }
    private val notifyPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        prepareVpn()
    }
    private val qrImport = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            val fromIntent = it.data?.dataString
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val fromClip = clipboard.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()
            val value = fromIntent?.takeIf { s -> s.isNotBlank() } ?: fromClip?.takeIf { s -> s.isNotBlank() }
            if (!value.isNullOrBlank()) acceptQr(value)
        }
    }
    private val qrImagePick = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) decodeQrFromUri(uri)
    }
    private var qrResult by mutableStateOf("")
    private var qrTick by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LabudaApp(this) }
        offerQuickTile()
    }

    private fun offerQuickTile() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        if (prefs.getBoolean("qs_tile_asked", false)) return
        prefs.edit().putBoolean("qs_tile_asked", true).apply()
        runCatching {
            getSystemService(StatusBarManager::class.java).requestAddTileService(
                ComponentName(this, LabudaTileService::class.java),
                "LABUDA",
                Icon.createWithResource(this, R.drawable.ic_stat_labuda),
                mainExecutor
            ) { }
        }
    }

    fun scanQr() { qrImport.launch(Intent(this, QrScannerActivity::class.java)) }
    fun pickQrImage() { qrImagePick.launch("image/*") }
    fun qrGeneration(): Int = qrTick
    fun consumeQrResult(): String = qrResult.also { qrResult = "" }
    private fun acceptQr(value: String) { qrResult = value.trim(); qrTick++ }
    private fun decodeQrFromUri(uri: Uri) {
        runCatching {
            BarcodeScanning.getClient().process(InputImage.fromFilePath(this, uri))
                .addOnSuccessListener { codes ->
                    codes.firstOrNull()?.rawValue?.takeIf { it.isNotBlank() }?.let { acceptQr(it) }
                }
        }
    }
    fun startVpn() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        prepareVpn()
    }
    private fun prepareVpn() {
        val intent = VpnService.prepare(this)
        if (intent != null) vpnPermission.launch(intent) else startVpnService()
    }
    private fun vpnCommand(action: String) {
        val intent = Intent(this, LabudaVpnService::class.java).setAction(action)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
    }
    private fun startVpnService() { vpnCommand(LabudaVpnService.ACTION_START) }
    fun stopVpn() { vpnCommand(LabudaVpnService.ACTION_STOP) }
    fun switchVpn() { vpnCommand(LabudaVpnService.ACTION_SWITCH) }
}

object VlessParser {
    private val PATTERN = Regex("""vless://[^\\s\"<>]+""", RegexOption.IGNORE_CASE)
    fun parseSubscription(input: String): List<VlessProfile> {
        val decoded = decode(input.trim())
        return PATTERN.findAll(decoded).map { it.value.trim().trimEnd(',', ';', '\r', '\n') }
            .mapNotNull { parseUri(it) }.distinctBy { it.raw }
            .mapIndexed { i, p -> p.copy(id = "${p.host}:${p.port}:$i") }.toList()
    }
    private fun decode(value: String): String {
        val v = value.replace("\\r", "\n").replace("\\n", "\n")
        if (v.contains("vless://", true)) return v
        val normalized = v.replace("\\s".toRegex(), "").replace('-', '+').replace('_', '/')
        return try {
            String(android.util.Base64.decode(normalized, android.util.Base64.DEFAULT), Charsets.UTF_8)
                .takeIf { it.contains("vless://", true) } ?: v
        } catch (_: Exception) { v }
    }
    private fun parseUri(raw: String): VlessProfile? {
        return try {
            val uri = URI(raw)
            val user = uri.userInfo ?: return null
            val uuid = user.substringBefore(':')
            if (uuid.isBlank() || uri.host.isNullOrBlank()) return null
            val q = uri.rawQuery.orEmpty().split('&').mapNotNull { p ->
                p.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to URLDecoder.decode(it[1], "UTF-8") }
            }.toMap()
            val sni = q["sni"].orEmpty().ifBlank { q["host"].orEmpty() }
            val remark = runCatching { URLDecoder.decode(uri.fragment.orEmpty(), "UTF-8") }.getOrDefault(uri.fragment.orEmpty())
            val label = runCatching { formatServerName(remark, "", uri.host.orEmpty(), sni) }.getOrDefault(remark.ifBlank { "Сервер" })
            VlessProfile(
                raw.hashCode().toString(),
                label,
                uuid, uri.host!!, if (uri.port > 0) uri.port else 443,
                q["security"].orEmpty(),
                q["type"].orEmpty().ifBlank { q["network"].orEmpty().ifBlank { "tcp" } },
                q["type"].orEmpty().ifBlank { "tcp" },
                q["path"].orEmpty(), sni,
                q["fp"].orEmpty(), q["pbk"].orEmpty(), q["sid"].orEmpty(), raw
            )
        } catch (_: Exception) {
            null
        }
    }
}

object ProfileStore {
    fun saveAll(context: Context, profiles: List<VlessProfile>) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_PROFILES, profiles.joinToString("\n") { it.raw }).apply()
    fun profiles(context: Context): List<VlessProfile> =
        VlessParser.parseSubscription(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_PROFILES, "").orEmpty())
    fun selectedProfile(context: Context): VlessProfile? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val id = p.getString(KEY_SELECTED_ID, null)
        return profiles(context).firstOrNull { it.id == id } ?: profiles(context).firstOrNull()
    }
    fun select(context: Context, profile: VlessProfile) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_SELECTED_ID, profile.id).apply()
}

object SubscriptionStore {
    fun load(context: Context): List<SubscriptionInfo> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_SUBSCRIPTIONS, null)
        if (!raw.isNullOrBlank()) return runCatching { decode(JSONArray(raw)) }.getOrDefault(emptyList())
        val url = prefs.getString(KEY_SUB_URL, "").orEmpty()
        val profiles = VlessParser.parseSubscription(prefs.getString(KEY_PROFILES, "").orEmpty())
        return if (profiles.isEmpty()) emptyList() else listOf(SubscriptionInfo(url.hashCode().toString(), url, "Подписка", profiles = profiles))
    }
    fun save(context: Context, list: List<SubscriptionInfo>) {
        val arr = JSONArray()
        list.forEach { s ->
            val title = s.title.trim().takeIf { it.isNotBlank() } ?: "Подписка"
            val o = JSONObject().put("id", s.id).put("url", s.url).put("title", title).put("comment", s.comment)
                .put("total", s.totalBytes ?: -1L).put("used", s.usedBytes).put("expire", s.expireAt ?: -1L)
            val a = JSONArray(); s.profiles.forEach { a.put(it.raw) }; o.put("profiles", a); arr.put(o)
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_SUBSCRIPTIONS, arr.toString()).apply()
        ProfileStore.saveAll(context, list.flatMap { it.profiles })
    }
    private fun decode(arr: JSONArray): List<SubscriptionInfo> = buildList {
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val a = o.optJSONArray("profiles") ?: JSONArray()
            val p = buildList {
                for (j in 0 until a.length()) VlessParser.parseSubscription(a.optString(j)).firstOrNull()?.let { add(it) }
            }
            add(
                SubscriptionInfo(
                    o.optString("id"), o.optString("url"), o.optString("title").ifBlank { "Подписка" },
                    o.optString("comment"), o.optLong("total", -1).takeIf { it > 0 }, o.optLong("used", 0),
                    o.optLong("expire", -1).takeIf { it > 0 }, p
                )
            )
        }
    }
}
