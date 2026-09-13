package com.labuda.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
            var themeId by remember { mutableStateOf(DesignStore.themeId(this)) }
            var showDesign by remember { mutableStateOf(false) }
            LabudaTheme(themeId = themeId) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (showDesign) {
                        DesignScreen(
                            selected = themeId,
                            onSelect = { themeId = it },
                            onBack = { showDesign = false; themeId = DesignStore.themeId(this) },
                            onApply = { DesignStore.save(this, themeId); showDesign = false },
                            onReset = { DesignStore.reset(this); themeId = THEME_NEON }
                        )
                    } else {
                        SettingsScreen(
                            onBack = { finish() },
                            onRouting = { startActivity(Intent(this, RoutingSettingsActivity::class.java)) },
                            onDesign = { showDesign = true },
                            onHelp = { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(LABUDA_SUPPORT_URL))) },
                            onSupport = { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(LABUDA_DONATE_URL))) }
                        )
                    }
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
    val accent = MaterialTheme.colorScheme.primary
    val pill = RoundedCornerShape(32.dp)
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Назад", tint = accent) }
            Text("Настройки", color = accent, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        Button(onRouting, Modifier.fillMaxWidth().height(64.dp), shape = pill, colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.White)) {
            Text("Маршрутизация", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        OutlinedButton(onDesign, Modifier.fillMaxWidth().height(64.dp), shape = pill, border = BorderStroke(2.dp, accent), colors = ButtonDefaults.outlinedButtonColors(contentColor = accent)) {
            Text("Дизайн", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Button(onSupport, Modifier.fillMaxWidth().height(64.dp), shape = pill, colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.White)) {
            Text("Поддержи автора", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        OutlinedButton(onHelp, Modifier.fillMaxWidth().height(64.dp), shape = pill, border = BorderStroke(2.dp, accent), colors = ButtonDefaults.outlinedButtonColors(contentColor = accent)) {
            Text("Помощь", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DesignScreen(
    selected: String,
    onSelect: (String) -> Unit,
    onBack: () -> Unit,
    onApply: () -> Unit,
    onReset: () -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary
    val pill = RoundedCornerShape(28.dp)
    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Назад", tint = accent) }
            Text("Дизайн", color = accent, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }
        Text("Тема", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        ThemeCard("Неон", "Фиолетовый неон", THEME_NEON, selected, Color(0xFFB56BFF), onSelect)
        ThemeCard("Бордо", "Тёмный бордовый", THEME_BORDO, selected, Color(0xFFC43B5A), onSelect)
        ThemeCard("Оранж", "Чёрно-оранжевый", THEME_ORANGE, selected, Color(0xFFFF6A00), onSelect)
        Spacer(Modifier.height(8.dp))
        Button(onApply, Modifier.fillMaxWidth().height(56.dp), shape = pill, colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.White)) {
            Text("Применить", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        OutlinedButton(onReset, Modifier.fillMaxWidth().height(56.dp), shape = pill, border = BorderStroke(2.dp, accent), colors = ButtonDefaults.outlinedButtonColors(contentColor = accent)) {
            Text("Сбросить", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ThemeCard(title: String, subtitle: String, id: String, selected: String, swatch: Color, onSelect: (String) -> Unit) {
    val on = selected == id
    Card(
        Modifier.fillMaxWidth().clickable { onSelect(id) },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(if (on) 2.dp else 1.dp, if (on) swatch else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Card(Modifier.height(36.dp).fillMaxWidth(0.18f), colors = CardDefaults.cardColors(containerColor = swatch), shape = RoundedCornerShape(8.dp)) {}
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (on) Text("\u2713", color = swatch, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
    }
}
