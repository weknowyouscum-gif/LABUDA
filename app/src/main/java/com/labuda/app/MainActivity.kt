package com.labuda.app

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.net.Uri
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

private const val PREFS = "labuda"
private const val KEY_SUB_URL = "subscription_url"
private const val KEY_PROFILES = "profiles"
private const val KEY_SELECTED_ID = "selected_profile_id"
private const val KEY_VPN_RUNNING = "vpn_running"
private const val KEY_DARK_THEME = "dark_theme"
private const val KEY_SUBSCRIPTIONS = "subscriptions_json"
private const val TELEGRAM_SUPPORT_URL = "https://t.me/LABUDASUPPORT"

data class VlessProfile(val id:String,val name:String,val uuid:String,val host:String,val port:Int,val security:String,val network:String,val type:String,val path:String,val sni:String,val fingerprint:String,val publicKey:String,val shortId:String,val raw:String,val latencyMs:Long?=null,val favorite:Boolean=false)
data class SubscriptionInfo(val id:String,val url:String,val title:String,val comment:String="",val totalBytes:Long?=null,val usedBytes:Long=0L,val expireAt:Long?=null,val profiles:List<VlessProfile> = emptyList())
data class SubscriptionPayload(val profiles:List<VlessProfile>,val title:String,val comment:String,val totalBytes:Long?,val usedBytes:Long,val expireAt:Long?)
data class VpnStatsSnapshot(val trafficBytes:Long=0L,val rxBytes:Long=0L,val txBytes:Long=0L,val rxSpeed:Long=0L,val txSpeed:Long=0L,val comment:String="")

class MainActivity:ComponentActivity(){
 private val vpnPermission=registerForActivityResult(ActivityResultContracts.StartActivityForResult()){if(it.resultCode==RESULT_OK)startVpnService()}
 private val qrImport=registerForActivityResult(ActivityResultContracts.StartActivityForResult()){if(it.resultCode==RESULT_OK){val clipboard=getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager;clipboard.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()?.takeIf{s->s.isNotBlank()}?.let{s->qrResult=s}}}
 private var qrResult by mutableStateOf("")
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContent{LabudaApp(this)}}
 fun scanQr(){qrImport.launch(Intent(this,QrScannerActivity::class.java))};fun consumeQrResult():String=qrResult.also{qrResult=""};fun startVpn(){val intent=VpnService.prepare(this);if(intent!=null)vpnPermission.launch(intent)else startVpnService()};private fun startVpnService(){startService(Intent(this,LabudaVpnService::class.java).setAction(LabudaVpnService.ACTION_START))};fun stopVpn(){startService(Intent(this,LabudaVpnService::class.java).setAction(LabudaVpnService.ACTION_STOP))};fun switchVpn(){startService(Intent(this,LabudaVpnService::class.java).setAction(LabudaVpnService.ACTION_SWITCH))}
}

object VlessParser{
 private val PATTERN=Regex("""vless://[^\s\"<>]+""",RegexOption.IGNORE_CASE)
 fun parseSubscription(input:String):List<VlessProfile>{val decoded=decode(input.trim());return PATTERN.findAll(decoded).map{it.value.trim().trimEnd(',', ';','\r','\n')}.mapNotNull{parseUri(it)}.distinctBy{it.raw}.mapIndexed{i,p->p.copy(id="${p.host}:${p.port}:$i")}.toList()}
 private fun decode(value:String):String{val v=value.replace("\\r","\n").replace("\\n","\n");if(v.contains("vless://",true))return v;val normalized=v.replace("\\s".toRegex(),"").replace('-','+').replace('_','/');return try{String(android.util.Base64.decode(normalized,android.util.Base64.DEFAULT),Charsets.UTF_8).takeIf{it.contains("vless://",true)}?:v}catch(_:Exception){v}}
 private fun parseUri(raw:String):VlessProfile?{return try{val uri=URI(raw);val user=uri.userInfo?:return null;val uuid=user.substringBefore(':');if(uuid.isBlank()||uri.host.isNullOrBlank())return null;val q=uri.rawQuery.orEmpty().split('&').mapNotNull{p->p.split('=',limit=2).takeIf{it.size==2}?.let{it[0] to URLDecoder.decode(it[1],"UTF-8")}}.toMap();VlessProfile(raw.hashCode().toString(),URLDecoder.decode(uri.fragment.orEmpty().ifBlank{uri.host},"UTF-8"),uuid,uri.host!!,if(uri.port>0)uri.port else 443,q["security"].orEmpty(),q["type"].orEmpty().ifBlank{q["network"].orEmpty().ifBlank{"tcp"}},q["type"].orEmpty().ifBlank{"tcp"},q["path"].orEmpty(),q["sni"].orEmpty().ifBlank{q["host"].orEmpty()},q["fp"].orEmpty(),q["pbk"].orEmpty(),q["sid"].orEmpty(),raw)}catch(_:Exception){null}}
}

