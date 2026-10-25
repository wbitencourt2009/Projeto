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
fun SettingsScreen(tmdbKey: String, onSave: (String) -> Unit) {
    var key by remember { mutableStateOf(tmdbKey) }
    var saved by remember { mutableStateOf(false) }
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
        }
    }
}
