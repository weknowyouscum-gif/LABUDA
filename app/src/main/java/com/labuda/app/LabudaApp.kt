package com.labuda.app

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LabudaApp(activity: MainActivity) {
    val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    var dark by remember { mutableStateOf(true) }
    var themeId by remember { mutableStateOf(DesignStore.themeId(activity)) }
    var subs by remember { mutableStateOf(SubscriptionStore.load(activity)) }
    var selected by remember { mutableStateOf(ProfileStore.selectedProfile(activity)) }
    var importUrl by remember { mutableStateOf("") }
    var showImport by remember { mutableStateOf(subs.isEmpty()) }
    var showBuy by remember { mutableStateOf(false) }
    var connected by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var stats by remember { mutableStateOf(VpnStatsSnapshot()) }
    val scope = rememberCoroutineScope()
    fun save() = SubscriptionStore.save(activity, subs)
    fun openUrl(url: String) = activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    LaunchedEffect(Unit) { prefs.edit().putBoolean(KEY_DARK_THEME, dark).apply() }

    fun runImport(raw: String) {
        val url = raw.trim()
        if (url.isBlank() || busy) return
        importUrl = url
        busy = true
        message = ""
        scope.launch {
            importSubscription(activity, url).onSuccess { p ->
                if (subs.any { it.url.trim() == url }) {
                    message = "Подписка уже добавлена"
                } else {
                    val title = normalizeSubscriptionTitle(p.title)
                    val comment = formatSubscriptionComment(p.comment).ifBlank { sharedRemark(p.profiles) }
                    val ready = SubscriptionInfo(
                        url.hashCode().toString(), url, title, comment,
                        p.totalBytes, p.usedBytes, p.expireAt,
                        p.profiles.map { it.copy(name = cleanServerName(it.name, title, it.host, it.sni, comment)) }
                    )
                    val pinged = withContext(Dispatchers.IO) {
                        ready.profiles.map { it.copy(latencyMs = ping(it.host, it.port)) }
                    }
                    val finalSub = ready.copy(profiles = pinged)
                    subs = subs + finalSub
                    selected = finalSub.profiles.firstOrNull()
                    selected?.let { ProfileStore.select(activity, it) }
                    save()
                    importUrl = ""
                    showImport = false
                    message = "Добавлено серверов: ${finalSub.profiles.size}"
                }
            }.onFailure { message = it.message ?: "Не удалось импортировать подписку" }
            busy = false
        }
    }

    suspend fun refreshNow() {
        if (subs.isEmpty()) return
        val current = subs
        val updated = withContext(Dispatchers.IO) {
            current.map { s ->
                importSubscription(activity, s.url).getOrNull()?.let { p ->
                    val title = normalizeSubscriptionTitle(p.title).ifBlank { s.title }
                    val comment = formatSubscriptionComment(p.comment).ifBlank { sharedRemark(p.profiles) }.ifBlank { s.comment }
                    s.copy(
                        title = title,
                        comment = comment,
                        totalBytes = p.totalBytes,
                        usedBytes = p.usedBytes,
                        expireAt = p.expireAt,
                        profiles = p.profiles.map {
                            it.copy(name = cleanServerName(it.name, title, it.host, it.sni, comment), latencyMs = ping(it.host, it.port))
                        }
                    )
                } ?: s
            }
        }
        subs = updated
        selected = selected?.let { x -> updated.flatMap { it.profiles }.firstOrNull { it.raw == x.raw } }
            ?: updated.flatMap { it.profiles }.firstOrNull()
        save()
    }

    LaunchedEffect(activity.qrGeneration()) {
        val qr = activity.consumeQrResult()
        if (qr.isNotBlank()) {
            showImport = true
            runImport(qr)
        }
    }
    LaunchedEffect(Unit) {
        val updated = withContext(Dispatchers.IO) {
            subs.map { s -> s.copy(profiles = s.profiles.map { it.copy(latencyMs = ping(it.host, it.port)) }) }
        }
        subs = updated
        selected = selected?.let { old -> updated.flatMap { it.profiles }.firstOrNull { it.raw == old.raw } }
            ?: updated.flatMap { it.profiles }.firstOrNull()
        save()
    }
    LaunchedEffect(Unit) {
        while (true) {
            connected = prefs.getBoolean(KEY_VPN_RUNNING, false)
            themeId = DesignStore.themeId(activity)
            val rx = prefs.getLong(VpnStats.KEY_RX, 0)
            val tx = prefs.getLong(VpnStats.KEY_TX, 0)
            val err = prefs.getString("vpn_error", null)
            if (!connected && !err.isNullOrBlank()) message = err
            val selectedComment = subs.firstOrNull { s -> s.profiles.any { it.raw == selected?.raw } }?.comment.orEmpty()
            stats = VpnStatsSnapshot(rx + tx, rx, tx, prefs.getLong(VpnStats.KEY_RX_SPEED, 0), prefs.getLong(VpnStats.KEY_TX_SPEED, 0), selectedComment)
            delay(1000)
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(600_000L)
            if (subs.isNotEmpty() && !busy) {
                busy = true
                refreshNow()
                busy = false
                message = "Подписки обновлены"
            }
        }
    }

    LabudaTheme(dark = dark, themeId = themeId) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when {
                showBuy -> BuyScreen(onBack = { showBuy = false }, onOpen = { openUrl(it) })
                showImport -> ImportScreen(
                    url = importUrl,
                    onUrl = { importUrl = it },
                    busy = busy,
                    message = message,
                    onScanQr = { activity.scanQr() },
                    onPickQrImage = { activity.pickQrImage() },
                    onClipboard = {
                        val cm = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = cm.primaryClip?.getItemAt(0)?.coerceToText(activity)?.toString().orEmpty()
                        runImport(clip)
                    },
                    onBack = if (subs.isNotEmpty()) ({
                        showImport = false
                        importUrl = ""
                    }) else null,
                    onImport = { runImport(importUrl) },
                    onBuy = { showBuy = true }
                )
                else -> MainScreen(
                    subs = subs,
                    selected = selected,
                    connected = connected,
                    busy = busy,
                    message = message,
                    stats = stats,
                    dark = dark,
                    onDark = { dark = it; prefs.edit().putBoolean(KEY_DARK_THEME, it).apply() },
                    onSettings = { activity.startActivity(Intent(activity, SettingsActivity::class.java)) },
                    onSelect = { p ->
                        selected = p
                        ProfileStore.select(activity, p)
                        if (connected) activity.switchVpn()
                    },
                    onConnect = {
                        if (connected) activity.stopVpn() else {
                            message = "Подключение…"
                            activity.startVpn()
                        }
                    },
                    onRefresh = {
                        if (!busy) scope.launch {
                            busy = true
                            refreshNow()
                            busy = false
                            message = "Подписки обновлены"
                        }
                    },
                    onImport = { importUrl = ""; showImport = true },
                    onFavorite = { p ->
                        subs = subs.map { s -> s.copy(profiles = s.profiles.map { if (it.raw == p.raw) it.copy(favorite = !it.favorite) else it }) }
                        save()
                    }
                )
            }
        }
    }
}
