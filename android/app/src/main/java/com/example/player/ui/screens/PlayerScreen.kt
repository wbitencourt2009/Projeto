package com.example.player.ui.screens

import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.player.data.PlayItem
import com.example.player.data.USER_AGENT
import com.example.player.ui.theme.AppColors
import com.example.player.ui.theme.AppType
import com.example.player.ui.theme.InitialsTile
import com.example.player.ui.theme.LiveBadge
import com.example.player.ui.theme.PillButton
import kotlinx.coroutines.delay

/**
 * Fullscreen player. Live: our own overlay, ▲ ▼ zap through [items].
 * On demand (movies, episodes): the standard Media3 controller with seek,
 * and the next item starts when one ends.
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    items: List<PlayItem>,
    startIndex: Int,
    live: Boolean,
    onItemShown: (PlayItem) -> Unit,
    loadEpg: suspend (PlayItem) -> List<com.example.player.data.Programme> = { emptyList() },
) {
    var programmes by remember { mutableStateOf<List<com.example.player.data.Programme>>(emptyList()) }
    val context = LocalContext.current
    var index by remember { mutableIntStateOf(startIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))) }
    var overlay by remember { mutableStateOf(true) }
    var buffering by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    val focus = remember { FocusRequester() }
    val shown by rememberUpdatedState(onItemShown)

    val player = remember {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(http))
            .build()
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) error = null
                if (state == Player.STATE_ENDED && !live && index < items.size - 1) index++
            }

            override fun onPlayerError(e: PlaybackException) {
                buffering = false
                error = when (e.errorCode) {
                    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "O servidor recusou este conteúdo (pode estar fora do ar ou no limite de conexões)."
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "Sem conexão com o servidor."
                    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED -> "Formato de vídeo não suportado por este aparelho."
                    else -> "Não foi possível reproduzir (${e.errorCodeName})."
                }
                overlay = true
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    val item = items.getOrNull(index)

    LaunchedEffect(index, attempt) {
        val it = items.getOrNull(index) ?: return@LaunchedEffect
        val media = MediaItem.Builder().setUri(it.url).apply {
            if (it.url.contains(".m3u8", ignoreCase = true)) setMimeType(MimeTypes.APPLICATION_M3U8)
        }.build()
        error = null
        buffering = true
        player.setMediaItem(media)
        player.prepare()
        player.playWhenReady = true
        overlay = true
        shown(it)
        if (live) {
            programmes = emptyList()
            programmes = loadEpg(it)
        }
    }

    LaunchedEffect(overlay, index, error) {
        if (live && overlay && error == null) {
            delay(4_000)
            overlay = false
        }
    }

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    fun zap(delta: Int) {
        if (items.isEmpty()) return
        index = (index + delta + items.size) % items.size
    }

    val keys = if (live) {
        Modifier
            .focusRequester(focus)
            .focusable()
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.key) {
                    Key.DirectionUp, Key.ChannelUp, Key.PageUp -> { zap(-1); true }
                    Key.DirectionDown, Key.ChannelDown, Key.PageDown -> { zap(1); true }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        if (error != null) attempt++ else overlay = !overlay
                        true
                    }
                    else -> false
                }
            }
            .pointerInput(Unit) { detectTapGestures(onTap = { overlay = !overlay }) }
    } else {
        Modifier
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .then(keys),
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = !live
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    keepScreenOn = true
                    setShowNextButton(!live)
                    setShowPreviousButton(!live)
                    this.player = player
                    if (!live) {
                        isFocusable = true
                        isFocusableInTouchMode = true
                        post { requestFocus() }
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (buffering && error == null) {
            CircularProgressIndicator(
                color = AppColors.accent,
                strokeWidth = 4.dp,
                modifier = Modifier.align(Alignment.Center).size(64.dp),
            )
        }

        error?.let { msg ->
            Column(
                Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(20.dp))
                    .background(AppColors.glass.copy(alpha = 0.95f))
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(msg, style = AppType.title, color = AppColors.text)
                Text(
                    if (live) "OK tenta de novo · ▲ ▼ trocam de canal · Voltar sai" else "Voltar para sair",
                    style = AppType.body,
                    color = AppColors.textMuted,
                )
                PillButton("Tentar novamente", primary = true) { attempt++ }
            }
        }

        if (live) {
            AnimatedVisibility(
                visible = overlay && item != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                if (item != null) LiveInfoBar(item, index, items.size, programmes, onPrev = { zap(-1) }, onNext = { zap(1) })
            }
        } else if (item != null) {
            AnimatedVisibility(
                visible = overlay,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopStart),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(AppColors.bg.copy(alpha = 0.85f), Color.Transparent)))
                        .padding(horizontal = 40.dp, vertical = 24.dp),
                ) {
                    item.subtitle?.let { Text(it, style = AppType.caption, color = AppColors.accent) }
                    Text(item.title, style = AppType.title, color = AppColors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            LaunchedEffect(index) {
                delay(5_000)
                overlay = false
            }
        }
    }
}

@Composable
private fun LiveInfoBar(
    item: PlayItem,
    index: Int,
    total: Int,
    programmes: List<com.example.player.data.Programme>,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    val now = rememberNow()
    val current = programmes.firstOrNull { it.isNow(now) }
    val next = programmes.firstOrNull { it.start >= (current?.end ?: now) }
    Box(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, AppColors.bg.copy(alpha = 0.95f))))
            .padding(horizontal = 40.dp, vertical = 28.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            if (item.image != null) {
                AsyncImage(model = item.image, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(84.dp))
            } else {
                InitialsTile(item.title, 84.dp, Modifier.size(84.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LiveBadge()
                    Text(
                        (item.number?.let { "Canal $it  ·  " } ?: "") + "${index + 1} de $total",
                        style = AppType.caption,
                        color = AppColors.accent,
                    )
                }
                Text(item.title, style = AppType.headline, color = AppColors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (current != null) {
                    Text(
                        "Agora: ${formatTime(current.start)} – ${formatTime(current.end)}  ${current.title}",
                        style = AppType.label,
                        color = AppColors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val pct = ((now - current.start).toFloat() / (current.end - current.start)).coerceIn(0f, 1f)
                    Box(
                        Modifier
                            .fillMaxWidth(0.6f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(AppColors.border),
                    ) {
                        Box(Modifier.fillMaxWidth(pct).height(4.dp).background(AppColors.accent))
                    }
                }
                if (next != null) {
                    Text(
                        "A seguir: ${formatTime(next.start)}  ${next.title}",
                        style = AppType.caption,
                        color = AppColors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text("▲ ▼ trocar de canal  ·  OK mostrar/ocultar  ·  Voltar sair", style = AppType.caption, color = AppColors.textMuted)
            }
            Spacer(Modifier.size(8.dp))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(formatTime(now), style = AppType.headline, color = AppColors.text)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton("▲", onClick = onPrev)
                    PillButton("▼", onClick = onNext)
                }
            }
        }
    }
}
