package com.example.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.player.data.SubtitleResult
import com.example.player.ui.theme.AppColors
import com.example.player.ui.theme.AppType
import com.example.player.ui.theme.PillButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** A subtitle track the video already has (or the one downloaded), as listed in the menu. */
data class TextTrackOption(val label: String, val selected: Boolean, val select: () -> Unit)

/**
 * Subtitles menu: off, the tracks the video has, text size, and a search for
 * Portuguese subtitles online when the video has none (or none in Portuguese).
 */
@Composable
fun SubtitleDialog(
    enabled: Boolean,
    tracks: List<TextTrackOption>,
    sizeName: String,
    hasDownloaded: Boolean,
    canSearch: Boolean,
    onOff: () -> Unit,
    onCycleSize: () -> Unit,
    onRemoveDownloaded: () -> Unit,
    search: suspend () -> List<SubtitleResult>,
    pick: suspend (SubtitleResult) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var results by remember { mutableStateOf<List<SubtitleResult>?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val first = remember { FocusRequester() }

    fun runSearch() {
        busy = "Procurando legendas em português…"
        message = null
        scope.launch {
            try {
                val found = search()
                results = found
                if (found.isEmpty()) message = "Nenhuma legenda em português encontrada para este título."
            } catch (e: Exception) {
                message = e.message ?: "Não foi possível buscar agora."
            } finally {
                busy = null
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .widthIn(max = 620.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(AppColors.glass)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(if (results == null) "Legendas" else "Legendas encontradas", style = AppType.title, color = AppColors.text)
            message?.let { Text(it, style = AppType.body, color = AppColors.warning) }
            busy?.let {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(color = AppColors.accent, strokeWidth = 3.dp, modifier = Modifier.size(24.dp))
                    Text(it, style = AppType.body, color = AppColors.textMuted)
                }
            }
            Column(
                Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val list = results
                if (list == null) {
                    PillButton(
                        (if (!enabled) "✓  " else "") + "Desligada",
                        primary = !enabled,
                        modifier = Modifier.focusRequester(first),
                        onClick = { onOff(); onDismiss() },
                    )
                    tracks.forEach { t ->
                        PillButton((if (enabled && t.selected) "✓  " else "") + t.label, primary = enabled && t.selected, onClick = { t.select(); onDismiss() })
                    }
                    if (tracks.isEmpty()) {
                        Text("Este vídeo não tem legenda própria.", style = AppType.caption, color = AppColors.textMuted)
                    }
                    if (canSearch) PillButton("🔎  Buscar legenda em português na internet", onClick = { if (busy == null) runSearch() })
                    if (hasDownloaded) PillButton("🗑  Apagar legenda baixada", onClick = { onRemoveDownloaded(); onDismiss() })
                    PillButton("Tamanho do texto: $sizeName", onClick = onCycleSize)
                } else {
                    list.forEachIndexed { i, r ->
                        PillButton(
                            "${r.language} · ${r.label}" + if (r.downloads > 0) "  (${r.downloads} downloads)" else "",
                            modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                            onClick = {
                                if (busy != null) return@PillButton
                                busy = "Baixando legenda…"
                                message = null
                                scope.launch {
                                    try {
                                        pick(r)
                                        onDismiss()
                                    } catch (e: Exception) {
                                        message = e.message ?: "Não foi possível baixar esta legenda."
                                    } finally {
                                        busy = null
                                    }
                                }
                            },
                        )
                    }
                    PillButton("← Voltar", modifier = if (list.isEmpty()) Modifier.focusRequester(first) else Modifier, onClick = { results = null; message = null })
                }
            }
            Text(
                "Dica: escolha a legenda que tenha o mesmo nome da versão do filme (ex.: 1080p, WEB) para ficar sincronizada.",
                style = AppType.caption,
                color = AppColors.textFaint,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    LaunchedEffect(results) {
        delay(100)
        runCatching { first.requestFocus() }
    }
}
