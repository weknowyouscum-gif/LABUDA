package com.labuda.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class SecurityActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showSecurity()
    }

    private fun showSecurity() {
        val setup = !LabudaSecurity.isSetup(this)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    if (setup) SetupScreen() else UnlockScreen()
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun SetupScreen() {
        var pin by androidx.compose.runtime.remember { mutableStateOf("") }
        var confirm by androidx.compose.runtime.remember { mutableStateOf("") }
        var secret by androidx.compose.runtime.remember { mutableStateOf("") }
        var error by androidx.compose.runtime.remember { mutableStateOf("") }
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text("LABUDA SECURITY", fontSize = 28.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(10.dp))
            Text("Первичная настройка защиты 3FA")
            Spacer(Modifier.height(20.dp))
            OutlinedTextField(pin, { pin = it }, Modifier.fillMaxWidth(), label = { Text("PIN / пароль") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(confirm, { confirm = it }, Modifier.fillMaxWidth(), label = { Text("Повторите PIN") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
            Spacer(Modifier.height(16.dp))
            Button(onClick = {
                error = ""
                runCatching {
                    require(pin.length >= 6) { "PIN должен содержать минимум 6 символов" }
                    require(pin == confirm) { "PIN не совпадает" }
                    secret = LabudaSecurity.createSetup(this@SecurityActivity, pin)
                }.onFailure { error = it.message ?: "Ошибка настройки" }
            }, Modifier.fillMaxWidth(), enabled = secret.isBlank()) { Text("Создать защиту") }
            if (secret.isNotBlank()) {
                Spacer(Modifier.height(18.dp))
                Text("Ключ TOTP для приложения-аутентификатора:", fontWeight = FontWeight.Bold)
                Text(secret, fontSize = 18.sp)
                Spacer(Modifier.height(8.dp))
                Text("Сохрани этот ключ в Authenticator. Он нужен как третий фактор вместе с PIN и ключом устройства.")
                Spacer(Modifier.height(14.dp))
                Button(onClick = { openApp() }, Modifier.fillMaxWidth()) { Text("Я сохранил ключ — продолжить") }
            }
            if (error.isNotBlank()) Text(error, Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.error)
        }
    }

    @androidx.compose.runtime.Composable
    private fun UnlockScreen() {
        var pin by androidx.compose.runtime.remember { mutableStateOf("") }
        var otp by androidx.compose.runtime.remember { mutableStateOf("") }
        var error by androidx.compose.runtime.remember { mutableStateOf("") }
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text("LABUDA", fontSize = 34.sp, fontWeight = FontWeight.Black)
            Text("Защищённый вход · 3FA", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(22.dp))
            OutlinedTextField(pin, { pin = it }, Modifier.fillMaxWidth(), label = { Text("PIN / пароль") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(otp, { otp = it.filter(Char::isDigit).take(6) }, Modifier.fillMaxWidth(), label = { Text("Одноразовый код") }, singleLine = true)
            Spacer(Modifier.height(16.dp))
            Button(onClick = { if (LabudaSecurity.verify(this@SecurityActivity, pin, otp)) openApp() else error = "Неверные данные 3FA" }, Modifier.fillMaxWidth(), enabled = pin.isNotBlank() && otp.length == 6) { Text("Войти") }
            if (error.isNotBlank()) Text(error, Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.error)
        }
    }

    private fun openApp() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
