package com.example.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.player.data.Kind
import com.example.player.data.Tile
import com.example.player.ui.screens.BrowseScreen
import com.example.player.ui.screens.GuideScreen
import com.example.player.ui.screens.HomeScreen
import com.example.player.ui.screens.LoadingScreen
import com.example.player.ui.screens.LoginScreen
import com.example.player.ui.screens.OfflineScreen
import com.example.player.ui.screens.PlayerScreen
import com.example.player.ui.screens.SearchScreen
import com.example.player.ui.screens.SeriesScreen
import com.example.player.ui.theme.AppColors

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = AppColors.accent,
                    onPrimary = AppColors.onAccent,
                    secondary = AppColors.accent2,
                    background = AppColors.bg,
                    surface = AppColors.surface,
                    onSurface = AppColors.text,
                    error = AppColors.danger,
                ),
            ) {
                Box(Modifier.fillMaxSize().background(AppColors.bg)) {
                    App(vm, onExit = { finish() })
                }
            }
        }
    }
}

@Composable
private fun App(vm: AppViewModel, onExit: () -> Unit) {
    val content = vm.content
    BackHandler { if (!vm.back()) onExit() }

    when (val s = vm.screen) {
        Screen.Splash -> LoadingScreen("Abrindo")
        Screen.Login -> LoginScreen(error = vm.loginError, initial = vm.lastAttempt, onLogin = vm::login)
        is Screen.Offline -> OfflineScreen(message = s.message, onRetry = vm::refresh, onChangeLogin = vm::changeLogin)
        Screen.Loading -> LoadingScreen(vm.loadingStep)
        Screen.Home -> {
            if (content == null) {
                LoadingScreen("Abrindo")
            } else {
                HomeScreen(
                    content = content,
                    username = vm.profile?.username ?: vm.profile?.name.orEmpty(),
                    message = vm.loginError,
                    onOpen = { kind ->
                        vm.lastHubKind = kind
                        vm.push(Screen.Browse(kind))
                    },
                    onSearch = { vm.push(Screen.Search) },
                    onGuide = { vm.push(Screen.Guide()) },
                    onFavourites = { vm.push(Screen.Browse(Kind.LIVE, BrowseIds.FAVOURITES)) },
                    onRefresh = vm::refresh,
                    onLogout = vm::logout,
                    restoreKind = vm.lastHubKind,
                )
            }
        }
        is Screen.Browse -> {
            if (content != null) {
                val tiles = remember(content, s.kind) { vm.tiles(s.kind) }
                BrowseScreen(
                    kind = s.kind,
                    selectedId = s.categoryId,
                    content = content,
                    tiles = tiles,
                    favourites = vm.favourites,
                    recent = vm.recent,
                    onSelect = { id -> if (id != s.categoryId) vm.replaceTop(s.copy(categoryId = id)) },
                    onOpen = vm::open,
                    onToggleFavourite = vm::toggleFavourite,
                    onRemoveRecent = vm::removeRecent,
                    onClearRecent = { vm.clearRecent(s.kind) },
                    onGuide = if (s.kind == Kind.LIVE) ({ vm.push(Screen.Guide(s.categoryId)) }) else null,
                    onBack = { vm.back() },
                    restoreKey = vm.lastFocus(s.kind),
                    onTileFocused = { key -> vm.rememberFocus(s.kind, key) },
                )
            }
        }
        is Screen.SeriesInfo -> SeriesScreen(
            series = s.series,
            detail = vm.seriesDetail,
            error = vm.seriesError,
            favourite = vm.isFavourite(Tile.keyOf(Kind.SERIES, s.series.id)),
            onToggleFavourite = { vm.toggleFavourite(Tile.keyOf(Kind.SERIES, s.series.id)) },
            onRetry = { vm.retrySeries(s.series) },
            onPlay = { episodes, i -> vm.playEpisodes(episodes, i, s.series) },
        )
        Screen.Search -> {
            if (content != null) {
                val all = remember(content) { Kind.entries.associateWith { vm.tiles(it) } }
                SearchScreen(
                    all = all,
                    favourites = vm.favourites,
                    onOpen = vm::open,
                    onToggleFavourite = vm::toggleFavourite,
                )
            }
        }
        is Screen.Guide -> {
            val channels = remember(content, s.categoryId) { vm.channelsFor(s.categoryId) }
            GuideScreen(
                channels = channels,
                initialChannelId = s.channelId,
                supported = vm.supportsEpg,
                loadEpg = vm::loadEpg,
                onChannelFocused = { id -> vm.replaceTop(s.copy(channelId = id)) },
                onPlay = { id -> vm.playChannel(channels, id) },
            )
        }
        is Screen.Player -> PlayerScreen(
            items = s.items,
            startIndex = s.index,
            live = s.live,
            onItemShown = { if (s.live) vm.onLivePlayed(it.key) },
            loadEpg = { item -> if (item.key.startsWith("live:")) vm.loadEpg(item.key.removePrefix("live:")) else emptyList() },
        )
    }
}
