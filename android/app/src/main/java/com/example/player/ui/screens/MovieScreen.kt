package com.example.player.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.player.data.MediaInfo
import com.example.player.data.Movie
import com.example.player.data.MovieDetail
import com.example.player.ui.theme.AppColors
import com.example.player.ui.theme.AppType
import com.example.player.ui.theme.InitialsTile
import com.example.player.ui.theme.PillButton

/** Movie page: backdrop, poster, synopsis and extras, with Watch / Trailer / Favourite. */
@Composable
fun MovieScreen(
    movie: Movie,
    detail: MovieDetail?,
    favourite: Boolean,
    onPlay: () -> Unit,
    onToggleFavourite: () -> Unit,
) {
    val context = LocalContext.current
    val current = detail?.takeIf { it.movie.id == movie.id }
    val info = current?.info
    val watch = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { watch.requestFocus() } }

    Box(Modifier.fillMaxSize().background(AppColors.bg)) {
        val backdrop = info?.backdrop ?: movie.poster
        if (backdrop != null) {
            AsyncImage(model = backdrop, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.horizontalGradient(listOf(AppColors.bg, AppColors.bg.copy(alpha = 0.93f), AppColors.bg.copy(alpha = 0.55f)))),
        )
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 40.dp, vertical = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(30.dp),
        ) {
            Box(
                Modifier
                    .width(230.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(AppColors.surfaceRaised),
            ) {
                if (movie.poster != null) {
                    AsyncImage(model = movie.poster, contentDescription = movie.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    InitialsTile(movie.name, 80.dp, Modifier.fillMaxSize())
                }
            }
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(movie.name, style = AppType.headline, color = AppColors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                MetaLine(info)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    PillButton("▶  Assistir", primary = true, modifier = Modifier.focusRequester(watch), onClick = onPlay)
                    val trailer = info?.trailer
                    if (trailer != null) PillButton("🎬  Trailer", onClick = { openTrailer(context, trailer) })
                    PillButton(if (favourite) "♥  Favorito" else "♡  Favoritar", onClick = onToggleFavourite)
                }
                if (current == null) {
                    CircularProgressIndicator(color = AppColors.accent, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
                } else {
                    Synopsis(info)
                }
            }
        }
    }
}

/** "2023 · 1h 52min · ★ 7.8 · Ação, Aventura" — only the parts the provider sent. */
@Composable
fun MetaLine(info: MediaInfo?) {
    if (info == null) return
    val parts = listOfNotNull(
        info.releaseDate?.take(4)?.takeIf { it.all(Char::isDigit) } ?: info.releaseDate,
        info.duration?.let(::prettyDuration),
        info.rating?.let { "★ $it" },
        info.genre,
    )
    if (parts.isNotEmpty()) {
        Text(parts.joinToString("  ·  "), style = AppType.label, color = AppColors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun Synopsis(info: MediaInfo?) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Sinopse", style = AppType.title, color = AppColors.text)
        Text(
            info?.plot?.takeIf { it.isNotBlank() } ?: "O servidor não enviou a sinopse deste título.",
            style = AppType.body,
            color = AppColors.textMuted,
        )
        info?.director?.let { Credit("Direção", it) }
        info?.cast?.let { Credit("Elenco", it) }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Credit(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, style = AppType.caption.copy(fontWeight = FontWeight.Bold), color = AppColors.accent, modifier = Modifier.width(70.dp))
        Text(value, style = AppType.caption, color = AppColors.text, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

/** "01:52:00" or "6720" seconds → "1h 52min"; anything else is shown as sent. */
private fun prettyDuration(raw: String): String {
    val parts = raw.split(":").mapNotNull { it.trim().toIntOrNull() }
    val minutes = when {
        parts.size == 3 -> parts[0] * 60 + parts[1]
        parts.size == 2 -> parts[0] * 60 + parts[1]
        raw.trim().toIntOrNull() != null -> raw.trim().toInt().let { if (it > 600) it / 60 else it }
        else -> return raw
    }
    if (minutes <= 0) return raw
    return if (minutes >= 60) "${minutes / 60}h ${minutes % 60}min" else "$minutes min"
}

/** Opens the trailer in the YouTube app (pre-installed on Android TV), or any browser. */
fun openTrailer(context: Context, trailer: String) {
    val t = trailer.trim()
    val id = when {
        t.contains("v=") -> t.substringAfter("v=").substringBefore('&')
        t.contains("youtu.be/") -> t.substringAfter("youtu.be/").substringBefore('?')
        t.startsWith("http") -> null
        else -> t
    }
    val intents = buildList {
        if (id != null) add(Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$id")))
        add(Intent(Intent.ACTION_VIEW, Uri.parse(if (id != null) "https://www.youtube.com/watch?v=$id" else t)))
    }
    for (intent in intents) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (e: ActivityNotFoundException) {
            // try the next way to open it
        }
    }
    Toast.makeText(context, "Instale o app do YouTube para ver o trailer.", Toast.LENGTH_LONG).show()
}

/** Full synopsis and credits in a dialog (series page). Back or "Fechar" closes it. */
@Composable
fun SynopsisDialog(title: String, info: MediaInfo, onDismiss: () -> Unit) {
    val close = remember { FocusRequester() }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(AppColors.glass)
                .padding(26.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = AppType.title, color = AppColors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            MetaLine(info)
            Column(Modifier.height(260.dp).verticalScroll(rememberScrollState())) { Synopsis(info) }
            PillButton("Fechar", primary = true, modifier = Modifier.focusRequester(close), onClick = onDismiss)
        }
    }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100)
        runCatching { close.requestFocus() }
    }
}
