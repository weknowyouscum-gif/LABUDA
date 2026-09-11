package com.labuda.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(typography = OswaldTypography) {
                SettingsScreen(
                    onBack = { finish() },
                    onRouting = { startActivity(Intent(this, RoutingSettingsActivity::class.java)) },
                    onHelp = {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/LABUDASAPP")))
                    }
                )
            }
        }
    }
}

@Composable
private fun SettingsScreen(onBack: () -> Unit, onRouting: () -> Unit, onHelp: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Назад")
            }
            Text("Настройки", style = MaterialTheme.typography.headlineSmall)
        }
        Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onRouting)) {
            Text("Маршрутизация", modifier = Modifier.padding(20.dp), style = MaterialTheme.typography.titleMedium)
        }
        Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onHelp)) {
            Text("Помощь", modifier = Modifier.padding(20.dp), style = MaterialTheme.typography.titleMedium)
        }
    }
}