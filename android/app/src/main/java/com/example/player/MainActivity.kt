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
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.player.ui.screens.HomeScreen
import com.example.player.ui.screens.LiveScreen
import com.example.player.ui.screens.LoadingScreen
import com.example.player.ui.screens.LoginScreen
import com.example.player.ui.screens.PlayerScreen
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
    when (val s = vm.screen) {
        Screen.Splash -> LoadingScreen("Abrindo")
        Screen.Login -> LoginScreen(error = vm.loginError, onLogin = vm::login)
        Screen.Loading -> LoadingScreen(vm.loadingStep)
        Screen.Home -> {
            if (content == null) {
                LoadingScreen("Abrindo")
            } else {
                BackHandler(onBack = onExit)
                HomeScreen(
                    content = content,
                    profileName = vm.profile?.name.orEmpty(),
                    message = vm.loginError,
                    onPlay = vm::play,
                    onOpenLive = { vm.navigate(Screen.Live()) },
                    onRefresh = vm::refresh,
                    onLogout = vm::logout,
                )
            }
        }
        is Screen.Live -> {
            if (content != null) {
                BackHandler { vm.navigate(Screen.Home) }
                LiveScreen(
                    content = content,
                    initialCategoryId = s.categoryId,
                    onCategoryChange = { vm.navigate(Screen.Live(it)) },
                    onPlay = vm::play,
                )
            }
        }
        is Screen.Player -> {
            BackHandler { vm.navigate(s.back) }
            PlayerScreen(channels = s.channels, startIndex = s.index, urlFor = vm::liveUrl)
        }
    }
}
