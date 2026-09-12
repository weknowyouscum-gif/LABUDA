package com.labuda.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
    onHelp: () -> Unit,
    onSupport: () -> Unit
) {
    val purple = MaterialTheme.colorScheme.primary
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Назад", tint = purple) }
            Text("Настройки", color = purple, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Button(
            onRouting,
            Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = purple, contentColor = Color.White)
        ) { Text("Маршрутизация", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        Button(
            onSupport,
            Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = purple, contentColor = Color.White)
        ) { Text("Поддержка", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        OutlinedButton(
            onHelp,
            Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, purple),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = purple)
        ) { Text("Помощь", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
    }
}
