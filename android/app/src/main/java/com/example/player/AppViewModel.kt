package com.example.player

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.player.data.Channel
import com.example.player.data.Content
import com.example.player.data.IptvClient
import com.example.player.data.LoginException
import com.example.player.data.Profile
import com.example.player.data.ProfileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Screen {
    data object Splash : Screen
    data object Login : Screen
    data object Loading : Screen
    data object Home : Screen
    data class Live(val categoryId: String? = null) : Screen
    data class Player(val channels: List<Channel>, val index: Int, val back: Screen) : Screen
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = ProfileStore(app)
    val client = IptvClient()

    var screen by mutableStateOf<Screen>(Screen.Splash)
        private set
    var profile by mutableStateOf<Profile?>(null)
        private set
    var content by mutableStateOf<Content?>(null)
        private set
    var loadingStep by mutableStateOf("")
        private set
    var loginError by mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) { store.load() }
            if (saved == null) {
                screen = Screen.Login
                return@launch
            }
            profile = saved
            val cached = withContext(Dispatchers.IO) { store.loadContent() }
            if (cached != null) {
                content = cached
                screen = Screen.Home
            } else {
                load(saved, isNewLogin = false)
            }
        }
    }

    fun login(newProfile: Profile) {
        loginError = null
        viewModelScope.launch { load(newProfile, isNewLogin = true) }
    }

    fun refresh() {
        val p = profile ?: return
        viewModelScope.launch { load(p, isNewLogin = false) }
    }

    fun logout() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.clear() }
            profile = null
            content = null
            screen = Screen.Login
        }
    }

    fun navigate(to: Screen) {
        screen = to
    }

    fun play(channels: List<Channel>, index: Int) {
        screen = Screen.Player(channels, index, back = screen)
    }

    fun liveUrl(channel: Channel): String = profile?.let { client.liveUrl(it, channel) }.orEmpty()

    private suspend fun load(p: Profile, isNewLogin: Boolean) {
        screen = Screen.Loading
        loadingStep = "Conectando"
        try {
            val fetched = client.fetch(p) { step -> viewModelScope.launch { loadingStep = step } }
            withContext(Dispatchers.IO) {
                store.save(p)
                store.saveContent(fetched)
            }
            profile = p
            content = fetched
            screen = Screen.Home
        } catch (e: LoginException) {
            onLoadFailed(e.message ?: "Erro ao carregar.", isNewLogin)
        } catch (e: Exception) {
            onLoadFailed("Erro inesperado: ${e.message ?: e.javaClass.simpleName}", isNewLogin)
        }
    }

    private fun onLoadFailed(message: String, isNewLogin: Boolean) {
        loginError = message
        // A failed refresh keeps the old list usable.
        screen = if (!isNewLogin && content != null) Screen.Home else Screen.Login
    }
}
