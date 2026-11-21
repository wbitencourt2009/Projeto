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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
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
    subtitles: SubtitleActions? = null,
) {
    var programmes by remember { mutableStateOf<List<com.example.player.data.Programme>>(emptyList()) }
    val context = LocalContext.current
    var index by remember { mutableIntStateOf(startIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))) }
    var overlay by remember { mutableStateOf(true) }
    var buffering by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    // Automatic reconnection when the server signal is bad.
    var retries by remember { mutableIntStateOf(0) }
    var retryPending by remember { mutableStateOf(false) }
    var lastProblem by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val shown by rememberUpdatedState(onItemShown)

    // Subtitles: on/off and text size remembered; a subtitle downloaded for this
    // movie or episode is loaded again automatically.
    val subPrefs = remember { context.getSharedPreferences("player", android.content.Context.MODE_PRIVATE) }
    var subsOn by remember { mutableStateOf(subPrefs.getBoolean("subs_on", true)) }
    var subSize by remember { mutableIntStateOf(subPrefs.getInt("sub_size", 1).coerceIn(0, SUB_SIZES.size - 1)) }
    var textGroups by remember { mutableStateOf<List<Tracks.Group>>(emptyList()) }
    var showSubs by remember { mutableStateOf(false) }
    val preparedIndex = remember { intArrayOf(-1) }
    // "Próximo episódio" card near the end of an episode.
    var remainingMs by remember { mutableLongStateOf(Long.MAX_VALUE) }
    var nextCancelledFor by remember { mutableIntStateOf(-1) }
    var episodeEnded by remember(index) { mutableStateOf(false) }
    var nextCountdown by remember { mutableIntStateOf(NEXT_COUNTDOWN_S) }
    val pickWhenListed = remember { booleanArrayOf(false) }
    var downloadedSub by remember(index) {
        mutableStateOf(if (live) null else items.getOrNull(index)?.let { subtitles?.saved?.invoke(it) })
    }

    val player = remember {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
        // Bigger buffer than the default so short drops in the server signal don't stop the picture.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(20_000, 60_000, 2_000, 4_000)
            .build()
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(http))
            .setLoadControl(loadControl)
            .build()
            .apply {
                videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    .setPreferredTextLanguages("pt-BR", "pt", "por")
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !context.getSharedPreferences("player", android.content.Context.MODE_PRIVATE).getBoolean("subs_on", true))
                    .build()
            }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    error = null
                    retries = 0
                    retryPending = false
                }
                if (state == Player.STATE_ENDED && !live && index < items.size - 1) {
                    // Card cancelled: wait for the viewer on the card; otherwise go on.
                    if (nextCancelledFor == index) {
                        nextCancelledFor = -1
                        episodeEnded = true
                    } else {
                        retries = 0
                        retryPending = false
                        index++
                    }
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                textGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
                if (pickWhenListed[0] && textGroups.isNotEmpty()) {
                    pickWhenListed[0] = false
                    if (textGroups.none { it.isSelected }) {
                        val g = textGroups.firstOrNull { g -> (0 until g.length).any { g.getTrackFormat(it).language?.startsWith("pt", true) == true } } ?: textGroups.first()
                        val i = (0 until g.length).firstOrNull { g.getTrackFormat(it).language?.startsWith("pt", true) == true } ?: 0
                        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                            .setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, i))
                            .build()
                    }
                }
            }

            override fun onPlayerError(e: PlaybackException) {
                if (e.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    // Live stream fell behind (slow connection): jump back to the live edge.
                    player.seekToDefaultPosition()
                    player.prepare()
                    return
                }
                val message = when (e.errorCode) {
                    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "O servidor recusou este conteúdo (pode estar fora do ar ou no limite de conexões)."
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "Sem conexão com o servidor."
                    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED -> "Formato de vídeo não suportado por este aparelho."
                    else -> "Não foi possível reproduzir (${e.errorCodeName})."
                }
                if (retries < MAX_RETRIES) {
                    lastProblem = message
                    retryPending = true
                } else {
                    buffering = false
                    error = message
                    overlay = true
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            // Releasing blocks the main thread for a moment on slow sticks; stop now so the
            // sound ends at once, and release after the previous screen has been drawn.
            player.playWhenReady = false
            player.stop()
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ player.release() }, 300)
        }
    }

    val item = items.getOrNull(index)

    // Retry with growing pauses; live channels alternate between .ts and .m3u8.
    LaunchedEffect(retryPending) {
        if (!retryPending) return@LaunchedEffect
        delay(1_500L * (retries + 1))
        retries++
        retryPending = false
        attempt++
    }

    // Stuck loading for too long counts as a failure and reconnects.
    LaunchedEffect(buffering, attempt, index) {
        if (!buffering || error != null) return@LaunchedEffect
        delay(STALL_TIMEOUT_MS)
        if (buffering && !retryPending && error == null) {
            if (retries < MAX_RETRIES) {
                lastProblem = "O servidor está lento."
                retryPending = true
            } else {
                error = "O servidor não está respondendo. Tente de novo em instantes ou escolha outro canal."
                buffering = false
                overlay = true
            }
        }
    }

    LaunchedEffect(index, attempt) {
        val it = items.getOrNull(index) ?: return@LaunchedEffect
        val url = if (retries % 2 == 1 && it.fallbackUrl != null) it.fallbackUrl else it.url
        val media = mediaItemOf(url, downloadedSub)
        error = null
        buffering = true
        // A retry keeps the place in the movie.
        val resumeAt = if (!live && preparedIndex[0] == index) player.currentPosition else 0L
        preparedIndex[0] = index
        if (resumeAt > 0) player.setMediaItem(media, resumeAt) else player.setMediaItem(media)
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

    // Screen format (fit / stretch / zoom), remembered between videos.
    val prefs = remember { context.getSharedPreferences("player", android.content.Context.MODE_PRIVATE) }
    var aspect by remember { mutableIntStateOf(prefs.getInt("aspect", 0).coerceIn(0, ASPECT_MODES.size - 1)) }
    var aspectLabelVisible by remember { mutableStateOf(false) }
    fun cycleAspect() {
        aspect = (aspect + 1) % ASPECT_MODES.size
        prefs.edit().putInt("aspect", aspect).apply()
        aspectLabelVisible = true
    }
    LaunchedEffect(aspect, aspectLabelVisible) {
        if (aspectLabelVisible) {
            delay(2_500)
            aspectLabelVisible = false
        }
    }

    // Movie / episode controls: our own, driven by the remote (the stock controller
    // can't receive D-pad focus inside Compose on Android TV sticks).
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var paused by remember { mutableStateOf(false) }
    var lastInteraction by remember { mutableLongStateOf(0L) }
    LaunchedEffect(overlay, index) {
        while (!live && overlay) {
            position = player.currentPosition
            duration = player.duration.takeIf { it > 0 } ?: 0L
            paused = !player.playWhenReady
            delay(500)
        }
    }
    LaunchedEffect(overlay, lastInteraction, paused) {
        if (!live && overlay && !paused) {
            delay(5_000)
            overlay = false
        }
    }
    fun showControls() {
        overlay = true
        lastInteraction = System.nanoTime()
    }
    fun togglePause() {
        player.playWhenReady = !player.playWhenReady
        paused = !player.playWhenReady
        showControls()
    }
    fun seekBy(ms: Long) {
        val end = player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
        val target = (player.currentPosition + ms).coerceIn(0L, end)
        player.seekTo(target)
        position = target
        showControls()
    }

    fun setSubtitlesOn(on: Boolean) {
        subsOn = on
        subPrefs.edit().putBoolean("subs_on", on).apply()
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !on)
            .apply { if (!on) clearOverridesOfType(C.TRACK_TYPE_TEXT) }
            .build()
    }

    fun selectTextTrack(group: Tracks.Group, track: Int) {
        subsOn = true
        subPrefs.edit().putBoolean("subs_on", true).apply()
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, track))
            .build()
    }

    /** Turning back on: Portuguese first, then the downloaded one, then the first the video has. */
    fun turnSubtitlesOn() {
        val all = textGroups.flatMap { g -> (0 until g.length).map { g to it } }
        val best = all.firstOrNull { (g, i) -> g.getTrackFormat(i).language?.startsWith("pt", ignoreCase = true) == true }
            ?: all.firstOrNull { (g, i) -> g.getTrackFormat(i).label == DOWNLOADED_LABEL }
            ?: all.firstOrNull()
        if (best != null) {
            selectTextTrack(best.first, best.second)
        } else {
            // The tracks are listed again once the text renderer is back on: choose then.
            pickWhenListed[0] = true
            setSubtitlesOn(true)
        }
    }

    /** Reloads the current video with (or without) the downloaded subtitle, at the same position. */
    fun reloadWithSubtitle(file: java.io.File?) {
        val current = items.getOrNull(index) ?: return
        downloadedSub = file
        if (file != null) {
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_TEXT).build()
        }
        val pos = player.currentPosition
        player.setMediaItem(mediaItemOf(current.url, file), pos)
        player.prepare()
        player.playWhenReady = true
    }

    val nextItem = if (!live) items.getOrNull(index + 1) else null
    val showNextCard = nextItem != null && error == null && !showSubs &&
        (episodeEnded || (nextCancelledFor != index && remainingMs in 1..NEXT_CARD_MS))

    fun playNext() {
        if (index >= items.size - 1) return
        retries = 0
        retryPending = false
        remainingMs = Long.MAX_VALUE
        index++
    }

    fun cancelNext() {
        nextCancelledFor = index
        episodeEnded = false
    }

    // Time left in the episode, checked even while the controls are hidden.
    LaunchedEffect(index, live) {
        remainingMs = Long.MAX_VALUE
        while (!live && index < items.size - 1) {
            val d = player.duration
            remainingMs = if (d > 0) (d - player.currentPosition).coerceAtLeast(0) else Long.MAX_VALUE
            delay(1_000)
        }
    }

    // Countdown on the card; pausing stops it.
    LaunchedEffect(showNextCard, index) {
        nextCountdown = NEXT_COUNTDOWN_S
        if (!showNextCard) return@LaunchedEffect
        while (nextCountdown > 0) {
            delay(1_000)
            if (player.playWhenReady || episodeEnded) nextCountdown--
        }
        playNext()
    }

    fun zap(delta: Int) {
        if (items.isEmpty()) return
        retries = 0
        retryPending = false
        index = (index + delta + items.size) % items.size
    }

    val keys = Modifier
        .focusRequester(focus)
        .focusable()
        .onKeyEvent { e ->
            if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
            val held = e.nativeKeyEvent.repeatCount > 0
            val ok = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
            if (!live && e.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_CAPTIONS) {
                showSubs = true
                return@onKeyEvent true
            }
            if (error != null && ok) {
                retries = 0
                attempt++
                return@onKeyEvent true
            }
            if (!live && e.key == Key.MediaNext) {
                playNext()
                return@onKeyEvent true
            }
            if (showNextCard) {
                when {
                    ok -> { playNext(); return@onKeyEvent true }
                    e.key == Key.Back || e.key == Key.Escape -> { cancelNext(); return@onKeyEvent true }
                }
            }
            if (live) {
                when (e.key) {
                    Key.DirectionUp, Key.ChannelUp, Key.PageUp -> { zap(-1); true }
                    Key.DirectionDown, Key.ChannelDown, Key.PageDown -> { zap(1); true }
                    Key.DirectionLeft, Key.DirectionRight, Key.Menu -> { cycleAspect(); true }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { overlay = !overlay; true }
                    else -> false
                }
            } else {
                when (e.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.MediaPlayPause, Key.Spacebar -> { togglePause(); true }
                    Key.MediaPlay -> { player.playWhenReady = true; paused = false; showControls(); true }
                    Key.MediaPause -> { player.playWhenReady = false; paused = true; showControls(); true }
                    // Hold the key to jump faster.
                    Key.DirectionLeft, Key.MediaRewind -> { seekBy(if (held) -30_000L else -10_000L); true }
                    Key.DirectionRight, Key.MediaFastForward -> { seekBy(if (held) 30_000L else 10_000L); true }
                    Key.DirectionUp, Key.Menu -> { cycleAspect(); showControls(); true }
                    Key.DirectionDown -> { showControls(); showSubs = true; true }
                    else -> false
                }
            }
        }
        .pointerInput(live) {
            detectTapGestures(onTap = {
                if (live) overlay = !overlay else if (overlay) togglePause() else showControls()
            })
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
                    useController = false
                    keepScreenOn = true
                    this.player = player
                }
            },
            update = { view ->
                view.resizeMode = ASPECT_MODES[aspect].second
                view.subtitleView?.apply {
                    setApplyEmbeddedStyles(false)
                    setApplyEmbeddedFontSizes(false)
                    setStyle(
                        CaptionStyleCompat(
                            android.graphics.Color.WHITE,
                            0x66000000,
                            android.graphics.Color.TRANSPARENT,
                            CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                            android.graphics.Color.BLACK,
                            null,
                        ),
                    )
                    setFractionalTextSize(SUB_SIZES[subSize].second)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        if ((buffering || retryPending) && error == null) {
            Column(
                Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                CircularProgressIndicator(color = AppColors.accent, strokeWidth = 4.dp, modifier = Modifier.size(64.dp))
                if (retries > 0 || retryPending) {
                    Column(
                        Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(AppColors.glass.copy(alpha = 0.85f))
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("Sinal fraco — reconectando (${retries + 1}/$MAX_RETRIES)", style = AppType.label, color = AppColors.text)
                        if (lastProblem.isNotEmpty()) Text(lastProblem, style = AppType.caption, color = AppColors.textMuted)
                    }
                }
            }
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
                    if (live) "OK tenta de novo · ▲ ▼ trocam de canal · Voltar sai" else "OK tenta de novo · Voltar sai",
                    style = AppType.body,
                    color = AppColors.textMuted,
                )
                PillButton("Tentar novamente", primary = true) { retries = 0; attempt++ }
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
            if (paused && error == null && !buffering) {
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size(110.dp)
                        .clip(RoundedCornerShape(50))
                        .background(AppColors.glass.copy(alpha = 0.8f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("❚❚", style = AppType.headline, color = AppColors.text)
                }
            }
            AnimatedVisibility(
                visible = overlay,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                VodControls(
                    item = item,
                    position = position,
                    duration = duration,
                    paused = paused,
                    aspectName = ASPECT_MODES[aspect].first,
                    onBack = { seekBy(-10_000L) },
                    onPlayPause = { togglePause() },
                    onForward = { seekBy(10_000L) },
                    subtitlesLabel = if (!subsOn) "Desligada" else currentSubtitleName(textGroups) ?: "Ligada",
                    onSubtitles = { showSubs = true },
                    onNext = if (nextItem != null) ({ playNext() }) else null,
                )
            }
            AnimatedVisibility(
                visible = showNextCard,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 40.dp, bottom = if (overlay) 190.dp else 40.dp),
            ) {
                if (nextItem != null) {
                    NextEpisodeCard(
                        next = nextItem,
                        countdown = nextCountdown,
                        ended = episodeEnded,
                        onPlay = { playNext() },
                        onCancel = { cancelNext() },
                    )
                }
            }
        }

        if (showSubs && !live) {
            val current = items.getOrNull(index)
            val options = textGroups.flatMap { g ->
                (0 until g.length).map { i ->
                    TextTrackOption(trackName(g.getTrackFormat(i)), g.isTrackSelected(i)) { selectTextTrack(g, i) }
                }
            }
            SubtitleDialog(
                enabled = subsOn,
                tracks = options,
                sizeName = SUB_SIZES[subSize].first,
                hasDownloaded = downloadedSub != null,
                canSearch = subtitles != null && current?.subtitleQuery != null,
                onOff = { setSubtitlesOn(false) },
                onOn = { turnSubtitlesOn() },
                onCycleSize = {
                    subSize = (subSize + 1) % SUB_SIZES.size
                    subPrefs.edit().putInt("sub_size", subSize).apply()
                },
                onRemoveDownloaded = {
                    if (current != null) subtitles?.forget?.invoke(current)
                    reloadWithSubtitle(null)
                },
                search = { subtitles!!.search(current!!.subtitleQuery!!) },
                pick = { result ->
                    val file = subtitles!!.download(result, current!!)
                    setSubtitlesOn(true)
                    reloadWithSubtitle(file)
                },
                onDismiss = { showSubs = false },
            )
        }
        LaunchedEffect(showSubs) {
            if (!showSubs) runCatching { focus.requestFocus() }
        }

        AnimatedVisibility(
            visible = aspectLabelVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 32.dp),
        ) {
            Text(
                "Tela: " + ASPECT_MODES[aspect].first,
                style = AppType.label,
                color = AppColors.text,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(AppColors.glass.copy(alpha = 0.9f))
                    .padding(horizontal = 22.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun VodControls(
    item: PlayItem,
    position: Long,
    duration: Long,
    paused: Boolean,
    aspectName: String,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onForward: () -> Unit,
    subtitlesLabel: String,
    onSubtitles: () -> Unit,
    onNext: (() -> Unit)? = null,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, AppColors.bg.copy(alpha = 0.95f))))
            .padding(horizontal = 40.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item.subtitle?.let { Text(it, style = AppType.caption, color = AppColors.accent) }
        Text(item.title, style = AppType.headline, color = AppColors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(clockOf(position), style = AppType.label, color = AppColors.text)
            val pct = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
            Box(
                Modifier
                    .weight(1f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(AppColors.border),
            ) {
                Box(Modifier.fillMaxWidth(pct).height(6.dp).background(AppColors.accent))
            }
            Text(if (duration > 0) clockOf(duration) else "--:--", style = AppType.label, color = AppColors.textMuted)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PillButton("⏪ 10s", onClick = onBack)
            PillButton(if (paused) "▶  Continuar" else "❚❚  Pausar", primary = true, onClick = onPlayPause)
            PillButton("10s ⏩", onClick = onForward)
            PillButton("CC  Legenda: $subtitlesLabel", onClick = onSubtitles)
            if (onNext != null) PillButton("Próximo ⏭", onClick = onNext)
            Spacer(Modifier.weight(1f))
            Text(
                "OK pausar · ◀ ▶ 10 s (segure p/ 30 s) · ▼ legendas · ▲ tela: $aspectName",
                style = AppType.caption,
                color = AppColors.textMuted,
            )
        }
    }
}

private fun clockOf(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

@Composable
private fun NextEpisodeCard(next: PlayItem, countdown: Int, ended: Boolean, onPlay: () -> Unit, onCancel: () -> Unit) {
    Row(
        Modifier
            .width(560.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(AppColors.glass.copy(alpha = 0.95f))
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(180.dp)
                .height(101.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(AppColors.surfaceRaised),
            contentAlignment = Alignment.Center,
        ) {
            if (next.image != null) {
                AsyncImage(model = next.image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Text("▶", style = AppType.headline, color = AppColors.text)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Próximo episódio", style = AppType.caption, color = AppColors.accent)
            Text(next.title, style = AppType.label, color = AppColors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                if (ended) "Começa em $countdown s" else "Começa em $countdown s  ·  OK assistir agora  ·  Voltar cancela",
                style = AppType.caption,
                color = AppColors.textMuted,
                maxLines = 2,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton("▶  Assistir agora", primary = true, onClick = onPlay)
                PillButton("Cancelar", onClick = onCancel)
            }
        }
    }
}

/** Callbacks the player uses to find and keep subtitles (see AppViewModel). */
class SubtitleActions(
    val search: suspend (com.example.player.data.SubtitleQuery) -> List<com.example.player.data.SubtitleResult>,
    val download: suspend (com.example.player.data.SubtitleResult, PlayItem) -> java.io.File,
    val saved: (PlayItem) -> java.io.File?,
    val forget: (PlayItem) -> Unit,
)

private val SUB_SIZES = listOf(
    "Pequeno" to 0.045f,
    "Médio" to 0.0533f,
    "Grande" to 0.068f,
    "Muito grande" to 0.085f,
)

private const val DOWNLOADED_LABEL = "Português (baixada)"

private fun mediaItemOf(url: String, subtitle: java.io.File?): MediaItem =
    MediaItem.Builder().setUri(url).apply {
        if (url.contains(".m3u8", ignoreCase = true)) setMimeType(MimeTypes.APPLICATION_M3U8)
        if (subtitle != null) {
            setSubtitleConfigurations(
                listOf(
                    MediaItem.SubtitleConfiguration.Builder(android.net.Uri.fromFile(subtitle))
                        .setMimeType(MimeTypes.APPLICATION_SUBRIP)
                        .setLanguage("pt-BR")
                        .setLabel(DOWNLOADED_LABEL)
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                        .build(),
                ),
            )
        }
    }.build()

/** "Português", "Inglês (SDH)"… from the track's label or language. */
private fun trackName(format: androidx.media3.common.Format): String {
    format.label?.takeIf { it.isNotBlank() }?.let { return it }
    val lang = format.language?.takeIf { it.isNotBlank() && it != "und" } ?: return "Legenda sem nome"
    val name = java.util.Locale.forLanguageTag(lang).getDisplayLanguage(java.util.Locale("pt", "BR"))
    return name.ifBlank { lang }.replaceFirstChar { it.uppercase() }
}

private fun currentSubtitleName(groups: List<Tracks.Group>): String? {
    for (g in groups) for (i in 0 until g.length) if (g.isTrackSelected(i)) return trackName(g.getTrackFormat(i))
    return null
}

private val ASPECT_MODES = listOf(
    "Ajustar à tela" to AspectRatioFrameLayout.RESIZE_MODE_FIT,
    "Esticar" to AspectRatioFrameLayout.RESIZE_MODE_FILL,
    "Zoom" to AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
)

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

private const val MAX_RETRIES = 5
/** The card shows in the last 40 s of an episode (credits) and starts the next one after 10 s. */
private const val NEXT_CARD_MS = 40_000L
private const val NEXT_COUNTDOWN_S = 10
private const val STALL_TIMEOUT_MS = 20_000L
