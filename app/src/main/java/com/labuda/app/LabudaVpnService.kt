package com.labuda.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log

class LabudaVpnService : VpnService() {
    companion object {
        const val ACTION_START = "com.labuda.app.START"
        const val ACTION_STOP = "com.labuda.app.STOP"
        private const val CHANNEL = "labuda_vpn"
        private const val NOTIFICATION_ID = 101
        private const val PREFS = "labuda"
        private const val KEY_VPN_RUNNING = "vpn_running"
        private const val KEY_VPN_ERROR = "vpn_error"
    }

    private var tun: ParcelFileDescriptor? = null
    private var xray: XrayCoreBridge? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopTunnel()
            ACTION_START -> startTunnel()
        }
        return START_NOT_STICKY
    }

    private fun startTunnel() {
        createChannel()
        startForeground(NOTIFICATION_ID, notification("Запуск VPN…"))
        if (tun != null && xray?.isRunning() == true) return

        setState(false, null)
        val profile = ProfileStore.selectedProfile(this) ?: ProfileStore.profiles(this).firstOrNull()
        if (profile == null) {
            fail("Нет выбранного VLESS-сервера")
            return
        }

        val builder = Builder()
            .setSession("LABUDA")
            .setMtu(1500)
            .addAddress("10.10.0.2", 32)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("1.1.1.1")
            .addDnsServer("8.8.8.8")

        // Xray itself runs inside this application process. Excluding the app
        // from the Android VPN prevents Xray's outbound socket from being
        // routed back into its own TUN interface.
        runCatching { builder.addDisallowedApplication(packageName) }

        tun = builder.establish()
        val descriptor = tun ?: run {
            fail("Android не выдал TUN-интерфейс")
            return
        }

        val bridge = XrayCoreBridge(this)
        val config = XrayConfigBuilder.build(profile)
        Log.i("LABUDA-XRAY", "Starting Xray for ${profile.host}:${profile.port}")
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
        setState(true, null)
        updateNotification("Лабуда подключена • ${profile.name}")
    }

    private fun fail(message: String) {
        Log.e("LABUDA-XRAY", message)
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
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notification(text))
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
