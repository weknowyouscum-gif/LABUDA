package com.labuda.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor

class LabudaVpnService : VpnService() {
    companion object {
        const val ACTION_START = "com.labuda.app.START"
        const val ACTION_STOP = "com.labuda.app.STOP"
        private const val CHANNEL = "labuda_vpn"
        private const val NOTIFICATION_ID = 101
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
        startForeground(NOTIFICATION_ID, notification())
        if (tun != null) return

        val profile = ProfileStore.profiles(this).firstOrNull()
        if (profile == null) {
            stopTunnel()
            return
        }

        tun = Builder()
            .setSession("LABUDA")
            .setMtu(1500)
            .addAddress("10.10.0.2", 32)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("1.1.1.1")
            .addDnsServer("8.8.8.8")
            .establish()

        val descriptor = tun ?: run { stopTunnel(); return }
        val bridge = XrayCoreBridge(this)
        val result = bridge.start(XrayConfigBuilder.build(profile), descriptor.fd)
        if (result.isFailure) {
            bridge.stop()
            descriptor.close()
            tun = null
            stopTunnel()
            return
        }
        xray = bridge
    }

    private fun stopTunnel() {
        runCatching { xray?.stop() }
        xray = null
        tun?.close()
        tun = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "LABUDA VPN", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun notification(): Notification {
        return if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL)
                .setContentTitle("Лабуда подключена")
                .setContentText("Xray VPN активен")
                .setSmallIcon(com.labuda.app.R.drawable.ic_labuda)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("Лабуда подключена")
                .setContentText("Xray VPN активен")
                .setSmallIcon(com.labuda.app.R.drawable.ic_labuda)
                .setOngoing(true)
                .build()
        }
    }

    override fun onDestroy() { runCatching { xray?.stop() }; tun?.close(); tun = null; super.onDestroy() }
}
