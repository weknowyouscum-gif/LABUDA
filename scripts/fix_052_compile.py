from pathlib import Path

# LABUDA 1.0.0.1 build-time compatibility and stabilization patch.
p = Path("app/src/main/java/com/labuda/app/MainActivity.kt")
s = p.read_text()

# Compose's platform serif is the closest built-in Android equivalent to the
# requested Times New Roman look without bundling a separate font file.
if "import androidx.compose.material3.Typography" not in s:
    s = s.replace("import androidx.compose.material3.Text\n", "import androidx.compose.material3.Text\nimport androidx.compose.material3.Typography\n")
if "import androidx.compose.ui.text.font.FontFamily" not in s:
    s = s.replace("import androidx.compose.ui.text.font.FontWeight\n", "import androidx.compose.ui.text.font.FontFamily\nimport androidx.compose.ui.text.font.FontWeight\n")

start = s.find('@Composable private fun LabudaApp')
end = s.find('\n\nprivate fun normalizeSubscriptionTitle', start)
if start < 0 or end < 0:
    raise SystemExit('LabudaApp block boundaries not found')

new_app = r'''@Composable
private fun LabudaApp(activity: MainActivity) {
    val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    var dark by remember { mutableStateOf(prefs.getBoolean(KEY_DARK_THEME, false)) }
    var subs by remember { mutableStateOf(SubscriptionStore.load(activity)) }
    var selected by remember { mutableStateOf(ProfileStore.selectedProfile(activity)) }
    var importUrl by remember { mutableStateOf("") }
    var showImport by remember { mutableStateOf(subs.isEmpty()) }
    var connected by remember { mutableStateOf(prefs.getBoolean(KEY_VPN_RUNNING, false)) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var stats by remember { mutableStateOf(VpnStatsSnapshot()) }
    val scope = rememberCoroutineScope()

    fun saveSubscriptions() = SubscriptionStore.save(activity, subs)

    LaunchedEffect(Unit) {
        val qr = activity.consumeQrResult()
        if (qr.isNotBlank()) {
            importUrl = qr
            showImport = true
        }
        if (subs.isNotEmpty()) {
            val updated = withContext(Dispatchers.IO) {
                subs.map { sub ->
                    sub.copy(profiles = sub.profiles.map { profile ->
                        profile.copy(latencyMs = ping(profile.host, profile.port))
                    })
                }
            }
            subs = updated
            selected = selected?.let { old ->
                updated.flatMap { it.profiles }.firstOrNull { it.raw == old.raw }
            } ?: updated.flatMap { it.profiles }.firstOrNull()
            saveSubscriptions()
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            connected = prefs.getBoolean(KEY_VPN_RUNNING, false)
            val rx = prefs.getLong(VpnStats.KEY_RX, 0L)
            val tx = prefs.getLong(VpnStats.KEY_TX, 0L)
            val selectedComment = subs.firstOrNull { sub ->
                sub.profiles.any { it.raw == selected?.raw }
            }?.comment.orEmpty()
            stats = VpnStatsSnapshot(
                trafficBytes = rx + tx,
                rxBytes = rx,
                txBytes = tx,
                rxSpeed = prefs.getLong(VpnStats.KEY_RX_SPEED, 0L),
                txSpeed = prefs.getLong(VpnStats.KEY_TX_SPEED, 0L),
                comment = selectedComment
            )
            delay(1000L)
        }
    }

    fun importOne(url: String) {
        val cleanUrl = url.trim()
        if (cleanUrl.isBlank() || busy) return
        busy = true
        message = ""
        scope.launch {
            val result = withContext(Dispatchers.IO) { importSubscription(activity, cleanUrl) }
            result.onSuccess { payload ->
                if (subs.any { it.url.trim() == cleanUrl }) {
                    message = "Подписка уже добавлена"
                } else {
                    val title = normalizeSubscriptionTitle(payload.title)
                    val ready = SubscriptionInfo(
                        cleanUrl.hashCode().toString(), cleanUrl, title,
                        payload.comment, payload.totalBytes, payload.usedBytes, payload.expireAt,
                        payload.profiles.map { it.copy(name = cleanServerName(it.name, title)) }
                    )
                    val pinged = withContext(Dispatchers.IO) {
                        ready.profiles.map { it.copy(latencyMs = ping(it.host, it.port)) }
                    }
                    val finalSub = ready.copy(profiles = pinged)
                    subs = subs + finalSub
                    selected = finalSub.profiles.firstOrNull()
                    selected?.let { ProfileStore.select(activity, it) }
                    saveSubscriptions()
                    importUrl = ""
                    showImport = false
                    message = "Добавлено серверов: ${finalSub.profiles.size}"
                }
            }.onFailure { error -> message = error.message ?: "Не удалось импортировать подписку" }
            busy = false
        }
    }

    fun refreshSubscriptions() {
        if (busy || subs.isEmpty()) return
        busy = true
        message = ""
        scope.launch {
            val refreshed = withContext(Dispatchers.IO) {
                subs.map { old ->
                    val payload = importSubscription(activity, old.url).getOrNull() ?: return@map old
                    val title = normalizeSubscriptionTitle(payload.title).ifBlank { old.title }
                    val pinged = payload.profiles.map { profile ->
                        profile.copy(name = cleanServerName(profile.name, title), latencyMs = ping(profile.host, profile.port))
                    }
                    old.copy(
                        title = title,
                        comment = payload.comment.ifBlank { old.comment },
                        totalBytes = payload.totalBytes ?: old.totalBytes,
                        usedBytes = payload.usedBytes,
                        expireAt = payload.expireAt ?: old.expireAt,
                        profiles = pinged
                    )
                }
            }
            subs = refreshed
            selected = selected?.let { old -> refreshed.flatMap { it.profiles }.firstOrNull { it.raw == old.raw } }
                ?: refreshed.flatMap { it.profiles }.firstOrNull()
            selected?.let { ProfileStore.select(activity, it) }
            saveSubscriptions()
            busy = false
            message = "Подписки обновлены"
        }
    }

    MaterialTheme(
        colorScheme = if (dark) darkColorScheme() else lightColorScheme(),
        typography = Typography(defaultFontFamily = FontFamily.Serif)
    ) {
        Surface(Modifier.fillMaxSize()) {
            if (showImport) {
                ImportScreen(
                    importUrl,
                    { importUrl = it },
                    busy,
                    message,
                    { activity.scanQr() },
                    {
                        val cm = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        importUrl = cm.primaryClip?.getItemAt(0)?.coerceToText(activity)?.toString().orEmpty()
                    },
                    { importOne(importUrl) }
                )
            } else {
                MainScreen(
                    subs, selected, connected, busy, message, stats, dark,
                    { value -> dark = value; prefs.edit().putBoolean(KEY_DARK_THEME, value).apply() },
                    { activity.startActivity(Intent(activity, SettingsActivity::class.java)) },
                    { profile -> selected = profile; ProfileStore.select(activity, profile); if (connected) activity.switchVpn() },
                    { if (connected) activity.stopVpn() else activity.startVpn() },
                    { refreshSubscriptions() },
                    { importUrl = ""; showImport = true },
                    { profile ->
                        subs = subs.map { sub -> sub.copy(profiles = sub.profiles.map { if (it.raw == profile.raw) it.copy(favorite = !it.favorite) else it }) }
                        saveSubscriptions()
                    }
                )
            }
        }
    }
}
'''

s = s[:start] + new_app + s[end:]
p.write_text(s)
print("LABUDA 1.0.0.1: stabilized Compose state flow and applied built-in serif typography")
