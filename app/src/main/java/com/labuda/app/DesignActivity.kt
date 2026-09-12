package com.labuda.app

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class DesignActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LabudaTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    DesignScreen(onBack = { finish() })
                }
            }
        }
    }
}

@Composable
private fun DesignScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val saved = remember { DesignStore.load(context) }
    var colors by remember { mutableStateOf(saved) }
    Column(
        Modifier.fillMaxSize().background(colors.background).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, "Назад", tint = colors.text)
            }
            Text("Дизайн", color = colors.text, fontSize = 22.sp)
        }
        OutlinedCard(Modifier.fillMaxWidth()) {
            Text(
                "Проведите пальцем по полосе, затем нажмите «Применить».",
                modifier = Modifier.padding(16.dp),
                color = colors.text,
                fontSize = 14.sp
            )
        }
        ColorSlot("Фон", colors.background, colors.text, colors.outline) { colors = colors.copy(background = it) }
        ColorSlot("Буквы", colors.text, colors.text, colors.outline) { colors = colors.copy(text = it) }
        ColorSlot("Обводка", colors.outline, colors.text, colors.outline) { colors = colors.copy(outline = it) }
        Spacer(Modifier.weight(1f))
        Button(
            onClick = {
                DesignStore.save(context, colors)
                onBack()
            },
            modifier = Modifier.fillMaxWidth().height(46.dp)
        ) { Text("Применить") }
        OutlinedButton(
            onClick = { colors = DesignStore.default },
            modifier = Modifier.fillMaxWidth().height(46.dp)
        ) { Text("Сбросить") }
    }
}

@Composable
private fun ColorSlot(
    title: String,
    color: Color,
    text: Color,
    outline: Color,
    onColor: (Color) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(28.dp).clip(CircleShape).background(color).border(1.dp, outline, CircleShape)
            )
            Text(title, color = text, fontSize = 16.sp)
        }
        HueBar(color, onColor)
        SatValBar(color, onColor)
    }
}

private val hueColors = listOf(
    Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red
)

@Composable
private fun HueBar(color: Color, onColor: (Color) -> Unit) {
    val hsv = remember(color) { color.toHsv() }
    SpectrumBar(
        brush = Brush.horizontalGradient(hueColors),
        onPick = { t ->
            onColor(Color.hsv(t * 360f, hsv[1].coerceIn(0.15f, 1f), hsv[2].coerceIn(0.15f, 1f)))
        }
    )
}

@Composable
private fun SatValBar(color: Color, onColor: (Color) -> Unit) {
    val hsv = remember(color) { color.toHsv() }
    val hue = hsv[0]
    SpectrumBar(
        brush = Brush.horizontalGradient(listOf(Color.White, Color.hsv(hue, 1f, 1f), Color.Black)),
        onPick = { t ->
            val sat = if (t < 0.5f) t * 2f else 1f
            val value = if (t < 0.5f) 1f else (1f - (t - 0.5f) * 2f).coerceIn(0.08f, 1f)
            onColor(Color.hsv(hue, sat.coerceIn(0f, 1f), value))
        }
    )
}

@Composable
private fun SpectrumBar(brush: Brush, onPick: (Float) -> Unit) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    Box(
        Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(brush)
            .onSizeChanged { size = it }
            .pointerInput(size) {
                if (size.width <= 0) return@pointerInput
                detectTapGestures { onPick((it.x / size.width.toFloat()).coerceIn(0f, 1f)) }
            }
            .pointerInput(size) {
                if (size.width <= 0) return@pointerInput
                detectDragGestures { change, _ ->
                    change.consume()
                    onPick((change.position.x / size.width.toFloat()).coerceIn(0f, 1f))
                }
            }
    )
}

private fun Color.toHsv(): FloatArray {
    val out = FloatArray(3)
    AndroidColor.colorToHSV(toArgb(), out)
    return out
}
