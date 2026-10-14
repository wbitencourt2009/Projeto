package com.example.player.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.player.data.Kind
import com.example.player.data.Tile
import com.example.player.ui.theme.AppColors
import com.example.player.ui.theme.AppType
import com.example.player.ui.theme.FocusBox
import com.example.player.ui.theme.InitialsTile
import kotlinx.coroutines.delay
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val ptBR: Locale = Locale("pt", "BR")

/**
 * Futuristic backdrop shared by the hub and the lists: deep space blue with
 * large diagonal cyan and violet light planes.
 */
@Composable
fun SpaceBackground(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize().background(AppColors.bg)) {
        val w = size.width
        val h = size.height
        drawRect(Brush.linearGradient(listOf(Color(0xFF0B1430), Color(0xFF120B2A), AppColors.bg), Offset.Zero, Offset(w, h)))
        val plane1 = Path().apply {
            moveTo(w * 0.42f, 0f); lineTo(w * 0.78f, 0f); lineTo(w * 0.40f, h); lineTo(w * 0.04f, h); close()
        }
        drawPath(plane1, Brush.linearGradient(listOf(AppColors.accent.copy(alpha = 0.16f), Color.Transparent), Offset(w * 0.6f, 0f), Offset(w * 0.2f, h)))
        val plane2 = Path().apply {
            moveTo(w * 0.70f, 0f); lineTo(w, 0f); lineTo(w, h * 0.55f); lineTo(w * 0.52f, h); lineTo(w * 0.36f, h); close()
        }
        drawPath(plane2, Brush.linearGradient(listOf(AppColors.accent2.copy(alpha = 0.30f), Color(0xFF5B3FD1).copy(alpha = 0.10f)), Offset(w, 0f), Offset(w * 0.4f, h)))
        val plane3 = Path().apply {
            moveTo(w * 0.86f, h * 0.35f); lineTo(w, h * 0.15f); lineTo(w, h); lineTo(w * 0.62f, h); close()
        }
        drawPath(plane3, Brush.linearGradient(listOf(AppColors.accent.copy(alpha = 0.26f), Color.Transparent), Offset(w, h * 0.3f), Offset(w * 0.6f, h)))
        drawCircle(
            Brush.radialGradient(listOf(AppColors.accent.copy(alpha = 0.18f), Color.Transparent), Offset(w * 0.15f, h * 0.1f), w * 0.3f),
            radius = w * 0.3f,
            center = Offset(w * 0.15f, h * 0.1f),
        )
    }
}

@Composable
fun rememberNow(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            now = System.currentTimeMillis()
        }
    }
    return now
}

fun formatTime(ms: Long): String = SimpleDateFormat("HH:mm", ptBR).format(Date(ms))
fun formatDate(ms: Long): String = SimpleDateFormat("EEE, d 'de' MMMM", ptBR).format(Date(ms))
    .replaceFirstChar { it.titlecase(ptBR) }

fun formatCount(n: Int): String = String.format(ptBR, "%,d", n)

fun normalise(s: String): String =
    Normalizer.normalize(s.lowercase(ptBR), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

/** Poster (movies, series) or logo card (channels) with its title underneath. */
@Composable
fun TileCard(
    tile: Tile,
    favourite: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val isLive = tile.kind == Kind.LIVE
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FocusBox(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(if (isLive) 16f / 11f else 2f / 3f),
            shape = RoundedCornerShape(12.dp),
            background = if (isLive) AppColors.glass else AppColors.surfaceRaised,
            focusScale = 1.07f,
            onLongClick = onLongClick,
            onClick = onClick,
        ) { _ ->
            if (tile.image != null) {
                AsyncImage(
                    model = tile.image,
                    contentDescription = tile.title,
                    contentScale = if (isLive) ContentScale.Fit else ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(if (isLive) 14.dp else 0.dp),
                )
            } else {
                InitialsTile(tile.title, 64.dp, Modifier.fillMaxSize())
            }
            if (isLive && tile.number != null) {
                Text(
                    tile.number.toString(),
                    style = AppType.caption,
                    color = AppColors.textFaint,
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                )
            }
            if (favourite) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(26.dp)
                        .clip(RoundedCornerShape(50))
                        .background(AppColors.bg.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("♥", color = AppColors.accent2, fontSize = 14.sp)
                }
            }
        }
        Text(
            tile.title,
            style = AppType.caption.copy(fontWeight = FontWeight.Medium),
            color = AppColors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (isLive) TextAlign.Center else TextAlign.Start,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun EmptyMessage(title: String, line: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = AppType.title, color = AppColors.text, textAlign = TextAlign.Center)
        Text(line, style = AppType.body, color = AppColors.textMuted, textAlign = TextAlign.Center)
    }
}
