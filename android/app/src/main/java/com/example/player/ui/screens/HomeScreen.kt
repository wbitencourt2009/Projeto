package com.example.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.player.data.Content
import com.example.player.data.Kind
import com.example.player.ui.theme.AppColors
import com.example.player.ui.theme.AppType
import com.example.player.ui.theme.FocusBox
import com.example.player.ui.theme.InitialsTile
import com.example.player.ui.theme.PillButton
import java.text.SimpleDateFormat
import java.util.Date

/**
 * The hub: three large glass tiles (Live, Movies, Series) with a collage of the
 * provider's own artwork, a top bar with clock and search, and side actions.
 */
@Composable
fun HomeScreen(
    content: Content,
    username: String,
    message: String?,
    onOpen: (Kind) -> Unit,
    onSearch: () -> Unit,
    onGuide: () -> Unit,
    onFavourites: () -> Unit,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
    restoreKind: Kind? = null,
) {
    val now = rememberNow()
    val liveTile = remember { FocusRequester() }
    val movieTile = remember { FocusRequester() }
    val seriesTile = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        val target = when (restoreKind) {
            Kind.MOVIE -> movieTile
            Kind.SERIES -> seriesTile
            else -> liveTile
        }
        runCatching { target.requestFocus() }
    }

    Box(Modifier.fillMaxSize()) {
        SpaceBackground()
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 40.dp, vertical = 22.dp),
        ) {
            // Top bar
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("◉", fontSize = 30.sp, color = AppColors.accent)
                Text("PLAYER", style = AppType.title.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 3.sp), color = AppColors.text)
                Spacer(Modifier.weight(1f))
                Text(formatTime(now), style = AppType.title, color = AppColors.text)
                Text(formatDate(now), style = AppType.caption, color = AppColors.textMuted)
                PillButton("⌕  Busca geral", onClick = onSearch)
                PillButton("⟳", onClick = onRefresh)
            }

            Spacer(Modifier.height(18.dp))

            Row(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(22.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.weight(0.15f))
                HubTile(
                    label = "AO VIVO",
                    count = "${formatCount(content.channels.size)} canais",
                    images = remember(content) { content.channels.mapNotNull { it.logo }.take(9) },
                    names = remember(content) { content.channels.take(9).map { it.name } },
                    logos = true,
                    modifier = Modifier.focusRequester(liveTile),
                    onClick = { onOpen(Kind.LIVE) },
                )
                HubTile(
                    label = "FILMES",
                    count = "${formatCount(content.movies.size)} filmes",
                    images = remember(content) { content.movies.mapNotNull { it.poster }.take(9) },
                    names = remember(content) { content.movies.take(9).map { it.name } },
                    logos = false,
                    modifier = Modifier.focusRequester(movieTile),
                    onClick = { onOpen(Kind.MOVIE) },
                )
                HubTile(
                    label = "SÉRIES",
                    count = "${formatCount(content.series.size)} séries",
                    images = remember(content) { content.series.mapNotNull { it.cover }.take(9) },
                    names = remember(content) { content.series.take(9).map { it.name } },
                    logos = false,
                    modifier = Modifier.focusRequester(seriesTile),
                    onClick = { onOpen(Kind.SERIES) },
                )
                Spacer(Modifier.weight(0.05f))
                Column(
                    Modifier.width(190.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SideButton("☰  Guia de TV", onGuide)
                    SideButton("♥  Favoritos", onFavourites)
                    SideButton("⟳  Atualizar lista", onRefresh)
                    SideButton("⏻  Sair", onLogout)
                }
            }

            if (message != null) {
                Text(message, style = AppType.caption, color = AppColors.warning, modifier = Modifier.padding(top = 8.dp))
            }

            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val exp = content.account?.expDate
                Text(
                    if (exp != null) "Expira em: " + SimpleDateFormat("dd/MM/yyyy", ptBR).format(Date(exp * 1000)) else "Assinatura sem data de expiração",
                    style = AppType.caption,
                    color = AppColors.textMuted,
                )
                Spacer(Modifier.weight(1f))
                Text("Conectado: ", style = AppType.caption, color = AppColors.textMuted)
                Text(username, style = AppType.caption.copy(fontWeight = FontWeight.SemiBold), color = AppColors.text)
            }
        }
    }
}

@Composable
private fun RowScope.HubTile(
    label: String,
    count: String,
    images: List<String>,
    names: List<String>,
    logos: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    FocusBox(
        modifier = modifier
            .weight(1f)
            .fillMaxHeight(0.86f),
        shape = RoundedCornerShape(22.dp),
        background = AppColors.glass.copy(alpha = 0.55f),
        focusScale = 1.06f,
        onClick = onClick,
    ) { focused ->
        Collage(images, names, logos, Modifier.fillMaxSize().padding(10.dp).clip(RoundedCornerShape(16.dp)))
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.45f to AppColors.bg.copy(alpha = 0.25f),
                        1f to AppColors.bg.copy(alpha = 0.92f),
                    )
                ),
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                label,
                style = AppType.headline.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp),
                color = if (focused) AppColors.focus else AppColors.text,
                textAlign = TextAlign.Center,
            )
            Text(count, style = AppType.caption, color = AppColors.textMuted)
        }
    }
}

/** 3 x 3 grid of the provider's posters or channel logos. */
@Composable
private fun Collage(images: List<String>, names: List<String>, logos: Boolean, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (r in 0 until 3) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (c in 0 until 3) {
                    val i = r * 3 + c
                    val cell = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(6.dp))
                        .background(AppColors.surfaceRaised)
                    val url = images.getOrNull(i)
                    Box(cell, contentAlignment = Alignment.Center) {
                        if (url != null) {
                            AsyncImage(
                                model = url,
                                contentDescription = null,
                                contentScale = if (logos) ContentScale.Fit else ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(if (logos) 8.dp else 0.dp),
                            )
                        } else {
                            InitialsTile(names.getOrNull(i) ?: "", 40.dp, Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SideButton(text: String, onClick: () -> Unit) {
    FocusBox(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        background = AppColors.glass.copy(alpha = 0.8f),
        focusScale = 1.05f,
        onClick = onClick,
    ) { focused ->
        Text(
            text,
            style = AppType.label,
            color = if (focused) AppColors.focus else AppColors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}
