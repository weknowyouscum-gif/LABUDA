from pathlib import Path

p=Path('app/src/main/java/com/labuda/app/MainActivity.kt')
s=p.read_text(encoding='utf-8')
lines=s.splitlines()
start=next(i for i,l in enumerate(lines) if l.startswith('@Composable private fun LabudaApp('))
end=start+1
while end < len(lines) and not lines[end].startswith('private fun normalizeSubscriptionTitle('):
    end += 1
new='''@Composable
private fun LabudaApp(activity:MainActivity){
    val prefs=activity.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
    var dark by remember{mutableStateOf(prefs.getBoolean(KEY_DARK_THEME,false))}
    var subs by remember{mutableStateOf(SubscriptionStore.load(activity))}
    var selected by remember{mutableStateOf(ProfileStore.selectedProfile(activity))}
    var importUrl by remember{mutableStateOf("")}
    var showImport by remember{mutableStateOf(subs.isEmpty())}
    var connected by remember{mutableStateOf(false)}
    var busy by remember{mutableStateOf(false)}
    var message by remember{mutableStateOf("")}
    var stats by remember{mutableStateOf(VpnStatsSnapshot())}
    val scope=rememberCoroutineScope()
    fun save(){SubscriptionStore.save(activity,subs)}
    LaunchedEffect(Unit){
        val qr=activity.consumeQrResult()
        if(qr.isNotBlank()){importUrl=qr;showImport=true}
        val updated=withContext(Dispatchers.IO){subs.map{s->s.copy(profiles=s.profiles.map{it.copy(latencyMs=ping(it.host,it.port))})}}
        subs=updated
        selected=selected?.let{old->subs.flatMap{it.profiles}.firstOrNull{it.raw==old.raw}}?:subs.flatMap{it.profiles}.firstOrNull()
        save()
    }
    LaunchedEffect(Unit){
        while(true){
            connected=prefs.getBoolean(KEY_VPN_RUNNING,false)
            val rx=prefs.getLong(VpnStats.KEY_RX,0)
            val tx=prefs.getLong(VpnStats.KEY_TX,0)
            val storedComment=prefs.getString(VpnStats.KEY_COMMENT,"").orEmpty()
            val selectedComment=subs.firstOrNull{s->s.profiles.any{it.raw==selected?.raw}}?.comment.orEmpty()
            stats=VpnStatsSnapshot(rx+tx,rx,tx,prefs.getLong(VpnStats.KEY_RX_SPEED,0),prefs.getLong(VpnStats.KEY_TX_SPEED,0),storedComment.ifBlank{selectedComment})
            delay(500)
        }
    }
    MaterialTheme(colorScheme=if(dark)darkColorScheme()else lightColorScheme()){
        Surface(Modifier.fillMaxSize()){
            if(showImport){
                ImportScreen(importUrl,{importUrl=it},busy,message,{activity.scanQr()},{
                    val cm=activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    importUrl=cm.primaryClip?.getItemAt(0)?.coerceToText(activity)?.toString().orEmpty()
                }){
                    busy=true;message=""
                    scope.launch{
                        val result=importSubscription(activity,importUrl)
                        result.onSuccess{p->
                            val url=importUrl.trim()
                            if(subs.any{it.url.trim()==url}){
                                message="Подписка уже добавлена"
                            }else{
                                val title=normalizeSubscriptionTitle(p.title)
                                val profiles=p.profiles.map{it.copy(name=cleanServerName(it.name,title),latencyMs=null)}
                                val pinged=withContext(Dispatchers.IO){profiles.map{it.copy(latencyMs=ping(it.host,it.port))}}
                                val ready=SubscriptionInfo(url.hashCode().toString(),url,title,p.comment,p.totalBytes,p.usedBytes,p.expireAt,pinged)
                                subs=subs+ready
                                selected=ready.profiles.firstOrNull()
                                selected?.let{ProfileStore.select(activity,it)}
                                save();importUrl="";showImport=false
                                message="Добавлено серверов: ${ready.profiles.size}"
                            }
                        }.onFailure{message=it.message?:"Не удалось импортировать подписку"}
                        busy=false
                    }
                }
            }else{
                MainScreen(subs,selected,connected,busy,message,stats,dark,
                    {dark=it;prefs.edit().putBoolean(KEY_DARK_THEME,it).apply()},
                    {activity.startActivity(Intent(activity,RoutingSettingsActivity::class.java))},
                    {p->selected=p;ProfileStore.select(activity,p);if(connected)activity.switchVpn()},
                    {if(connected)activity.stopVpn()else activity.startVpn()},
                    {
                        busy=true
                        scope.launch{
                            subs=subs.map{s->
                                val p=importSubscription(activity,s.url).getOrNull()
                                if(p==null)s else{
                                    val title=normalizeSubscriptionTitle(p.title).ifBlank{s.title}
                                    val profiles=p.profiles.map{it.copy(name=cleanServerName(it.name,title),latencyMs=ping(it.host,it.port))}
                                    s.copy(title=title,comment=p.comment.ifBlank{s.comment},totalBytes=p.totalBytes?:s.totalBytes,usedBytes=p.usedBytes,expireAt=p.expireAt?:s.expireAt,profiles=profiles)
                                }
                            }
                            selected=selected?.let{x->subs.flatMap{it.profiles}.firstOrNull{it.raw==x.raw}}?:subs.flatMap{it.profiles}.firstOrNull()
                            save();busy=false;message="Подписки обновлены"
                        }
                    },
                    {importUrl="";showImport=true},
                    {p->subs=subs.map{s->s.copy(profiles=s.profiles.map{if(it.raw==p.raw)it.copy(favorite=!it.favorite)else it})};save()}
                )
            }
        }
    }
}
'''.splitlines()
lines=lines[:start]+new+lines[end:]
s='\n'.join(lines)+'\n'
if 'private fun formatExpiry(' not in s:
    s += '\nprivate fun formatExpiry(ms:Long):String = java.text.SimpleDateFormat("dd.MM.yyyy",java.util.Locale.getDefault()).format(java.util.Date(ms))\n'
