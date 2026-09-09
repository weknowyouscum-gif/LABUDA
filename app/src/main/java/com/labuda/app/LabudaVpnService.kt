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

class LabudaVpnService : VpnService() {
    companion object {
        const val ACTION_START = "com.labuda.app.START"
        const val ACTION_STOP = "com.labuda.app.STOP"
        private const val CHANNEL = "labuda_vpn"
        private const val NOTIFICATION_ID = 101
        private const val PREFS = "labuda"
        private const val KEY_VPN_RUNNING = "vpn_running"
        private const val KEY_VPN_ERROR = "vpn_error"
        private const val KEY_BYPASS_PRIVATE = "routing_bypass_private"
        private const val TAG = "LABUDA-VPN"
    }

    private var tun: ParcelFileDescriptor? = null
    private var xray: XrayCoreBridge? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopTunnel()
            ACTION_START -> startTunnel()
        }
        return START_STICKY
    }

    private fun startTunnel() {
        createChannel()
        try {
            val notification = notification("Запуск VPN…")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                @Suppress("DEPRECATION")
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            fail("Не удалось запустить foreground VPN: ${e.message ?: e.javaClass.simpleName}")
            return
        }

        if (tun != null && xray?.isRunning() == true) return

        setState(false, null)
        val profile = ProfileStore.selectedProfile(this) ?: ProfileStore.profiles(this).firstOrNull()
        if (profile == null) {
            fail("Нет выбранного VLESS-сервера")
            return
        }

        val bypassPrivate = getSharedPreferences(PREFS, MODE_PRIVATE)
            .getBoolean(KEY_BYPASS_PRIVATE, true)

        val builder = Builder()
            .setSession("LABUDA VPN")
            .setMtu(1500)
            .setBlocking(false)
            .setMetered(false)
            .addAddress("10.10.0.2", 32)
            .addAddress("fd10:10:10::2", 128)
            .addRoute("0.0.0.0", 0)
            .addRoute("::", 0)
            .addDnsServer("1.1.1.1")
            .addDnsServer("8.8.8.8")

        // The VLESS endpoint itself must never enter our TUN, otherwise the
        // Xray connection can loop back into LABUDA.
        excludeProxyEndpointRoutes(builder, profile.host)

        // Optional split routing: keep RFC1918/private and IPv6 local traffic on
        // the physical network. Public Internet remains inside the VPN.
        if (bypassPrivate) excludePrivateRoutes(builder)

        // LABUDA/Xray sockets must stay on the physical network instead of re-entering its own TUN.
        runCatching { builder.addDisallowedApplication(packageName) }

        tun = try {
            builder.establish()
        } catch (e: Exception) {
            fail("Ошибка создания Android VPN: ${e.message ?: e.javaClass.simpleName}")
            return
        }

        val descriptor = tun ?: run {
            fail("Android не выдал TUN-интерфейс")
            return
        }

        Log.i(TAG, "Android VPN established: fd=${descriptor.fd}; endpoint=${profile.host}:${profile.port}; bypassPrivate=$bypassPrivate")
        val bridge = XrayCoreBridge(this)
        val config = XrayConfigBuilder.build(profile, bypassPrivate)
        Log.i(TAG, "Starting Xray for ${profile.host}:${profile.port}")
        val result = bridge.start(config, descriptor.fd)
        if (result.isFailure || !bridge.isRunning()) {
            val error = result.exceptionOrNull()?.message ?: "Xray не запустился"
            bridge.stop()
            descriptor.close()
            tun = null
            fail(error)
            return
        }

        xray = bridge

        if (!waitForSystemVpn()) {
            bridge.stop()
            xray = null
            descriptor.close()
            tun = null
            fail("Android не зарегистрировал активную VPN-сеть")
            return
        }

        setUnderlyingNetworks(null)
        setState(true, null)
        updateNotification("VPN подключена • ${profile.name}")
        Log.i(TAG, "LABUDA VPN ACTIVE; tunFd=${descriptor.fd}; xrayRunning=${bridge.isRunning()}")
    }

    private fun excludePrivateRoutes(builder: Builder) {
        val routes = listOf(
            "10.0.0.0/8",
            "172.16.0.0/12",
            "192.168.0.0/16",
            "100.64.0.0/10",
            "169.254.0.0/16",
            "127.0.0.0/8",
            "::1/128",
            "fc00::/7",
            "fe80::/10"
        )
        routes.forEach { cidr ->
            runCatching {
                val prefix = cidr.substringAfterLast('/').toInt()
                val address = InetAddress.getByName(cidr.substringBefore('/'))
                builder.excludeRoute(IpPrefix(address, prefix))
                Log.i(TAG, "Excluded private route: $cidr")
            }.onFailure { error ->
                Log.w(TAG, "Could not exclude route $cidr: ${error.message}")
            }
        }
    }

    private fun excludeProxyEndpointRoutes(builder: Builder, host: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Log.i(TAG, "Route exclusion unavailable on Android < 13")
            return
        }

        runCatching {
            val addresses = InetAddress.getAllByName(host)
            if (addresses.isEmpty()) {
                Log.w(TAG, "No addresses resolved for VLESS endpoint $host")
                return@runCatching
            }
            addresses.forEach { address ->
                val prefixLength = address.address.size * 8
                builder.excludeRoute(IpPrefix(address, prefixLength))
                Log.i(TAG, "Excluded VLESS endpoint from VPN route: ${address.hostAddress}/$prefixLength")
            }
        }.onFailure {
            Log.w(TAG, "Could not resolve VLESS endpoint $host for route exclusion: ${it.message}")
        }
    }

    private fun waitForSystemVpn(): Boolean {
        val connectivity = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        repeat(20) { attempt ->
            val active = connectivity.allNetworks.any { network ->
                connectivity.getNetworkCapabilities(network)
                    ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
            }
            if (active) {
                Log.i(TAG, "Android reports TRANSPORT_VPN after ${attempt * 100}ms")
                return true
            }
            try {
                Thread.sleep(100)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return false
            }
        }
        return false
    }

    private fun fail(message: String) {
        Log.e(TAG, message)
        setState(false, message)
        updateNotification("Ошибка подключения: $message")
        stopTunnel(keepError = true)
    }

    private fun stopTunnel(keepError: Boolean = false) {
        runCatching { xray?.stop() }
        xray = null
        tun?.close()
        tun = null
        setState(false, if (keepError) vpnError() else null)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun setState(running: Boolean, error: String?) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putBoolean(KEY_VPN_RUNNING, running)
            .putString(KEY_VPN_ERROR, error)
            .apply()
    }

    private fun vpnError(): String? = getSharedPreferences(PREFS, MODE_PRIVATE)
        .getString(KEY_VPN_ERROR, null)

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "LABUDA VPN", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun notification(text: String): Notification {
        return if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL)
                .setContentTitle("LABUDA")
                .setContentText(text)
                .setSmallIcon(com.labuda.app.R.drawable.ic_labuda)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("LABUDA")
                .setContentText(text)
                .setSmallIcon(com.labuda.app.R.drawable.ic_labuda)
                .setOngoing(true)
                .build()
        }
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(text))
    }

    override fun onRevoke() {
        stopTunnel()
        super.onRevoke()
    }

    override fun onDestroy() {
        runCatching { xray?.stop() }
        xray = null
        tun?.close()
        tun = null
        setState(false, null)
        super.onDestroy()
    }
}
