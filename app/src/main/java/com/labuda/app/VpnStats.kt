package com.labuda.app

import android.content.Context
import android.net.TrafficStats
import android.os.SystemClock
import kotlin.math.max

object VpnStats {
    const val PREFS = "labuda"
    const val KEY_RX = "vpn_rx_bytes"
    const val KEY_TX = "vpn_tx_bytes"
    const val KEY_RX_SPEED = "vpn_rx_bps"
    const val KEY_TX_SPEED = "vpn_tx_bps"
    const val KEY_COMMENT = "vpn_comment"

    fun reset(context: Context, comment: String = "Подключение…") {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_RX, 0L)
            .putLong(KEY_TX, 0L)
            .putLong(KEY_RX_SPEED, 0L)
            .putLong(KEY_TX_SPEED, 0L)
            .putString(KEY_COMMENT, comment)
            .apply()
    }

    fun setComment(context: Context, comment: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_COMMENT, comment)
            .apply()
    }

    fun startMonitor(context: Context) {
        val appContext = context.applicationContext
        Thread {
            val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            var lastRx = TrafficStats.getUidRxBytes(android.os.Process.myUid())
            var lastTx = TrafficStats.getUidTxBytes(android.os.Process.myUid())
            var lastTime = SystemClock.elapsedRealtime()
            var totalRx = 0L
            var totalTx = 0L
            while (prefs.getBoolean("vpn_running", false) || prefs.getString(KEY_COMMENT, "") == "Подключение…") {
                Thread.sleep(1000L)
                val now = SystemClock.elapsedRealtime()
                val rx = TrafficStats.getUidRxBytes(android.os.Process.myUid())
                val tx = TrafficStats.getUidTxBytes(android.os.Process.myUid())
                if (rx >= 0L && lastRx >= 0L) totalRx += max(0L, rx - lastRx)
                if (tx >= 0L && lastTx >= 0L) totalTx += max(0L, tx - lastTx)
                val seconds = max(1L, now - lastTime) / 1000.0
                val rxSpeed = if (rx >= 0L && lastRx >= 0L) (max(0L, rx - lastRx) / seconds).toLong() else -1L
                val txSpeed = if (tx >= 0L && lastTx >= 0L) (max(0L, tx - lastTx) / seconds).toLong() else -1L
                prefs.edit()
                    .putLong(KEY_RX, totalRx)
                    .putLong(KEY_TX, totalTx)
                    .putLong(KEY_RX_SPEED, rxSpeed)
                    .putLong(KEY_TX_SPEED, txSpeed)
                    .apply()
                lastRx = rx
                lastTx = tx
                lastTime = now
            }
        }.apply {
            name = "LABUDA-TrafficStats"
            isDaemon = true
        }.start()
    }
}
