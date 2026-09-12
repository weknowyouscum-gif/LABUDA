package com.labuda.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

const val LABUDA_SUPPORT_URL = "https://t.me/LABUDASAPP"
const val LABUDA_DONATE_URL = "https://yoomoney.ru/fundraise/1K8LRRNMB65.260912"

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(typography = OswaldTypography) {
                Surface(Modifier.fillMaxSize()) {
                    SettingsScreen(
                        onBack = { finish() },
                        onRouting = { startActivity(Intent(this, RoutingSettingsActivity::class.java)) },
                        onHelp = {
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(LABUDA_SUPPORT_URL)))
                        },
                        onSupport = {
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(LABUDA_DONATE_URL)))
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    onBack: () -> Unit,
    onRouting: () -> Unit,
    onHelp: () -> Unit,
    onSupport: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Назад") }
            Text("Настройки", style = MaterialTheme.typography.headlineSmall)
        }
        Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onRouting)) {
            Text("Маршрутизация", modifier = Modifier.padding(20.dp), style = MaterialTheme.typography.titleMedium)
        }
        Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onSupport)) {
            Text("Поддержка", modifier = Modifier.padding(20.dp), style = MaterialTheme.typography.titleMedium)
        }
        Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onHelp)) {
            Text("Помощь", modifier = Modifier.padding(20.dp), style = MaterialTheme.typography.titleMedium)
        }
    }
}