object ProfileStore{fun saveAll(context:Context,profiles:List<VlessProfile>)=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString(KEY_PROFILES,profiles.joinToString("\n"){it.raw}).apply();fun profiles(context:Context):List<VlessProfile> = VlessParser.parseSubscription(context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString(KEY_PROFILES,"").orEmpty());fun selectedProfile(context:Context):VlessProfile?{val p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);val id=p.getString(KEY_SELECTED_ID,null);return profiles(context).firstOrNull{it.id==id}?:profiles(context).firstOrNull()};fun select(context:Context,profile:VlessProfile)=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString(KEY_SELECTED_ID,profile.id).apply()}

object SubscriptionStore{
 fun load(context:Context):List<SubscriptionInfo>{val prefs=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);val raw=prefs.getString(KEY_SUBSCRIPTIONS,null);if(!raw.isNullOrBlank())return runCatching{decode(JSONArray(raw))}.getOrDefault(emptyList());val url=prefs.getString(KEY_SUB_URL,"").orEmpty();val profiles=VlessParser.parseSubscription(prefs.getString(KEY_PROFILES,"").orEmpty());return if(profiles.isEmpty())emptyList()else listOf(SubscriptionInfo(url.hashCode().toString(),url,"Подписка",profiles=profiles))}
 fun save(context:Context,list:List<SubscriptionInfo>){val arr=JSONArray();list.forEach{s->val title=s.title.trim().takeIf{it.isNotBlank()}?:"Подписка";val o=JSONObject().put("id",s.id).put("url",s.url).put("title",title).put("comment",s.comment).put("total",s.totalBytes?:-1L).put("used",s.usedBytes).put("expire",s.expireAt?:-1L);val a=JSONArray();s.profiles.forEach{a.put(it.raw)};o.put("profiles",a);arr.put(o)};context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString(KEY_SUBSCRIPTIONS,arr.toString()).apply();ProfileStore.saveAll(context,list.flatMap{it.profiles})}
 private fun decode(arr:JSONArray):List<SubscriptionInfo> = buildList{for(i in 0 until arr.length()){val o=arr.getJSONObject(i);val a=o.optJSONArray("profiles")?:JSONArray();val p=buildList{for(j in 0 until a.length())VlessParser.parseSubscription(a.optString(j)).firstOrNull()?.let{add(it)}};add(SubscriptionInfo(o.optString("id"),o.optString("url"),o.optString("title").ifBlank{"Подписка"},o.optString("comment"),o.optLong("total",-1).takeIf{it>=0},o.optLong("used",0),o.optLong("expire",-1).takeIf{it>0},p))}}
}

