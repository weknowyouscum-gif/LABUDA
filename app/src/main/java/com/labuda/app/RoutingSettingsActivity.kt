package com.labuda.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val PREFS = "labuda"
private const val KEY_ROUTING_MODE = "routing_mode"
private const val KEY_ROUTING_APPS = "routing_apps"

private const val MODE_ALL = "all"
private const val MODE_BYPASS = "bypass"
private const val MODE_TUNNEL = "tunnel"

data class RoutingApp(val packageName: String, val label: String)

object RoutingStore {
    fun mode(context: Context): String = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(KEY_ROUTING_MODE, MODE_ALL).orEmpty()
        .let { when (it) { MODE_ALL -> MODE_ALL; MODE_TUNNEL -> MODE_TUNNEL; else -> MODE_BYPASS } }

    fun selectedApps(context: Context): Set<String> = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getStringSet(KEY_ROUTING_APPS, emptySet()).orEmpty()

    fun save(context: Context, mode: String, apps: Set<String>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_ROUTING_MODE, mode)
            .putStringSet(KEY_ROUTING_APPS, apps)
            .apply()
    }
}

class RoutingSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { RoutingScreen(this) }
    }

    override fun onBackPressed() {
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }
}

@androidx.compose.runtime.Composable
private fun RoutingScreen(context: Context) {
    var mode by remember { mutableStateOf(RoutingStore.mode(context)) }
    var selectedApps by remember { mutableStateOf(RoutingStore.selectedApps(context)) }
    var search by remember { mutableStateOf("") }
    val apps = remember { loadApps(context) }
    val filteredApps = remember(apps, search) {
        val q = search.trim().lowercase()
        if (q.isBlank()) apps else apps.filter {
            it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q)
        }
    }

    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(14.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { (context as? ComponentActivity)?.finish() }) {
                        Icon(Icons.Filled.ArrowBack, "Назад")
                    }
                    Text("Маршрутизация", fontSize = 23.sp)
                }
                Spacer(Modifier.height(6.dp))
                Text("Режим", fontSize = 17.sp)
                Spacer(Modifier.height(4.dp))

                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors()) {
                    Column(Modifier.padding(6.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = mode == MODE_ALL, onClick = { mode = MODE_ALL })
                            Column(Modifier.weight(1f)) {
                                Text("1. Весь трафик через LBD", fontSize = 15.sp)
                                Text("Все приложения работают через туннель", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                            }
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = mode == MODE_TUNNEL, onClick = { mode = MODE_TUNNEL })
                            Column(Modifier.weight(1f)) {
                                Text("2. Только выбранные приложения", fontSize = 15.sp)
                                Text("Отмеченные приложения работают через LBD", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                            }
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = mode == MODE_BYPASS, onClick = { mode = MODE_BYPASS })
                            Column(Modifier.weight(1f)) {
                                Text("3. Выбранные приложения обходят LBD", fontSize = 15.sp)
                                Text("Отмеченные приложения работают напрямую", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Приложения (${apps.size})", fontSize = 17.sp, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Поиск") },
                    placeholder = { Text("Поиск приложения") }
                )
                Text(
                    when (mode) {
                        MODE_ALL -> "При этом режиме выбор приложений не используется"
                        MODE_BYPASS -> "Отмеченные приложения будут обходить LBD"
                        else -> "Отмеченные приложения будут работать через LBD"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(4.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(filteredApps, key = { it.packageName }) { app ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = selectedApps.contains(app.packageName),
                                onCheckedChange = { checked ->
                                    selectedApps = if (checked) selectedApps + app.packageName else selectedApps - app.packageName
                                }
                            )
                            Column(Modifier.weight(1f)) {
                                Text(app.label, fontSize = 14.sp)
                                Text(app.packageName, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        RoutingStore.save(context, mode, selectedApps)
                        (context as? ComponentActivity)?.finish()
                    },
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) { Text("Сохранить") }
            }
        }
    }
}

private fun loadApps(context: Context): List<RoutingApp> {
    val pm = context.packageManager
    return pm.getInstalledApplications(0)
        .filter { it.packageName != context.packageName }
        .map { RoutingApp(it.packageName, it.loadLabel(pm).toString().ifBlank { it.packageName }) }
        .sortedBy { it.label.lowercase() }
}
