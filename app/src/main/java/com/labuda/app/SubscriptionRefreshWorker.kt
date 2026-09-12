package com.labuda.app

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class SubscriptionRefreshWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("labuda", Context.MODE_PRIVATE)
        val raw = prefs.getString("subscriptions_json", null) ?: return Result.success()
        val source = runCatching { JSONArray(raw) }.getOrNull() ?: return Result.success()
        val updated = JSONArray()

        for (i in 0 until source.length()) {
            val item = source.optJSONObject(i) ?: continue
            val url = item.optString("url").trim()
            if (url.isBlank()) {
                updated.put(item)
                continue
            }
            val refreshed = runCatching { readSubscriptionHeaders(url) }.getOrNull()
            if (refreshed == null) {
                updated.put(item)
                continue
            }

            if (refreshed.totalBytes != null) item.put("total", refreshed.totalBytes)
            if (refreshed.usedBytes != null) item.put("used", refreshed.usedBytes)
            if (refreshed.expireFound) {
                item.put("expire", refreshed.expireAt ?: -1L)
            }
            updated.put(item)
        }

        prefs.edit().putString("subscriptions_json", updated.toString()).apply()
        return Result.success()
    }

    private data class HeaderInfo(
        val totalBytes: Long?,
        val usedBytes: Long?,
        val expireAt: Long?,
        val expireFound: Boolean
    )

    private fun readSubscriptionHeaders(urlString: String): HeaderInfo {
        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 20000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "LABUDA")
        }
        return try {
            connection.connect()
            val info = connection.getHeaderField("subscription-userinfo")
                ?: connection.getHeaderField("Subscription-Userinfo")
                ?: ""
            val values = info.split(';', ',')
                .mapNotNull {
                    val parts = it.trim().split('=', limit = 2)
                    if (parts.size == 2) parts[0].trim().lowercase() to parts[1].trim() else null
                }.toMap()

            fun number(key: String): Long? = values[key]?.toLongOrNull()
            val expireRaw = number("expire")
            HeaderInfo(
                totalBytes = number("total") ?: number("totalbytes"),
                usedBytes = number("upload")?.let { up -> (number("download") ?: 0L) + up },
                expireAt = expireRaw?.takeIf { it > 0L },
                expireFound = values.containsKey("expire")
            )
        } finally {
            connection.disconnect()
        }
    }
}
