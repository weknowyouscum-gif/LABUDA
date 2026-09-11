package com.labuda.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(typography = Tele2Typography) {
                SettingsScreen(
                    onRouting = { startActivity(Intent(this, RoutingSettingsActivity::class.java)) },
                    onHelp = {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/LABUDASUPPORT")))
                    }
                )
            }
        }
    }
}

@Composable
private fun SettingsScreen(onRouting: () -> Unit, onHelp: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Настройки", style = MaterialTheme.typography.headlineSmall)
        Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onRouting)) {
            Text("Маршрутизация", modifier = Modifier.padding(20.dp), style = MaterialTheme.typography.titleMedium)
        }
        Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onHelp)) {
            Text("Помощь", modifier = Modifier.padding(20.dp), style = MaterialTheme.typography.titleMedium)
        }
    }
}
