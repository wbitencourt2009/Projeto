package com.example.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.player.data.Profile
import com.example.player.data.normaliseServer
import com.example.player.ui.theme.AppColors
import com.example.player.ui.theme.AppType
import com.example.player.ui.theme.PillButton
import com.example.player.ui.theme.heroBackdrop

@Composable
fun LoginScreen(error: String?, onLogin: (Profile) -> Unit) {
    var xtream by rememberSaveable { mutableStateOf(true) }
    var name by rememberSaveable { mutableStateOf("Minha lista") }
    var server by rememberSaveable { mutableStateOf("") }
    var user by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }
    var m3u by rememberSaveable { mutableStateOf("") }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }

    Box(
        Modifier
            .fillMaxSize()
            .background(heroBackdrop()),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(32.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(AppColors.glass.copy(alpha = 0.92f))
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Bem-vindo", style = AppType.display, color = AppColors.text)
            Text(
                "Entre com os dados que o seu provedor de IPTV enviou. O app não vem com canais: ele toca a sua assinatura.",
                style = AppType.body,
                color = AppColors.textMuted,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PillButton("Xtream Codes", primary = xtream) { xtream = true }
                PillButton("Lista M3U", primary = !xtream) { xtream = false }
            }
            Field("Nome da lista", name) { name = it }
            if (xtream) {
                Field("Servidor (ex.: http://exemplo.com:8080)", server, KeyboardType.Uri) { server = it }
                Field("Usuário", user) { user = it }
                Field("Senha", pass, KeyboardType.Password, isPassword = true) { pass = it }
            } else {
                Field("Link da lista M3U", m3u, KeyboardType.Uri) { m3u = it }
            }
            val shown = localError ?: error
            if (shown != null) {
                Text(shown, style = AppType.body, color = AppColors.danger)
            }
            Spacer(Modifier.height(4.dp))
            PillButton("Entrar", primary = true, modifier = Modifier.align(Alignment.Start)) {
                localError = null
                if (xtream) {
                    if (server.isBlank() || user.isBlank() || pass.isBlank()) {
                        localError = "Preencha servidor, usuário e senha."
                    } else {
                        onLogin(
                            Profile(
                                name = name.ifBlank { "Minha lista" },
                                type = Profile.TYPE_XTREAM,
                                server = normaliseServer(server),
                                username = user.trim(),
                                password = pass,
                            )
                        )
                    }
                } else {
                    val url = m3u.trim()
                    if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) {
                        localError = "O link da lista deve começar com http:// ou https://"
                    } else {
                        onLogin(Profile(name = name.ifBlank { "Minha lista" }, type = Profile.TYPE_M3U, m3uUrl = url))
                    }
                }
            }
        }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    keyboard: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = ImeAction.Next),
        visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = AppColors.text,
            unfocusedTextColor = AppColors.text,
            focusedBorderColor = AppColors.focus,
            unfocusedBorderColor = AppColors.borderInput,
            focusedLabelColor = AppColors.focus,
            unfocusedLabelColor = AppColors.textMuted,
            cursorColor = AppColors.accent,
            focusedContainerColor = AppColors.surface,
            unfocusedContainerColor = AppColors.surface,
        ),
    )
}

@Composable
fun LoadingScreen(step: String) {
    Box(
        Modifier
            .fillMaxSize()
            .background(heroBackdrop()),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            androidx.compose.material3.CircularProgressIndicator(color = AppColors.accent, strokeWidth = 4.dp)
            Text(step, style = AppType.title, color = AppColors.text)
            Text("Listas grandes podem levar alguns segundos.", style = AppType.body, color = AppColors.textMuted)
        }
    }
}
