package com.labuda.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.IpPrefix
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

class LabudaVpnService : VpnService() {
    companion object {
        const val ACTION_START = "com.labuda.app.START"
        const val ACTION_STOP = "com.labuda.app.STOP"
        const val ACTION_SWITCH = "com.labuda.app.SWITCH"
        private const val CHANNEL = "labuda_vpn"
        private const val NOTIFICATION_ID = 101
        private const val PREFS = "labuda"
        private const val KEY_VPN_RUNNING = "vpn_running"
        private const val KEY_VPN_ERROR = "vpn_error"
        private const val KEY_BYPASS_PRIVATE = "routing_bypass_private"
        private const val KEY_ROUTING_MODE = "routing_mode"
        private const val KEY_ROUTING_APPS = "routing_apps"
        private const val MODE_ALL = "all"
        private const val MODE_BYPASS = "bypass"
        private const val MODE_TUNNEL = "tunnel"
        private const val TAG = "LABUDA-VPN"
    }
    private var tun: ParcelFileDescriptor? = null
    private var xray: XrayCoreBridge? = null
    private var currentProfile: VlessProfile? = null
    private var monitorThread: Thread? = null
    @Volatile private var stopping = false
    @Volatile private var operationThread: Thread? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopTunnel()
            ACTION_START -> startTunnelAsync()
            ACTION_SWITCH -> switchTunnelAsync()
        }
        return START_STICKY
    }

    private fun startTunnelAsync() {
        operationThread?.interrupt()
        operationThread = Thread({ startTunnel() }, "LABUDA-vpn-start").apply { isDaemon = true; start() }
    }

    private fun switchTunnelAsync() {
        operationThread?.interrupt()
        operationThread = Thread({ switchTunnel() }, "LABUDA-vpn-switch").apply { isDaemon = true; start() }
    }

    private fun startTunnel() {
        stopping=false; createChannel()
        try {
            val n=notification("Запуск LBD…")
            if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.Q) startForeground(NOTIFICATION_ID,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else { @Suppress("DEPRECATION") val ignored=startForeground(NOTIFICATION_ID,n) }
        } catch(e:Exception){fail("Не удалось запустить foreground LBD: ${e.message ?: e.javaClass.simpleName}");return}
        if(tun!=null&&xray?.isRunning()==true)return
        VpnStats.reset(this);setState(false,null)
        val profiles=ProfileStore.profiles(this)
        if(profiles.isEmpty()){fail("Нет VLESS-серверов");return}
        val measurements=profiles.map{it to probe(it)}
        val ordered=measurements.sortedBy{it.second?:Long.MAX_VALUE}.map{it.first}
        Log.i(TAG,"Server priority: ${measurements.sortedBy{it.second?:Long.MAX_VALUE}.joinToString{"${it.first.name}=${it.second?:-1}ms"}}")
        for(profile in ordered){if(stopping)return;val created=startTunnelForProfile(profile);if(created){currentProfile=profile;startHealthMonitor();return}}
        fail("Не удалось подключиться ни к одному серверу")
    }

    private fun switchTunnel() {
        if (stopping) return
        val target = ProfileStore.selectedProfile(this) ?: return
        if (currentProfile?.raw == target.raw && xray?.isRunning() == true) return
        val oldTun = tun
        val oldXray = xray
        val oldProfile = currentProfile
        monitorThread?.interrupt()
        monitorThread = null
        Log.i(TAG, "Seamless server switch: ${oldProfile?.name ?: "none"} -> ${target.name}")
        val created = createTunnel(target)
        if (created == null) {
            Log.e(TAG, "Server switch failed; keeping current VPN connection")
            if (oldXray?.isRunning() == true) startHealthMonitor()
            return
        }
        val newTun = created.first
        val newXray = created.second
        xray = newXray
        tun = newTun
        currentProfile = target
        setState(true, null)
        VpnStats.setComment(this, "Подключено • ${target.name}")
        VpnStats.startMonitor(this)
        updateNotification("LBD переключена • ${target.name}")
        runCatching { oldXray?.stop() }
        runCatching { oldTun?.close() }
        startHealthMonitor()
    }

    private fun startTunnelForProfile(profile:VlessProfile):Boolean {
        val created = createTunnel(profile) ?: return false
        tun = created.first
        xray = created.second
        setState(true,null)
        VpnStats.setComment(this,"Подключено • ${profile.name}")
        VpnStats.startMonitor(this)
        updateNotification("LBD подключена • ${profile.name}")
        return true
    }

    private fun createTunnel(profile: VlessProfile): Pair<ParcelFileDescriptor, XrayCoreBridge>? {
        val prefs=getSharedPreferences(PREFS,MODE_PRIVATE)
        val bypassPrivate=prefs.getBoolean(KEY_BYPASS_PRIVATE,true)
        val routingMode=prefs.getString(KEY_ROUTING_MODE,MODE_ALL).orEmpty().let{when(it){MODE_ALL->MODE_ALL;MODE_TUNNEL->MODE_TUNNEL;else->MODE_BYPASS}}
        val selectedApps=prefs.getStringSet(KEY_ROUTING_APPS,emptySet()).orEmpty()
        val builder=Builder().setSession("LABUDA LBD").setMtu(1500).setBlocking(false).addAddress("10.10.0.2",32).addAddress("fd10:10:10::2",128).addRoute("0.0.0.0",0).addRoute("::",0).addDnsServer("1.1.1.1").addDnsServer("8.8.8.8")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) builder.setMetered(false)
        excludeProxyEndpointRoutes(builder,profile.host);if(bypassPrivate)excludePrivateRoutes(builder)
        if(routingMode==MODE_TUNNEL) selectedApps.forEach{pkg->runCatching{builder.addAllowedApplication(pkg)}} else {runCatching{builder.addDisallowedApplication(packageName)};if(routingMode==MODE_BYPASS)selectedApps.forEach{pkg->runCatching{builder.addDisallowedApplication(pkg)}}}
        val newTun=try{builder.establish()}catch(e:Exception){Log.e(TAG,"TUN establish failed for ${profile.host}: ${e.message}");null}
        val descriptor=newTun?:return null
        val bridge=XrayCoreBridge(this)
        val result=bridge.start(XrayConfigBuilder.build(profile,bypassPrivate),descriptor.fd)
        if(result.isFailure||!bridge.isRunning()){Log.e(TAG,"Xray failed for ${profile.host}: ${result.exceptionOrNull()?.message?:"not running"}");bridge.stop();descriptor.close();return null}
        if(!waitForSystemVpn()){Log.e(TAG,"System VPN was not established for ${profile.host}");bridge.stop();descriptor.close();return null}
        return descriptor to bridge
    }

    private fun startHealthMonitor(){
        monitorThread?.interrupt();val profile=currentProfile?:return
        monitorThread=Thread{while(!stopping&&xray?.isRunning()==true){try{Thread.sleep(8000)}catch(_:InterruptedException){break};if(stopping||xray?.isRunning()!=true)break;if(!isReachable(profile)){Log.w(TAG,"Server ${profile.host}:${profile.port} failed health check; switching");val old=profile.raw;runCatching{xray?.stop()};xray=null;tun?.close();tun=null;currentProfile=null;setState(false,null);val candidates=ProfileStore.profiles(this).filter{it.raw!=old}.map{it to probe(it)}.sortedBy{it.second?:Long.MAX_VALUE}.map{it.first};for(candidate in candidates){if(stopping)return@Thread;if(startTunnelForProfile(candidate)){currentProfile=candidate;updateNotification("LBD переключена • ${candidate.name}");startHealthMonitor();return@Thread}};if(!stopping)fail("Основной сервер недоступен, резервных серверов нет");return@Thread}}}.apply{isDaemon=true;name="LABUDA-server-health";start()}
    }
    private fun isReachable(p:VlessProfile)=probe(p)!=null
    private fun probe(p:VlessProfile):Long?{val t=System.currentTimeMillis();return runCatching{Socket().use{s->protect(s);s.connect(InetSocketAddress(p.host,p.port),2500)};System.currentTimeMillis()-t}.getOrNull()}
    private fun excludePrivateRoutes(b:Builder){listOf("10.0.0.0/8","172.16.0.0/12","192.168.0.0/16","100.64.0.0/10","169.254.0.0/16","127.0.0.0/8","::1/128","fc00::/7","fe80::/10").forEach{cidr->runCatching{if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.TIRAMISU){val p=cidr.substringAfterLast('/').toInt();b.excludeRoute(IpPrefix(InetAddress.getByName(cidr.substringBefore('/')),p))}}}}
    private fun excludeProxyEndpointRoutes(b:Builder,host:String){if(Build.VERSION.SDK_INT<Build.VERSION_CODES.TIRAMISU)return;runCatching{InetAddress.getAllByName(host).forEach{a->b.excludeRoute(IpPrefix(a,a.address.size*8))}}}
    private fun waitForSystemVpn():Boolean{val c=getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager;repeat(20){if(c.allNetworks.any{c.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN)==true})return true;try{Thread.sleep(100)}catch(_:InterruptedException){return false}};return false}
    private fun fail(m:String){Log.e(TAG,m);setState(false,m);VpnStats.setComment(this,"Ошибка LBD • $m");updateNotification("Ошибка подключения: $m");cleanup(true)}
    private fun stopTunnel(){stopping=true;operationThread?.interrupt();operationThread=null;cleanup(false)}
    private fun cleanup(stopService:Boolean){monitorThread?.interrupt();monitorThread=null;runCatching{xray?.stop()};xray=null;tun?.close();tun=null;currentProfile=null;setState(false,if(stopService)vpnError()else null);VpnStats.setComment(this,if(stopService)"Ошибка LBD • ${vpnError().orEmpty()}"else"Отключено");stopForeground(STOP_FOREGROUND_REMOVE);if(stopService)stopSelf()}
    private fun setState(running:Boolean,error:String?){getSharedPreferences(PREFS,MODE_PRIVATE).edit().putBoolean(KEY_VPN_RUNNING,running).putString(KEY_VPN_ERROR,error).apply()}
    private fun vpnError():String?=getSharedPreferences(PREFS,MODE_PRIVATE).getString(KEY_VPN_ERROR,null)
    private fun createChannel(){if(Build.VERSION.SDK_INT>=26)getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL,"LABUDA LBD",NotificationManager.IMPORTANCE_LOW))}
    private fun notification(text:String):Notification=if(Build.VERSION.SDK_INT>=26)Notification.Builder(this,CHANNEL).setContentTitle("LABUDA").setContentText(text).setSmallIcon(R.drawable.ic_labuda).setOngoing(true).build()else{@Suppress("DEPRECATION") val n=Notification.Builder(this).setContentTitle("LABUDA").setContentText(text).setSmallIcon(R.drawable.ic_labuda).setOngoing(true).build();n}
    private fun updateNotification(text:String){getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID,notification(text))}
    override fun onRevoke(){cleanup(false);super.onRevoke()}
    override fun onDestroy(){stopping=true;operationThread?.interrupt();cleanup(false);super.onDestroy()}
}