package com.labuda.app

import android.app.Activity
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

const val MODE_ALL = "all"
const val MODE_BYPASS = "bypass"
const val MODE_TUNNEL = "tunnel"
private const val ROUTING_PREFS = "labuda"
private const val KEY_ROUTING_MODE = "routing_mode"
private const val KEY_ROUTING_APPS = "routing_apps"

data class RoutingApp(val label: String, val packageName: String, val system: Boolean, val icon: Bitmap)

object RoutingStore {
    fun mode(context: Context): String = context.getSharedPreferences(ROUTING_PREFS, Context.MODE_PRIVATE).getString(KEY_ROUTING_MODE, MODE_ALL) ?: MODE_ALL
    fun selectedApps(context: Context): Set<String> = context.getSharedPreferences(ROUTING_PREFS, Context.MODE_PRIVATE).getStringSet(KEY_ROUTING_APPS, emptySet()) ?: emptySet()
    fun save(context: Context, mode: String, apps: Set<String>) { context.getSharedPreferences(ROUTING_PREFS, Context.MODE_PRIVATE).edit().putString(KEY_ROUTING_MODE, mode).putStringSet(KEY_ROUTING_APPS, apps).apply() }
}

class RoutingSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { RoutingScreen(this) }
    }
}

private fun drawableToBitmap(source: Drawable): Bitmap {
    val drawable = source.mutate()
    val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 96
    val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 96
    val canvasSize = maxOf(width, height).coerceAtMost(192).coerceAtLeast(48)
    val bitmap = Bitmap.createBitmap(canvasSize, canvasSize, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val drawWidth = width.coerceAtMost(canvasSize)
    val drawHeight = height.coerceAtMost(canvasSize)
    val left = (canvasSize - drawWidth) / 2
    val top = (canvasSize - drawHeight) / 2
    drawable.setBounds(left, top, left + drawWidth, top + drawHeight)
    drawable.draw(canvas)
    return bitmap
}

private fun loadApps(context: Context): List<RoutingApp> {
    val pm = context.packageManager
    return pm.getInstalledApplications(PackageManager.GET_META_DATA)
        .filter { it.packageName != context.packageName }
        .mapNotNull { app ->
            runCatching {
                RoutingApp(
                    pm.getApplicationLabel(app).toString(),
                    app.packageName,
                    (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                    drawableToBitmap(pm.getApplicationIcon(app))
                )
            }.getOrNull()
        }
        .sortedBy { it.label.lowercase() }
}

@Composable
private fun RoutingScreen(activity: RoutingSettingsActivity) {
    val context = activity
    var mode by remember { mutableStateOf(RoutingStore.mode(context)) }
    var selected by remember { mutableStateOf(RoutingStore.selectedApps(context)) }
    var filter by remember { mutableStateOf("all") }
    var search by remember { mutableStateOf("") }
    val apps = remember { loadApps(context) }
    val visible = remember(apps, filter, search) {
        val q = search.trim().lowercase()
        apps.filter {
            when (filter) {
                "system" -> it.system
                "installed" -> !it.system
                "popular" -> !it.system && PopularApps.requiresRouting(it.packageName)
                else -> true
            }
        }.filter { q.isBlank() || it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q) }
    }
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { (context as Activity).finish() }) { Icon(Icons.Filled.ArrowBack, "Назад") }
                    Text("Настройки • Маршрутизация", fontSize = 21.sp)
                }
                Text("Режим маршрутизации", fontSize = 16.sp)
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(4.dp)) {
                        listOf(MODE_ALL to "Все приложения", MODE_TUNNEL to "Только выбранные", MODE_BYPASS to "Выбранные обходят LBD").forEach { (value, title) ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = mode == value, onClick = { mode = value })
                                Text(title)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Фильтр приложений", fontSize = 16.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = filter == "all", onClick = { filter = "all" }, label = { Text("Все") })
                    FilterChip(selected = filter == "popular", onClick = { filter = "popular" }, label = { Text("Популярные") })
                    FilterChip(selected = filter == "installed", onClick = { filter = "installed" }, label = { Text("Установленные") })
                    FilterChip(selected = filter == "system", onClick = { filter = "system" }, label = { Text("Системные") })
                }
                OutlinedTextField(value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, leadingIcon = { Icon(Icons.Filled.Search, "Поиск") }, placeholder = { Text("Поиск приложения") })
                Text(
                    if (filter == "popular") "Требуют маршрутизацию: ${visible.size}" else "Приложений: ${visible.size}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(visible, key = { it.packageName }) { app ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Image(bitmap = app.icon.asImageBitmap(), contentDescription = app.label, contentScale = ContentScale.Fit, modifier = Modifier.size(40.dp).padding(4.dp))
                            Checkbox(checked = selected.contains(app.packageName), onCheckedChange = { checked -> selected = if (checked) selected + app.packageName else selected - app.packageName })
                            Column(Modifier.weight(1f)) { Text(app.label, fontSize = 14.sp); Text(app.packageName, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
                Button(onClick = { RoutingStore.save(context, mode, selected); (context as Activity).finish() }, modifier = Modifier.fillMaxWidth().height(44.dp)) { Text("Сохранить") }
            }
        }
    }
}
