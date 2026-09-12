package com.labuda.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

const val LABUDA_SUPPORT_URL = "https://t.me/LABUDASAPP"
const val LABUDA_DONATE_URL = "https://yoomoney.ru/fundraise/1K8LRRNMB65.260912"

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LabudaTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    SettingsScreen(
                        onBack = { finish() },
                        onRouting = { startActivity(Intent(this, RoutingSettingsActivity::class.java)) },
                        onDesign = { startActivity(Intent(this, DesignActivity::class.java)) },
                        onHelp = { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(LABUDA_SUPPORT_URL))) },
                        onSupport = { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(LABUDA_DONATE_URL))) }
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
    onDesign: () -> Unit,
    onHelp: () -> Unit,
    onSupport: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Назад") }
            Text("Настройки", style = MaterialTheme.typography.headlineSmall)
        }
        Button(onRouting, Modifier.fillMaxWidth().height(52.dp)) {
            Text("Маршрутизация", fontSize = 16.sp)
        }
        Button(onDesign, Modifier.fillMaxWidth().height(52.dp)) {
            Text("Дизайн", fontSize = 16.sp)
        }
        Button(onSupport, Modifier.fillMaxWidth().height(52.dp)) {
            Text("Поддержка", fontSize = 16.sp)
        }
        OutlinedButton(onHelp, Modifier.fillMaxWidth().height(52.dp)) {
            Text("Помощь", fontSize = 16.sp)
        }
    }
}
