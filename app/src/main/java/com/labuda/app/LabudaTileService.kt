package com.labuda.app

import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class LabudaTileService : TileService() {
    override fun onStartListening() {
        refresh()
    }

    override fun onClick() {
        val running = prefs().getBoolean(KEY_VPN_RUNNING, false)
        if (running) {
            startService(Intent(this, LabudaVpnService::class.java).setAction(LabudaVpnService.ACTION_STOP))
            qsTile?.state = Tile.STATE_INACTIVE
            qsTile?.subtitle = "Отключена"
            qsTile?.updateTile()
            return
        }
        val prepare = VpnService.prepare(this)
        if (prepare != null) {
            val launch = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startActivityAndCollapse(android.app.PendingIntent.getActivity(this, 0, launch, android.app.PendingIntent.FLAG_IMMUTABLE))
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(launch)
            }
            return
        }
        startService(Intent(this, LabudaVpnService::class.java).setAction(LabudaVpnService.ACTION_START))
        qsTile?.state = Tile.STATE_ACTIVE
        qsTile?.subtitle = "Подключена"
        qsTile?.updateTile()
    }

    private fun refresh() {
        val running = prefs().getBoolean(KEY_VPN_RUNNING, false)
        qsTile?.apply {
            label = "LABUDA"
            state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = if (running) "Подключена" else "Отключена"
            }
            updateTile()
        }
    }

    private fun prefs() = getSharedPreferences(PREFS, MODE_PRIVATE)
}