@Composable private fun LabudaApp(activity:MainActivity){val prefs=activity.getSharedPreferences(PREFS,Context.MODE_PRIVATE);var dark by remember{mutableStateOf(prefs.getBoolean(KEY_DARK_THEME,false))};var subs by remember{mutableStateOf(SubscriptionStore.load(activity))};var selected by remember{mutableStateOf(ProfileStore.selectedProfile(activity))};var importUrl by remember{mutableStateOf("")};var showImport by remember{mutableStateOf(subs.isEmpty())};var connected by remember{mutableStateOf(false)};var busy by remember{mutableStateOf(false)};var message by remember{mutableStateOf("")};var stats by remember{mutableStateOf(VpnStatsSnapshot())};val scope=rememberCoroutineScope();fun save()=SubscriptionStore.save(activity,subs);LaunchedEffect(Unit){val qr=activity.consumeQrResult();if(qr.isNotBlank()){importUrl=qr;showImport=true};val updated=withContext(Dispatchers.IO){subs.map{s->s.copy(profiles=s.profiles.map{it.copy(latencyMs=ping(it.host,it.port))})}};subs=updated;selected=selected?.let{old->subs.flatMap{it.profiles}.firstOrNull{it.raw==old.raw}}?:subs.flatMap{it.profiles}.firstOrNull();save()};LaunchedEffect(Unit){while(true){connected=prefs.getBoolean(KEY_VPN_RUNNING,false);val rx=prefs.getLong(VpnStats.KEY_RX,0);val tx=prefs.getLong(VpnStats.KEY_TX,0);val storedComment=prefs.getString(VpnStats.KEY_COMMENT,"").orEmpty();val selectedComment=subs.firstOrNull{s->s.profiles.any{it.raw==selected?.raw}}?.comment.orEmpty();stats=VpnStatsSnapshot(rx+tx,rx,tx,prefs.getLong(VpnStats.KEY_RX_SPEED,0),prefs.getLong(VpnStats.KEY_TX_SPEED,0),storedComment.ifBlank{selectedComment});delay(500)}};MaterialTheme(colorScheme=if(dark)darkColorScheme()else lightColorScheme()){Surface(Modifier.fillMaxSize()){if(showImport)ImportScreen(importUrl,{importUrl=it},busy,message,{activity.scanQr()},{val cm=activity.getSystemService(Context.CLIPBOARD_SERVICE)as ClipboardManager;importUrl=cm.primaryClip?.getItemAt(0)?.coerceToText(activity)?.toString().orEmpty()}){busy=true;message="";scope.launch{importSubscription(activity,importUrl).onSuccessSuspend{p->val url=importUrl.trim();if(subs.any{it.url.trim()==url})message="Подписка уже добавлена"else{val title=normalizeSubscriptionTitle(p.title);val s=SubscriptionInfo(url.hashCode().toString(),url,title,p.comment,p.totalBytes,p.usedBytes,p.expireAt,p.profiles.map{it.copy(name=cleanServerName(it.name,title),latencyMs=null)});val pinged=withContext(Dispatchers.IO){s.profiles.map{it.copy(latencyMs=ping(it.host,it.port))}};val ready=s.copy(profiles=pinged);subs=subs+ready;selected=ready.profiles.firstOrNull();selected?.let{ProfileStore.select(activity,it)};save();importUrl="";showImport=false;message="Добавлено серверов: ${ready.profiles.size}"}}.onFailure{message=it.message?:"Не удалось импортировать подписку"};busy=false}}}else MainScreen(subs,selected,connected,busy,message,stats,dark,{dark=it;prefs.edit().putBoolean(KEY_DARK_THEME,it).apply()},{activity.startActivity(Intent(activity,RoutingSettingsActivity::class.java))},{p->selected=p;ProfileStore.select(activity,p);if(connected)activity.switchVpn()},{if(connected)activity.stopVpn()else activity.startVpn()},{busy=true;scope.launch{subs=subs.map{s->importSubscription(activity,s.url).getOrNull()?.let{p->val title=normalizeSubscriptionTitle(p.title).ifBlank{s.title};s.copy(title=title,comment=p.comment.ifBlank{s.comment},totalBytes=p.totalBytes?:s.totalBytes,usedBytes=p.usedBytes,expireAt=p.expireAt?:s.expireAt,profiles=runBlocking(Dispatchers.IO){p.profiles.map{it.copy(name=cleanServerName(it.name,title),latencyMs=ping(it.host,it.port))}})}?:s};selected=selected?.let{x->subs.flatMap{it.profiles}.firstOrNull{it.raw==x.raw}}?:subs.flatMap{it.profiles}.firstOrNull();save();busy=false;message="Подписки обновлены"}},{importUrl="";showImport=true},{p->subs=subs.map{s->s.copy(profiles=s.profiles.map{if(it.raw==p.raw)it.copy(favorite=!it.favorite)else it})};save()})}}}}

