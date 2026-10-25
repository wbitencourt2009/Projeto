package com.example.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.player.data.Episode
import com.example.player.data.Series
import com.example.player.data.SeriesDetail
import com.example.player.ui.theme.AppColors
import com.example.player.ui.theme.AppType
import com.example.player.ui.theme.FocusBox
import com.example.player.ui.theme.InitialsTile
import com.example.player.ui.theme.PillButton

@Composable
fun SeriesScreen(
    series: Series,
    detail: SeriesDetail?,
    error: String?,
    favourite: Boolean,
    onToggleFavourite: () -> Unit,
    onRetry: () -> Unit,
    onPlay: (List<Episode>, Int) -> Unit,
) {
    val current = detail?.takeIf { it.series.id == series.id }
    val seasons = current?.episodes?.keys?.sorted().orEmpty()
    var season by remember(current) { mutableStateOf(seasons.firstOrNull()) }
    val episodes = season?.let { current?.episodes?.get(it) }.orEmpty()
    val firstEpisode = remember { FocusRequester() }
    val context = androidx.compose.ui.platform.LocalContext.current
    var showSynopsis by remember { mutableStateOf(false) }
    if (showSynopsis) {
        SynopsisDialog(series.name, current?.info ?: com.example.player.data.MediaInfo(plot = series.plot)) { showSynopsis = false }
    }
    LaunchedEffect(current) { if (current != null) runCatching { firstEpisode.requestFocus() } }

    Box(Modifier.fillMaxSize().background(AppColors.bg)) {
        val backdrop = current?.backdrop ?: series.cover
        if (backdrop != null) {
            AsyncImage(model = backdrop, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.horizontalGradient(listOf(AppColors.bg, AppColors.bg.copy(alpha = 0.92f), AppColors.bg.copy(alpha = 0.55f)))),
        )
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 40.dp, vertical = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Column(Modifier.width(220.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(AppColors.surfaceRaised),
                ) {
                    if (series.cover != null) {
                        AsyncImage(model = series.cover, contentDescription = series.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        InitialsTile(series.name, 80.dp, Modifier.fillMaxSize())
                    }
                }
                PillButton(if (favourite) "♥  Favorito" else "♡  Favoritar", primary = favourite, onClick = onToggleFavourite)
                PillButton("📖  Sinopse", onClick = { showSynopsis = true })
                val trailer = current?.info?.trailer
                PillButton("🎬  Trailer", onClick = {
                    if (trailer != null) openTrailer(context, trailer) else searchTrailer(context, series.name)
                })
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Text(series.name, style = AppType.headline, color = AppColors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (current != null) MetaLine(current.info) else series.rating?.let { Text("★ $it", style = AppType.caption, color = AppColors.warning) }
                val plot = current?.plot ?: series.plot
                if (!plot.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(plot, style = AppType.body, color = AppColors.textMuted, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(16.dp))
                when {
                    error != null && current == null -> {
                        Text(error, style = AppType.body, color = AppColors.danger)
                        Spacer(Modifier.height(10.dp))
                        PillButton("Tentar novamente", primary = true, onClick = onRetry)
                    }
                    current == null -> CircularProgressIndicator(color = AppColors.accent)
                    seasons.isEmpty() -> Text("Esta série não tem episódios no servidor.", style = AppType.body, color = AppColors.textMuted)
                    else -> {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 6.dp)) {
                            items(seasons) { s ->
                                PillButton("Temporada $s", primary = s == season, onFocused = { season = s }) { season = s }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(vertical = 6.dp, horizontal = 4.dp),
                        ) {
                            itemsIndexed(episodes, key = { _, e -> e.id }) { i, e ->
                                EpisodeRow(
                                    episode = e,
                                    fallback = series.cover,
                                    modifier = if (i == 0) Modifier.focusRequester(firstEpisode) else Modifier,
                                    onClick = { onPlay(episodes, i) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(episode: Episode, fallback: String?, modifier: Modifier, onClick: () -> Unit) {
    FocusBox(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        background = AppColors.glass.copy(alpha = 0.85f),
        focusScale = 1.02f,
        onClick = onClick,
    ) { _ ->
        Row(
            Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier
                    .width(130.dp)
                    .height(74.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(AppColors.surfaceRaised),
            ) {
                val img = episode.image ?: fallback
                if (img != null) AsyncImage(model = img, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Text("▶", color = AppColors.text, modifier = Modifier.align(Alignment.Center))
            }
            Column(Modifier.weight(1f)) {
                Text("Episódio ${episode.number}", style = AppType.caption, color = AppColors.accent)
                Text(episode.title, style = AppType.label, color = AppColors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                episode.plot?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = AppType.caption, color = AppColors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            episode.duration?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = AppType.caption, color = AppColors.textFaint, modifier = Modifier.size(width = 70.dp, height = 20.dp))
            }
        }
    }
}
