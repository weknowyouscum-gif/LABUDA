from pathlib import Path
import re

path = Path("app/src/main/java/com/labuda/app/MainActivity.kt")
s = path.read_text(encoding="utf-8")

# Kotlin parser fixes for the minified source.
s = s.replace("fun profiles(context:Context):List<VlessProfile>=", "fun profiles(context:Context):List<VlessProfile> =")
s = s.replace("private fun decode(arr:JSONArray):List<SubscriptionInfo>=", "private fun decode(arr:JSONArray):List<SubscriptionInfo> =")
s = s.replace("private suspend fun importSubscription(activity:Context,input:String):Result<SubscriptionPayload>=", "private suspend fun importSubscription(activity:Context,input:String):Result<SubscriptionPayload> =")
s = s.replace("private suspend inline fun<T>Result<T>.onSuccessSuspend(crossinline action:suspend(T)->Unit):Result<T>{", "private suspend inline fun <T> Result<T>.onSuccessSuspend(crossinline action: suspend (T) -> Unit): Result<T> {")

# The previous minified LabudaApp expression had a malformed nested coroutine/lambda chain.
# Replace that whole composable with ordinary Kotlin so the compiler can parse it reliably.
new_labuda = r'''@Composable
private fun LabudaApp(activity: MainActivity) {
    val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    var dark by remember { mutableStateOf(prefs.getBoolean(KEY_DARK_THEME, false)) }
    var subs by remember { mutableStateOf(SubscriptionStore.load(activity)) }
    var selected by remember { mutableStateOf(ProfileStore.selectedProfile(activity)) }
    var importUrl by remember { mutableStateOf("") }
    var showImport by remember { mutableStateOf(subs.isEmpty()) }
    var connected by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var stats by remember { mutableStateOf(VpnStatsSnapshot()) }
    val scope = rememberCoroutineScope()

    fun save() {
        SubscriptionStore.save(activity, subs)
    }

    LaunchedEffect(Unit) {
        val qr = activity.consumeQrResult()
        if (qr.isNotBlank()) {
            importUrl = qr
            showImport = true
        }

        val updated = withContext(Dispatchers.IO) {
            subs.map { subscription ->
                subscription.copy(
                    profiles = subscription.profiles.map { profile ->
                        profile.copy(latencyMs = ping(profile.host, profile.port))
                    }
                )
            }
        }
        subs = updated
        selected = selected?.let { old ->
            subs.flatMap { it.profiles }.firstOrNull { it.raw == old.raw }
        } ?: subs.flatMap { it.profiles }.firstOrNull()
        save()
    }

    LaunchedEffect(Unit) {
        while (true) {
            connected = prefs.getBoolean(KEY_VPN_RUNNING, false)
            val rx = prefs.getLong(VpnStats.KEY_RX, 0)
            val tx = prefs.getLong(VpnStats.KEY_TX, 0)
            val storedComment = prefs.getString(VpnStats.KEY_COMMENT, "").orEmpty()
            val selectedComment = subs.firstOrNull { subscription ->
                subscription.profiles.any { it.raw == selected?.raw }
            }?.comment.orEmpty()
            stats = VpnStatsSnapshot(
                trafficBytes = rx + tx,
                rxBytes = rx,
                txBytes = tx,
                rxSpeed = prefs.getLong(VpnStats.KEY_RX_SPEED, 0),
                txSpeed = prefs.getLong(VpnStats.KEY_TX_SPEED, 0),
                comment = storedComment.ifBlank { selectedComment }
            )
            delay(500)
        }
    }

    fun importOneSubscription() {
        busy = true
        message = ""
        scope.launch {
            importSubscription(activity, importUrl)
                .onSuccessSuspend { payload ->
                    val url = importUrl.trim()
                    if (subs.any { it.url.trim() == url }) {
                        message = "Подписка уже добавлена"
                    } else {
                        val title = cleanSubscriptionTitle(
                            payload.title,
                            payload.profiles.firstOrNull()?.name
                        )
                        val base = SubscriptionInfo(
                            id = url.hashCode().toString(),
                            url = url,
                            title = title,
                            comment = payload.comment,
                            totalBytes = payload.totalBytes,
                            usedBytes = payload.usedBytes,
                            expireAt = payload.expireAt,
                            profiles = payload.profiles.map { it.copy(latencyMs = null) }
                        )
                        val pinged = withContext(Dispatchers.IO) {
                            base.profiles.map { profile ->
                                profile.copy(latencyMs = ping(profile.host, profile.port))
                            }
                        }
                        val ready = base.copy(profiles = pinged)
                        subs = subs + ready
                        selected = ready.profiles.firstOrNull()
                        selected?.let { ProfileStore.select(activity, it) }
                        save()
                        importUrl = ""
                        showImport = false
                        message = "Добавлено серверов: ${ready.profiles.size}"
                    }
                }
                .onFailure { error ->
                    message = error.message ?: "Не удалось импортировать подписку"
                }
            busy = false
        }
    }

    fun refreshSubscriptions() {
        busy = true
        scope.launch {
            val refreshed = withContext(Dispatchers.IO) {
                subs.map { subscription ->
                    val payload = importSubscription(activity, subscription.url).getOrNull()
                    if (payload == null) {
                        subscription
                    } else {
                        val profiles = payload.profiles.map { profile ->
                            profile.copy(latencyMs = ping(profile.host, profile.port))
                        }
                        subscription.copy(
                            title = cleanSubscriptionTitle(
                                payload.title,
                                payload.profiles.firstOrNull()?.name
                            ),
                            comment = payload.comment.ifBlank { subscription.comment },
                            totalBytes = payload.totalBytes ?: subscription.totalBytes,
                            usedBytes = payload.usedBytes,
                            expireAt = payload.expireAt ?: subscription.expireAt,
                            profiles = profiles
                        )
                    }
                }
            }
            subs = refreshed
            selected = selected?.let { old ->
                subs.flatMap { it.profiles }.firstOrNull { it.raw == old.raw }
            } ?: subs.flatMap { it.profiles }.firstOrNull()
            selected?.let { ProfileStore.select(activity, it) }
            save()
            busy = false
            message = "Подписки обновлены"
        }
    }

    MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
        Surface(Modifier.fillMaxSize()) {
            if (showImport) {
                ImportScreen(
                    url = importUrl,
                    onUrl = { importUrl = it },
                    busy = busy,
                    message = message,
                    onQr = { activity.scanQr() },
                    onClipboard = {
                        val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        importUrl = clipboard.primaryClip?.getItemAt(0)?.coerceToText(activity)?.toString().orEmpty()
                    },
                    onImport = { importOneSubscription() }
                )
            } else {
                MainScreen(
                    subs = subs,
                    selected = selected,
                    connected = connected,
                    busy = busy,
                    message = message,
                    stats = stats,
                    dark = dark,
                    onDark = {
                        dark = it
                        prefs.edit().putBoolean(KEY_DARK_THEME, it).apply()
                    },
                    onSettings = {
                        activity.startActivity(Intent(activity, RoutingSettingsActivity::class.java))
                    },
                    onSelect = { profile ->
                        selected = profile
                        ProfileStore.select(activity, profile)
                        if (connected) activity.switchVpn()
                    },
                    onConnect = {
                        if (connected) activity.stopVpn() else activity.startVpn()
                    },
                    onRefresh = { refreshSubscriptions() },
                    onImport = {
                        importUrl = ""
                        showImport = true
                    },
                    onFavorite = { profile ->
                        subs = subs.map { subscription ->
                            subscription.copy(
                                profiles = subscription.profiles.map { current ->
                                    if (current.raw == profile.raw) current.copy(favorite = !current.favorite) else current
                                }
                            )
                        }
                        save()
                    }
                )
            }
        }
    }
}
'''

pattern = r'@Composable private fun LabudaApp\(activity:MainActivity\)\{.*?\n\nprivate fun cleanSubscriptionTitle'
replacement = new_labuda + '\nprivate fun cleanSubscriptionTitle'
updated, count = re.subn(pattern, replacement, s, count=1, flags=re.DOTALL)
if count != 1:
    raise SystemExit(f"LabudaApp replacement failed: matched {count} blocks")
s = updated

# Add the missing expiry formatter once. The UI passes epoch seconds.
if "private fun formatExpiry(" not in s:
    marker = 'private suspend inline fun <T> Result<T>.onSuccessSuspend'
    formatter = '''private fun formatExpiry(value: Long): String {\n    val millis = if (value < 100000000000L) value * 1000L else value\n    return java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(millis))\n}\n'''
    s = s.replace(marker, formatter + marker, 1)

path.write_text(s, encoding="utf-8")
