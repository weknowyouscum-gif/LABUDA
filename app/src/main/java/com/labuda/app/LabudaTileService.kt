package com.labuda.app

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class LabudaTileService : TileService() {
    override fun onStartListening() {
        refresh()
    }

    override fun onClick() {
        val running = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_VPN_RUNNING, false)
        if (!running && VpnService.prepare(this) != null) {
            val launch = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val pi = PendingIntent.getActivity(this, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            startActivityAndCollapse(pi)
            return
        }
        val action = if (running) LabudaVpnService.ACTION_STOP else LabudaVpnService.ACTION_START
        val intent = Intent(this, LabudaVpnService::class.java).setAction(action)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        }.onFailure { startService(intent) }
        qsTile?.state = if (running) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
        qsTile?.updateTile()
    }

    private fun refresh() {
        val running = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_VPN_RUNNING, false)
        qsTile?.apply {
            state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = "L"
            contentDescription = "LABUDA"
            icon = Icon.createWithResource(this@LabudaTileService, R.drawable.ic_stat_labuda)
            updateTile()
        }
    }
}
