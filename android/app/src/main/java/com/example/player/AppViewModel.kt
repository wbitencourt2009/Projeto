package com.example.player

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.player.data.Channel
import com.example.player.data.Content
import com.example.player.data.Episode
import com.example.player.data.IptvClient
import com.example.player.data.Kind
import com.example.player.data.Movie
import com.example.player.data.MovieDetail
import com.example.player.data.Library
import com.example.player.data.LoginException
import com.example.player.data.PlayItem
import com.example.player.data.Profile
import com.example.player.data.Programme
import com.example.player.data.ProfileStore
import com.example.player.data.Series
import com.example.player.data.SeriesDetail
import com.example.player.data.Tile
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Screen {
    data object Splash : Screen
    data object Login : Screen
    data object Loading : Screen
    /** Saved login, but the list could not be downloaded (server down / bad signal). */
    data class Offline(val message: String) : Screen
    data object Home : Screen
    data class Browse(val kind: Kind, val categoryId: String = BrowseIds.ALL) : Screen
    data class SeriesInfo(val series: Series) : Screen
    data class MovieInfo(val movie: Movie) : Screen
    data object Search : Screen
    data class Guide(val categoryId: String = BrowseIds.ALL, val channelId: String? = null) : Screen
    data class Player(val items: List<PlayItem>, val index: Int, val live: Boolean) : Screen
}

object BrowseIds {
    const val ALL = "__all__"
    const val RECENT = "__recent__"
    const val FAVOURITES = "__fav__"
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = ProfileStore(app)
    private val library = Library(app)
    val client = IptvClient()

    private var stack by mutableStateOf<List<Screen>>(listOf(Screen.Splash))
    val screen: Screen get() = stack.last()

    var profile by mutableStateOf<Profile?>(null)
        private set
    var content by mutableStateOf<Content?>(null)
        private set
    var loadingStep by mutableStateOf("")
        private set
    var loginError by mutableStateOf<String?>(null)
        private set
    /** Last login typed, used to refill the form after an error. */
    var lastAttempt by mutableStateOf<Profile?>(null)
        private set
    var favourites by mutableStateOf(library.favourites())
        private set
    var recent by mutableStateOf(library.recent())
        private set
    var seriesDetail by mutableStateOf<SeriesDetail?>(null)
        private set
    var seriesError by mutableStateOf<String?>(null)
        private set
    var movieDetail by mutableStateOf<MovieDetail?>(null)
        private set

