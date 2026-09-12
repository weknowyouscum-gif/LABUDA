package com.labuda.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

const val LABUDA_BUY_TG = "https://t.me/WKYSPN"
const val LABUDA_BUY_WA = "https://wa.me/79080800880"
const val LABUDA_BUY_MAX = "https://max.ru/u/f9LHodD0cOLyEwhdbQzfRxm7SPPIyRAypTsIN2Qw_Arzl32LOZxWcax_pX4"
const val LABUDA_PLAN_1M = "https://yoomoney.ru/fundraise/1K8O66AS9K3.260913"
const val LABUDA_PLAN_3M = "https://yoomoney.ru/fundraise/1K8O6RDG7NO.260913"
const val LABUDA_PLAN_6M = "https://yoomoney.ru/fundraise/1K8O79QB9QI.260913"

@Composable
fun BuyScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val pill = RoundedCornerShape(26.dp)
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 22.dp, vertical = 8.dp).verticalScroll(rememberScrollState())
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Назад", tint = accent) }
            Text("Купить подписку", color = accent, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(18.dp))
        Text(
            "После оплаты отправьте скриншот сюда",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        OutlinedButton({ onOpen(LABUDA_BUY_TG) }, Modifier.fillMaxWidth().height(52.dp), shape = pill, border = BorderStroke(1.dp, accent), colors = ButtonDefaults.outlinedButtonColors(contentColor = accent)) {
            Text("Telegram", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton({ onOpen(LABUDA_BUY_WA) }, Modifier.fillMaxWidth().height(52.dp), shape = pill, border = BorderStroke(1.dp, accent), colors = ButtonDefaults.outlinedButtonColors(contentColor = accent)) {
            Text("WhatsApp", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton({ onOpen(LABUDA_BUY_MAX) }, Modifier.fillMaxWidth().height(52.dp), shape = pill, border = BorderStroke(1.dp, accent), colors = ButtonDefaults.outlinedButtonColors(contentColor = accent)) {
            Text("Max", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        Spacer(Modifier.height(22.dp))
        Button({ onOpen(LABUDA_PLAN_1M) }, Modifier.fillMaxWidth().height(58.dp), shape = pill, colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.White)) {
            Text("1 месяц — 500 ₽", fontWeight = FontWeight.Bold, fontSize = 17.sp)
        }
        Spacer(Modifier.height(10.dp))
        Button({ onOpen(LABUDA_PLAN_3M) }, Modifier.fillMaxWidth().height(58.dp), shape = pill, colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.White)) {
            Text("3 месяца — 1200 ₽", fontWeight = FontWeight.Bold, fontSize = 17.sp)
        }
        Spacer(Modifier.height(10.dp))
        Button({ onOpen(LABUDA_PLAN_6M) }, Modifier.fillMaxWidth().height(58.dp), shape = pill, colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.White)) {
            Text("6 месяцев — 2000 ₽", fontWeight = FontWeight.Bold, fontSize = 17.sp)
        }
    }
}
