package com.example.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.player.ui.theme.AppColors
import com.example.player.ui.theme.AppType
import com.example.player.ui.theme.PillButton

/** Settings: the optional TMDB key used to fill missing synopsis, trailer and cast. */
@Composable
fun SettingsScreen(
    tmdbKey: String,
    onSave: (String) -> Unit,
    subtitleKey: String = "",
    subtitleUser: String = "",
    subtitlePasswordSaved: Boolean = false,
    onSaveSubtitles: (key: String, user: String, password: String?) -> Unit = { _, _, _ -> },
) {
    var key by remember { mutableStateOf(tmdbKey) }
    var saved by remember { mutableStateOf(false) }
    var osKey by remember { mutableStateOf(subtitleKey) }
    var osUser by remember { mutableStateOf(subtitleUser) }
    var osPassword by remember { mutableStateOf("") }
    var osSaved by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(AppColors.bg), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .widthIn(max = 640.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(AppColors.glass)
                .padding(28.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Ajustes", style = AppType.headline, color = AppColors.text)
            Text("Sinopse e trailer", style = AppType.title, color = AppColors.text)
            Text(
                "Quando o servidor não envia a sinopse, o app busca na Wikipédia. " +
                    "Para resultados melhores (sinopse em português, trailer, elenco e notas), " +
                    "cole aqui sua chave gratuita do TMDB: crie uma conta em themoviedb.org, " +
                    "vá em Configurações → API e copie a \"Chave da API\".",
                style = AppType.body,
                color = AppColors.textMuted,
            )
            Field(label = "Chave da API do TMDB (opcional)", value = key, onChange = { key = it; saved = false })
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                PillButton("Salvar", primary = true, onClick = { onSave(key); saved = true })
                if (key.isNotEmpty()) PillButton("Apagar chave", onClick = { key = ""; onSave(""); saved = true })
                if (saved) Text("Salvo ✓", style = AppType.label, color = AppColors.accent)
            }
            Text(
                "Sem chave, o botão Trailer abre a busca do YouTube pelo nome do filme.",
                style = AppType.caption,
                color = AppColors.textFaint,
            )

            Text("Legendas da internet", style = AppType.title, color = AppColors.text)
            Text(
                "Quando o filme ou episódio não tem legenda, o app busca legendas em português no OpenSubtitles. " +
                    "Crie uma conta grátis em opensubtitles.com, abra o seu perfil → API consumers, crie um \"consumer\" " +
                    "e copie a \"API key\". Usuário e senha são opcionais e aumentam o limite diário de downloads.",
                style = AppType.body,
                color = AppColors.textMuted,
            )
            Field(label = "Chave da API do OpenSubtitles", value = osKey, onChange = { osKey = it; osSaved = false })
            Field(label = "Usuário do OpenSubtitles (opcional)", value = osUser, onChange = { osUser = it; osSaved = false })
            Field(
                label = if (subtitlePasswordSaved) "Senha (salva — digite para trocar)" else "Senha do OpenSubtitles (opcional)",
                value = osPassword,
                isPassword = true,
                onChange = { osPassword = it; osSaved = false },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                PillButton("Salvar legendas", primary = true, onClick = {
                    onSaveSubtitles(osKey, osUser, osPassword.ifEmpty { null })
                    osPassword = ""
                    osSaved = true
                })
                if (subtitlePasswordSaved || osKey.isNotEmpty()) PillButton("Apagar dados", onClick = {
                    osKey = ""; osUser = ""; osPassword = ""
                    onSaveSubtitles("", "", "")
                    osSaved = true
                })
                if (osSaved) Text("Salvo ✓", style = AppType.label, color = AppColors.accent)
            }
            Text(
                "No filme: aperte ▼ (ou o botão CC) para ligar, desligar ou buscar legenda.",
                style = AppType.caption,
                color = AppColors.textFaint,
            )
        }
    }
}