p.write_text(s,encoding='utf-8')

# LABUDA 1.0.0.0 UI/security changes
p=Path('app/src/main/java/com/labuda/app/MainActivity.kt')
s=p.read_text(encoding='utf-8')
# Remove the airplane from the top bar.
s=s.replace('IconButton(onClick={activityLaunchTelegram()},modifier=Modifier.size(36.dp)){Text("✈",fontSize=20.sp)};','')
# MainScreen owns the settings state so the gear opens a settings hub instead of routing directly.
s=s.replace('onDark:(Boolean)->Unit,onSettings:()->Unit,onSelect:', 'onDark:(Boolean)->Unit,onSelect:', 1)
s=s.replace('{dark=it;prefs.edit().putBoolean(KEY_DARK_THEME,it).apply()},{activity.startActivity(Intent(activity,RoutingSettingsActivity::class.java))},{p->', '{dark=it;prefs.edit().putBoolean(KEY_DARK_THEME,it).apply()},{p->', 1)
settings='''@Composable private fun SettingsScreen(onBack:()->Unit,onRouting:()->Unit,onHelp:()->Unit){
    Column(Modifier.fillMaxSize().padding(20.dp)){
        Text("Настройки",fontSize=26.sp,fontWeight=FontWeight.Black)
        Spacer(Modifier.height(20.dp))
        Button(onClick=onRouting,modifier=Modifier.fillMaxWidth()){Text("Маршрутизация")}
        Spacer(Modifier.height(12.dp))
        Button(onClick=onHelp,modifier=Modifier.fillMaxWidth()){Text("Помощь")}
        Spacer(Modifier.height(20.dp))
        Text("Telegram",fontSize=16.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.primary,modifier=Modifier.clickable(onClick=onHelp))
        Spacer(Modifier.height(24.dp))
        Button(onClick=onBack,modifier=Modifier.fillMaxWidth()){Text("Назад")}
    }
}
'''
if '@Composable private fun SettingsScreen(' not in s:
    s=s.replace('@Composable private fun MainScreen(', settings+'@Composable private fun MainScreen(', 1)
# Add local settings screen and replace the old top-level Column body with an in-place hub.
s=s.replace('{Column(Modifier.fillMaxSize().padding(horizontal=12.dp).padding(top=32.dp)){Row(', '{var showSettings by remember{mutableStateOf(false)};if(showSettings) SettingsScreen({showSettings=false},{activity.startActivity(Intent(activity,RoutingSettingsActivity::class.java))},{activity.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(TELEGRAM_SUPPORT_URL)))}) else Column(Modifier.fillMaxSize().padding(horizontal=12.dp).padding(top=32.dp)){Row(', 1)
# The gear now toggles the local settings hub.
s=s.replace('IconButton(onSettings){Icon(Icons.Filled.Settings,"Настройки")};','IconButton(onClick={showSettings=true}){Icon(Icons.Filled.Settings,"Настройки")};', 1)
# Remove the obsolete placeholder helper.
s=s.replace('\nprivate fun activityLaunchTelegram(){/* placeholder */}\n','\n')
p.write_text(s,encoding='utf-8')

# Disable the 3FA login screen: opening the security activity now goes straight to LABUDA.
sec=Path('app/src/main/java/com/labuda/app/SecurityActivity.kt')
ss=sec.read_text(encoding='utf-8')
ss=ss.replace('override fun onCreate(savedInstanceState: Bundle?) {\n        super.onCreate(savedInstanceState)\n        showSecurity()\n    }','override fun onCreate(savedInstanceState: Bundle?) {\n        super.onCreate(savedInstanceState)\n        openApp()\n    }',1)
sec.write_text(ss,encoding='utf-8')
print('applied LABUDA 1.0.0.0 changes')