    init {
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) { store.load() }
            if (saved == null) {
                reset(Screen.Login)
                return@launch
            }
            profile = saved
            lastAttempt = saved
            val cached = withContext(Dispatchers.IO) { store.loadContent() }
            if (cached != null) {
                content = cached
                reset(Screen.Home)
            } else {
                load(saved, isNewLogin = false)
            }
        }
    }

    // ---- navigation ----

    /** Last focused item per list, so Back from the player lands where the user was. Not observed. */
    private val lastFocused = HashMap<Kind, String>()
    fun rememberFocus(kind: Kind, key: String) { lastFocused[kind] = key }
    fun lastFocus(kind: Kind): String? = lastFocused[kind]

    /** Hub tile used last, refocused when coming back to the home screen. */
    var lastHubKind: Kind? = null

    fun push(to: Screen) {
        stack = stack + to
        lastNavigationAt = android.os.SystemClock.uptimeMillis()
    }

    /** A second Back pressed while the previous screen is still closing must not skip a screen. */
    private var lastNavigationAt = 0L

    /** Replaces the top screen, e.g. to remember the selected category. */
    fun replaceTop(to: Screen) {
        stack = stack.dropLast(1) + to
    }

    /** Returns false when there is nothing to go back to (the app should close). */
    fun back(): Boolean {
        if (stack.size <= 1) return false
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastNavigationAt < BACK_DEBOUNCE_MS) return true
        lastNavigationAt = now
        // Leaving a list for good (not the player on top of it): forget its focus.
        (stack.last() as? Screen.Browse)?.let { lastFocused.remove(it.kind) }
        stack = stack.dropLast(1)
        return true
    }

    private fun reset(to: Screen) {
        stack = listOf(to)
    }

    // ---- account ----

    fun login(newProfile: Profile) {
        loginError = null
        lastAttempt = newProfile
        viewModelScope.launch { load(newProfile, isNewLogin = true) }
    }

    fun refresh() {
        val p = profile ?: return
        viewModelScope.launch { load(p, isNewLogin = false) }
    }

    /** From the offline screen: edit the saved login without deleting favourites. */
    fun changeLogin() {
        loginError = null
        reset(Screen.Login)
    }

    fun logout() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                store.clear()
                library.clear()
            }
            profile = null
            content = null
            searchIndex = null
            searchQuery = ""
            favourites = emptyList()
            recent = emptyList()
            reset(Screen.Login)
        }
    }

    private suspend fun load(p: Profile, isNewLogin: Boolean) {
        reset(Screen.Loading)
        loadingStep = "Conectando"
        try {
            val fetched = client.fetch(p) { step -> viewModelScope.launch { loadingStep = step } }
            withContext(Dispatchers.IO) {
                store.save(p)
                store.saveContent(fetched)
            }
            profile = p
            content = fetched
            searchIndex = null
            epgCache.clear()
            loginError = null
            reset(Screen.Home)
        } catch (e: LoginException) {
            onLoadFailed(e.message ?: "Erro ao carregar.", isNewLogin)
        } catch (e: Exception) {
            onLoadFailed("Erro inesperado: ${e.message ?: e.javaClass.simpleName}", isNewLogin)
        }
    }

    private fun onLoadFailed(message: String, isNewLogin: Boolean) {
        loginError = message
        reset(
            when {
                // A failed refresh keeps the old list usable.
                !isNewLogin && content != null -> Screen.Home
                // Saved login but nothing cached yet: keep the login and offer to try again.
                !isNewLogin && profile != null -> Screen.Offline(message)
                else -> Screen.Login
            }
        )
    }

    // ---- library ----

    fun isFavourite(key: String) = key in favourites

    fun toggleFavourite(key: String) {
        favourites = if (key in favourites) favourites - key else favourites + key
        library.saveFavourites(favourites)
    }

    fun removeRecent(key: String) {
        recent = recent - key
        library.saveRecent(recent)
    }

    fun clearRecent(kind: Kind) {
        val prefix = kind.name.lowercase() + ":"
        recent = recent.filterNot { it.startsWith(prefix) }
        library.saveRecent(recent)
    }

    // ---- search ----

    /** Text typed in the global search, kept when coming back from the player. */
    var searchQuery by mutableStateOf("")

    private class SearchIndex(val byKind: Map<Kind, Pair<Array<String>, List<Tile>>>)
    private var searchIndex: Deferred<SearchIndex>? = null

    /** Normalised names are computed once per list, in the background (100k+ titles on big providers). */
    private fun searchIndex(): Deferred<SearchIndex> = searchIndex ?: viewModelScope.async(Dispatchers.Default) {
        SearchIndex(Kind.entries.associateWith { kind ->
            val list = tiles(kind)
            Array(list.size) { com.example.player.ui.screens.normalise(list[it].title) } to list
        })
    }.also { searchIndex = it }

    data class SearchResult(val kind: Kind, val total: Int, val items: List<Tile>)

    /** Runs off the main thread; at most [limit] items per kind are returned. */
    suspend fun search(query: String, limit: Int = 40): List<SearchResult> = withContext(Dispatchers.Default) {
        val q = com.example.player.ui.screens.normalise(query.trim())
        if (q.length < 2) return@withContext emptyList()
        val index = searchIndex().await()
        listOf(Kind.LIVE, Kind.MOVIE, Kind.SERIES).mapNotNull { kind ->
            val (names, list) = index.byKind[kind] ?: return@mapNotNull null
            var total = 0
            val found = ArrayList<Tile>(limit)
            for (i in names.indices) {
                if (i % 2048 == 0) ensureActive()
                if (names[i].contains(q)) {
                    total++
                    if (found.size < limit) found += list[i]
                }
            }
            if (total == 0) null else SearchResult(kind, total, found)
        }
    }

    // ---- EPG ----

    private val epgCache = HashMap<String, List<Programme>>()

    val supportsEpg: Boolean get() = profile?.isXtream == true

    suspend fun loadEpg(channelId: String): List<Programme> {
        epgCache[channelId]?.let { return it }
        val p = profile ?: return emptyList()
        val ch = content?.channels?.firstOrNull { it.id == channelId } ?: return emptyList()
        val list = try {
            client.epg(p, ch)
        } catch (e: Exception) {
            emptyList()
        }
        epgCache[channelId] = list
        return list
    }

    /** Channels for the guide: a category, favourites, recent, or all. */
    fun channelsFor(categoryId: String): List<Channel> {
        val c = content ?: return emptyList()
        return when (categoryId) {
            BrowseIds.ALL -> c.channels
            BrowseIds.FAVOURITES -> favourites.filter { it.startsWith("live:") }
                .mapNotNull { k -> c.channels.firstOrNull { it.id == k.removePrefix("live:") } }
            BrowseIds.RECENT -> recent.filter { it.startsWith("live:") }
                .mapNotNull { k -> c.channels.firstOrNull { it.id == k.removePrefix("live:") } }
            else -> c.channelsByCategory[categoryId].orEmpty()
        }
    }

    fun playChannel(channels: List<Channel>, channelId: String) {
        val tiles = channels.map { Tile(Kind.LIVE, it.id, it.name, it.logo, it.categoryId, it.number) }
        val tile = tiles.firstOrNull { it.id == channelId } ?: return
        open(tile, tiles)
    }

    private fun markWatched(key: String) {
        recent = (listOf(key) + (recent - key)).take(Library.MAX_RECENT)
        library.saveRecent(recent)
    }

    // ---- items ----

    fun tiles(kind: Kind): List<Tile> {
        val c = content ?: return emptyList()
        return when (kind) {
            Kind.LIVE -> c.channels.map { com.example.player.data.Tile(Kind.LIVE, it.id, it.name, it.logo, it.categoryId, it.number) }
            Kind.MOVIE -> c.movies.map { Tile(Kind.MOVIE, it.id, it.name, it.poster, it.categoryId) }
            Kind.SERIES -> c.series.map { Tile(Kind.SERIES, it.id, it.name, it.cover, it.categoryId) }
        }
    }

    /** Opens a tile: plays a channel or movie, opens a series. [list] is the grid it came from. */
    fun open(tile: Tile, list: List<Tile>) {
        val c = content ?: return
        val p = profile ?: return
        when (tile.kind) {
            Kind.LIVE -> {
                val channels = list.filter { it.kind == Kind.LIVE }
                val byId = c.channels.associateBy { it.id }
                val items = channels.mapNotNull { t ->
                    byId[t.id]?.let { ch ->
                        PlayItem(t.key, ch.name, subtitle = null, image = ch.logo, url = client.liveUrl(p, ch), number = ch.number, fallbackUrl = client.liveFallbackUrl(p, ch))
                    }
                }
                val index = items.indexOfFirst { it.key == tile.key }.coerceAtLeast(0)
                markWatched(tile.key)
                push(Screen.Player(items, index, live = true))
            }
            Kind.MOVIE -> {
                val movie = c.movies.firstOrNull { it.id == tile.id } ?: return
                loadMovie(movie)
                push(Screen.MovieInfo(movie))
            }
            Kind.SERIES -> {
                val series = c.series.firstOrNull { it.id == tile.id } ?: return
                markWatched(tile.key)
                if (series.url != null) {
                    push(Screen.Player(listOf(PlayItem(tile.key, series.name, image = series.cover, url = series.url)), 0, live = false))
                } else {
                    loadSeries(series)
                    push(Screen.SeriesInfo(series))
                }
            }
        }
    }

    fun onLivePlayed(key: String) = markWatched(key)

    private fun loadSeries(series: Series) {
        val p = profile ?: return
        seriesDetail = null
        seriesError = null
        viewModelScope.launch {
            try {
                seriesDetail = client.seriesDetail(p, series)
            } catch (e: LoginException) {
                seriesError = e.message
            } catch (e: Exception) {
                seriesError = "Não foi possível carregar os episódios."
            }
        }
    }

    fun retrySeries(series: Series) = loadSeries(series)

    private fun loadMovie(movie: Movie) {
        val p = profile ?: return
        movieDetail = null
        viewModelScope.launch {
            // Details are optional: on failure the page still shows the poster and Watch.
            movieDetail = runCatching { client.movieDetail(p, movie) }
                .getOrElse { MovieDetail(movie, com.example.player.data.MediaInfo(backdrop = movie.poster, rating = movie.rating)) }
        }
    }

    fun playMovie(movie: Movie) {
        val p = profile ?: return
        val key = Tile.keyOf(Kind.MOVIE, movie.id)
        markWatched(key)
        push(Screen.Player(listOf(PlayItem(key, movie.name, image = movie.poster, url = client.movieUrl(p, movie))), 0, live = false))
    }

    fun playEpisodes(episodes: List<Episode>, index: Int, series: Series) {
        val p = profile ?: return
        val items = episodes.map { e ->
            PlayItem(
                key = "episode:${e.id}",
                title = "T${e.season} E${e.number} · ${e.title}",
                subtitle = series.name,
                image = e.image ?: series.cover,
                url = client.episodeUrl(p, e),
            )
        }
        push(Screen.Player(items, index, live = false))
    }
}

private const val BACK_DEBOUNCE_MS = 700L