private fun normalizeSubscriptionTitle(title:String?):String{var value=title.orEmpty().trim();if(value.isBlank())return "Подписка";value=value.replace(Regex("^base64:",RegexOption.IGNORE_CASE),"").trim();return if(value.isBlank())"Подписка" else value}
private fun cleanServerName(name:String,subscriptionTitle:String):String{var value=name.trim();if(value.isBlank())return "Сервер";val title=subscriptionTitle.trim();if(title.isNotBlank()&&!title.equals("Подписка",true)){value=value.replace(Regex("^${Regex.escape(title)}\\s*[-|:/•·]+\\s*",RegexOption.IGNORE_CASE),"").replace(Regex("\\s*[-|:/•·]+\\s*${Regex.escape(title)}$",RegexOption.IGNORE_CASE),"")};value=value.replace(Regex("^base64:",RegexOption.IGNORE_CASE),"").trim();return value.ifBlank{"Сервер"}}
private suspend inline fun <T> Result<T>.onSuccessSuspend(crossinline action: suspend (T) -> Unit):Result<T>{if(isSuccess)action(getOrThrow());return this}
@Composable private fun ImportScreen(url:String,onUrl:(String)->Unit,busy:Boolean,message:String,onQr:()->Unit,onClipboard:()->Unit,onImport:()->Unit){Column(Modifier.fillMaxSize().padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){Text("LBD",fontSize=56.sp,fontWeight=FontWeight.Black);Text("LABUDA",fontSize=18.sp,fontWeight=FontWeight.Bold);Spacer(Modifier.height(32.dp));Card(shape=RoundedCornerShape(24.dp)){Column(Modifier.padding(20.dp)){Text("Добавить подписку",fontSize=20.sp,fontWeight=FontWeight.Bold);Spacer(Modifier.height(12.dp));OutlinedTextField(url,onUrl,Modifier.fillMaxWidth(),label={Text("URL подписки или VLESS")},singleLine=true);Spacer(Modifier.height(12.dp));Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onQr,Modifier.weight(1f)){Text("QR")};Button(onClipboard,Modifier.weight(1f)){Icon(Icons.Filled.ContentPaste,null);Spacer(Modifier.size(6.dp));Text("Буфер")}};Spacer(Modifier.height(12.dp));Button(onImport,Modifier.fillMaxWidth(),enabled=!busy&&url.isNotBlank()){Text(if(busy)"Загрузка…"else"Импортировать подписку")};if(message.isNotBlank())Text(message,Modifier.padding(top=10.dp),color=MaterialTheme.colorScheme.onSurfaceVariant)}}}}
@Composable private fun MainScreen(subs:List<SubscriptionInfo>,selected:VlessProfile?,connected:Boolean,busy:Boolean,message:String,stats:VpnStatsSnapshot,dark:Boolean,onDark:(Boolean)->Unit,onSettings:()->Unit,onSelect:(VlessProfile)->Unit,onConnect:()->Unit,onRefresh:()->Unit,onImport:()->Unit,onFavorite:(VlessProfile)->Unit){Column(Modifier.fillMaxSize().padding(horizontal=12.dp).padding(top=32.dp)){Row(Modifier.fillMaxWidth().padding(top=8.dp),verticalAlignment=Alignment.CenterVertically){Text("LABUDA",fontSize=24.sp,fontWeight=FontWeight.Black,modifier=Modifier.weight(1f));IconButton(onClick={activityLaunchTelegram()},modifier=Modifier.size(36.dp)){Text("✈",fontSize=20.sp)};Icon(Icons.Filled.DarkMode,"Тёмная тема",Modifier.size(18.dp));Switch(dark,onDark);IconButton(onSettings){Icon(Icons.Filled.Settings,"Настройки")};IconButton(onRefresh,enabled=!busy){Icon(Icons.Filled.Refresh,"Обновить")};IconButton(onImport){Icon(Icons.Filled.Add,"Добавить")}};Spacer(Modifier.height(5.dp));Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp)){Column(Modifier.padding(10.dp),horizontalAlignment=Alignment.CenterHorizontally){Box(Modifier.size(82.dp).background(MaterialTheme.colorScheme.onSurfaceVariant,CircleShape),contentAlignment=Alignment.Center){Text("LBD",fontSize=28.sp,fontWeight=FontWeight.Black,color=MaterialTheme.colorScheme.surface)};Spacer(Modifier.height(5.dp));Text(if(connected)"Лабуда подключена"else"Лабуда отключена",fontSize=17.sp,fontWeight=FontWeight.Bold);Spacer(Modifier.height(6.dp));Button(onConnect,Modifier.fillMaxWidth().height(42.dp),shape=RoundedCornerShape(14.dp),enabled=selected!=null||subs.any{it.profiles.isNotEmpty()}){Text(if(connected)"Отключить"else"Подключить",fontSize=15.sp)}}};Spacer(Modifier.height(5.dp));VpnStatsCard(stats);Spacer(Modifier.height(6.dp));Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("Подписки (${subs.size})",fontSize=17.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f));if(busy)Text("Обновление…",color=MaterialTheme.colorScheme.onSurfaceVariant)};Spacer(Modifier.height(4.dp));LazyColumn(verticalArrangement=Arrangement.spacedBy(7.dp)){subs.forEach{s->item(key="sub-${s.id}"){SubscriptionHeader(s)};items(s.profiles,key={"${s.id}:${it.raw}"}){p->ServerCard(p,selected,onSelect,onFavorite)}}};if(message.isNotBlank())Text(message,Modifier.padding(vertical=6.dp),color=MaterialTheme.colorScheme.onSurfaceVariant)}}
@Composable private fun SubscriptionHeader(s:SubscriptionInfo){Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp)){Column(Modifier.padding(horizontal=10.dp,vertical=7.dp)){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text(s.title,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f));Text(if(s.totalBytes==null)"∞"else"Осталось ${formatBytes((s.totalBytes-s.usedBytes).coerceAtLeast(0))}",fontSize=12.sp)};Text("Окончание: ${s.expireAt?.let{formatExpiry(it)}?:"Без срока"}",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant);if(s.comment.isNotBlank())Text(s.comment,fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1)}}}
@Composable private fun ServerCard(p:VlessProfile,selected:VlessProfile?,onSelect:(VlessProfile)->Unit,onFavorite:(VlessProfile)->Unit){Card(Modifier.fillMaxWidth().clickable{onSelect(p)},shape=RoundedCornerShape(14.dp),colors=CardDefaults.cardColors(containerColor=if(selected?.id==p.id)MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface)){Row(Modifier.fillMaxWidth().padding(horizontal=10.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically){Text(p.name,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f));Text(p.latencyMs?.let{"$it мс"}?:"—",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=12.sp)}}}
@Composable private fun VpnStatsCard(s:VpnStatsSnapshot){Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp)){Column(Modifier.padding(horizontal=12.dp,vertical=7.dp)){Text("Статистика",fontSize=15.sp,fontWeight=FontWeight.Bold);Text("Трафик: ${formatBytes(s.trafficBytes)}",fontSize=13.sp);if(s.comment.isNotBlank())Text("Комментарий: ${s.comment}",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=12.sp,maxLines=1);Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("Вход: ${formatBytes(s.rxBytes)}",fontSize=12.sp);Text("Выход: ${formatBytes(s.txBytes)}",fontSize=12.sp)}}}}
private suspend fun importSubscription(context:Context,input:String):Result<SubscriptionPayload> = withContext(Dispatchers.IO){runCatching{val source=input.trim();if(source.isBlank())error("Пустой источник подписки");var title="";var comment="";var total:Long?=null;var used=0L;var expire:Long?=null;val content=if(source.startsWith("http://")||source.startsWith("https://")){val c=URL(source).openConnection()as HttpURLConnection;c.connectTimeout=15000;c.readTimeout=20000;c.requestMethod="GET";val h=c.headerFields.entries.associate{(k,v)->(k?:"").lowercase() to v?.firstOrNull().orEmpty()};title=h["profile-title"].orEmpty().ifBlank{h["content-disposition"].orEmpty().substringAfter("filename=","").trim('"','\'')};comment=h["profile-comment"].orEmpty().ifBlank{h["profile-description"].orEmpty()};parseUserInfo(h["subscription-userinfo"].orEmpty())?.let{used=it.used;total=it.total;expire=it.expire};c.inputStream.bufferedReader().use{it.readText()}.also{c.disconnect()}}else source;val profiles=VlessParser.parseSubscription(content);if(profiles.isEmpty())error("В подписке не найдено корректных VLESS-конфигураций");SubscriptionPayload(profiles,title.ifBlank{"Подписка"},comment,total,used,expire)}}
private data class UserInfo(val used:Long,val total:Long?,val expire:Long?)
private fun parseUserInfo(v:String):UserInfo?{if(v.isBlank())return null;val m=v.split(';').mapNotNull{it.trim().split('=',limit=2).takeIf{x->x.size==2}?.let{x->x[0].lowercase() to x[1].toLongOrNull()}}.toMap();return UserInfo((m["upload"]?:0)+(m["download"]?:0),m["total"],m["expire"]?.takeIf{it>0}?.times(1000))}
private fun ping(host:String,port:Int):Long?{val t=System.currentTimeMillis();return runCatching{Socket().use{it.connect(InetSocketAddress(host,port),2500)};System.currentTimeMillis()-t}.getOrNull()}
private fun formatBytes(b:Long):String{if(b<1024)return "$b Б";if(b<1024*1024)return "%.1f КБ".format(b/1024.0);if(b<1024*1024*1024)return "%.1f МБ".format(b/1024.0/1024);return "%.1f ГБ".format(b/1024.0/1024/1024)}
private fun formatExpiry(ms:Long):String{val f=java.text.SimpleDateFormat("dd.MM.yyyy",java.util.Locale.getDefault());return f.format(java.util.Date(ms))}
private fun activityLaunchTelegram(){/* placeholder */}
