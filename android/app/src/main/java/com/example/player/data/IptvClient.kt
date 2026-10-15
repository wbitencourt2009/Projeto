package com.example.player.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromStream
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

const val USER_AGENT = "Player/0.2 (Android)"

/** Client for the Xtream Codes player API and for M3U playlists. */
class IptvClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun fetch(profile: Profile, onStep: (String) -> Unit): Content = withContext(Dispatchers.IO) {
        if (profile.isXtream) fetchXtream(profile, onStep) else fetchM3u(profile, onStep)
    }

    fun liveUrl(profile: Profile, channel: Channel, ext: String = "ts"): String =
        if (profile.isXtream) "${profile.server}/live/${auth(profile)}/${channel.id}.$ext" else channel.url.orEmpty()

    /** HLS variant of a live channel, tried when the .ts stream keeps failing. Xtream only. */
    fun liveFallbackUrl(profile: Profile, channel: Channel): String? =
        if (profile.isXtream) liveUrl(profile, channel, "m3u8") else null

    fun movieUrl(profile: Profile, movie: Movie): String =
        if (profile.isXtream) "${profile.server}/movie/${auth(profile)}/${movie.id}.${movie.ext ?: "mp4"}" else movie.url.orEmpty()

    fun episodeUrl(profile: Profile, episode: Episode): String =
        "${profile.server}/series/${auth(profile)}/${episode.id}.${episode.ext ?: "mp4"}"

    suspend fun seriesDetail(profile: Profile, series: Series): SeriesDetail = withContext(Dispatchers.IO) {
        if (!profile.isXtream) return@withContext SeriesDetail(series, series.plot, series.cover, emptyMap())
        val root = getJson("${base(profile)}&action=get_series_info&series_id=${series.id}") as? JsonObject
            ?: throw LoginException("O servidor não enviou os episódios desta série.")
        val info = root["info"] as? JsonObject
        val backdrop = (info?.get("backdrop_path") as? JsonArray)?.firstOrNull()?.let { (it as? JsonPrimitive)?.contentOrNull }
            ?: info?.str("cover")
        val raw = root["episodes"]
        val lists: List<JsonElement> = when (raw) {
            is JsonObject -> raw.values.toList()
            is JsonArray -> raw.toList()
            else -> emptyList()
        }
        val episodes = lists.flatMap { (it as? JsonArray).orEmpty() }
            .mapNotNull { it as? JsonObject }
            .mapNotNull { e ->
                val id = e.str("id") ?: return@mapNotNull null
                val epInfo = e["info"] as? JsonObject
                Episode(
                    id = id,
                    season = e.str("season")?.toIntOrNull() ?: 1,
                    number = e.str("episode_num")?.toIntOrNull() ?: 0,
                    title = e.str("title")?.trim().orEmpty().ifEmpty { "Episódio" },
                    ext = e.str("container_extension"),
                    image = epInfo?.str("movie_image")?.takeIf { it.startsWith("http") },
                    plot = epInfo?.str("plot"),
                    duration = epInfo?.str("duration"),
                )
            }
            .sortedWith(compareBy({ it.season }, { it.number }))
            .groupBy { it.season }
        SeriesDetail(series, info?.str("plot") ?: series.plot, backdrop?.takeIf { it.startsWith("http") } ?: series.cover, episodes)
    }

    /** Full guide of one channel (all days the server keeps). Xtream only. */
    suspend fun epg(profile: Profile, channel: Channel): List<Programme> = withContext(Dispatchers.IO) {
        if (!profile.isXtream) return@withContext emptyList()
        val root = getJson("${base(profile)}&action=get_simple_data_table&stream_id=${channel.id}") as? JsonObject
            ?: return@withContext emptyList()
        objects(root["epg_listings"] ?: JsonArray(emptyList()))
            .mapNotNull { o ->
                val start = o.str("start_timestamp")?.toLongOrNull() ?: return@mapNotNull null
                val end = o.str("stop_timestamp")?.toLongOrNull() ?: o.str("end_timestamp")?.toLongOrNull() ?: return@mapNotNull null
                if (end <= start) return@mapNotNull null
                Programme(
                    start = start * 1000,
                    end = end * 1000,
                    title = decode64(o.str("title")).ifBlank { "Sem título" },
                    description = decode64(o.str("description")).takeIf { it.isNotBlank() },
                    hasArchive = o.str("has_archive") == "1",
                )
            }
            .distinctBy { it.start }
            .sortedBy { it.start }
    }

    /** Xtream sends EPG text in base64; some panels send plain text. */
    private fun decode64(value: String?): String {
        if (value.isNullOrBlank()) return ""
        return try {
            val bytes = android.util.Base64.decode(value, android.util.Base64.DEFAULT)
            val text = String(bytes, Charsets.UTF_8)
            if (text.any { it == '\uFFFD' || (it.isISOControl() && it != '\n' && it != '\r' && it != '\t') }) value else text.trim()
        } catch (e: IllegalArgumentException) {
            value
        }
    }

    // ---- Xtream Codes ----

    private fun base(profile: Profile) =
        "${profile.server}/player_api.php?username=${enc(profile.username)}&password=${enc(profile.password)}"

    private fun auth(profile: Profile) = "${enc(profile.username)}/${enc(profile.password)}"

    private fun fetchXtream(profile: Profile, onStep: (String) -> Unit): Content {
        val base = base(profile)

        onStep("Conectando ao servidor")
        val auth = getJson(base) as? JsonObject
            ?: throw LoginException("O servidor não respondeu como um painel Xtream Codes. Confira o endereço.")
        val user = auth["user_info"] as? JsonObject
            ?: throw LoginException("O servidor não respondeu como um painel Xtream Codes. Confira o endereço.")
        if (user.str("auth") == "0") throw LoginException("Usuário ou senha incorretos.")
        val status = user.str("status")
        if (status != null && !status.equals("Active", ignoreCase = true)) {
            throw LoginException("Conta com status \"$status\" no servidor. Fale com o seu provedor.")
        }
        val account = AccountInfo(
            status = status,
            expDate = user.str("exp_date")?.toLongOrNull(),
            maxConnections = user.str("max_connections")?.toIntOrNull(),
        )

        onStep("Baixando canais")
        val categories = categories(getJson("$base&action=get_live_categories"))
        val channels = objects(getJson("$base&action=get_live_streams")).mapNotNull { o ->
            val id = o.str("stream_id") ?: return@mapNotNull null
            Channel(
                id = id,
                number = o.str("num")?.toDoubleOrNull()?.toInt(),
                name = o.str("name")?.trim().orEmpty().ifEmpty { "Canal $id" },
                logo = o.str("stream_icon")?.takeIf { it.startsWith("http") },
                categoryId = o.str("category_id"),
                epgId = o.str("epg_channel_id"),
            )
        }

        // Providers without VOD return errors or empty lists here; that is fine.
        onStep("Baixando filmes")
        val movieCategories = optional { categories(getJson("$base&action=get_vod_categories")) }
        val movies = optional {
            objects(getJson("$base&action=get_vod_streams")).mapNotNull { o ->
                val id = o.str("stream_id") ?: return@mapNotNull null
                Movie(
                    id = id,
                    name = o.str("name")?.trim().orEmpty().ifEmpty { "Filme $id" },
                    poster = o.str("stream_icon")?.takeIf { it.startsWith("http") },
                    categoryId = o.str("category_id"),
                    rating = o.str("rating")?.takeIf { it.isNotBlank() && it != "0" },
                    ext = o.str("container_extension"),
                )
            }
        }

        onStep("Baixando séries")
        val seriesCategories = optional { categories(getJson("$base&action=get_series_categories")) }
        val series = optional {
            objects(getJson("$base&action=get_series")).mapNotNull { o ->
                val id = o.str("series_id") ?: return@mapNotNull null
                Series(
                    id = id,
                    name = o.str("name")?.trim().orEmpty().ifEmpty { "Série $id" },
                    cover = o.str("cover")?.takeIf { it.startsWith("http") },
                    categoryId = o.str("category_id"),
                    rating = o.str("rating")?.takeIf { it.isNotBlank() && it != "0" },
                    plot = o.str("plot"),
                )
            }
        }

        if (channels.isEmpty() && movies.isEmpty() && series.isEmpty()) {
            throw LoginException("Login feito, mas o servidor não enviou nenhum conteúdo.")
        }
        return Content(
            categories = categories,
            channels = channels,
            account = account,
            fetchedAt = System.currentTimeMillis(),
            movieCategories = movieCategories,
            movies = movies,
            seriesCategories = seriesCategories,
            series = series,
        )
    }

    private fun categories(element: JsonElement): List<Category> = objects(element).mapNotNull { o ->
        val id = o.str("category_id") ?: return@mapNotNull null
        Category(id = id, name = o.str("category_name")?.trim().orEmpty().ifEmpty { "Categoria $id" })
    }

    private fun objects(element: JsonElement): List<JsonObject> =
        (element as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }

    private fun <T> optional(block: () -> List<T>): List<T> =
        try {
            block()
        } catch (e: Exception) {
            emptyList()
        }

    @OptIn(ExperimentalSerializationApi::class)
    private fun getJson(url: String): JsonElement {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        return try {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw LoginException("O servidor respondeu com erro HTTP ${response.code}.")
                val body = response.body ?: throw LoginException("Resposta vazia do servidor.")
                json.decodeFromStream(JsonElement.serializer(), body.byteStream())
            }
        } catch (e: LoginException) {
            throw e
        } catch (e: IOException) {
            throw LoginException("Não foi possível conectar ao servidor (${e.message ?: "erro de rede"}).")
        } catch (e: Exception) {
            throw LoginException("Resposta inválida do servidor. Confira o endereço.")
        }
    }

    // ---- M3U ----

    private fun fetchM3u(profile: Profile, onStep: (String) -> Unit): Content {
        onStep("Baixando lista M3U")
        val url = profile.m3uUrl ?: throw LoginException("Informe o link da lista M3U.")
        val request = try {
            Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        } catch (e: IllegalArgumentException) {
            throw LoginException("Link da lista M3U inválido.")
        }
        val channels = ArrayList<Channel>()
        val movies = ArrayList<Movie>()
        val series = ArrayList<Series>()
        val liveGroups = LinkedHashMap<String, Category>()
        val movieGroups = LinkedHashMap<String, Category>()
        val seriesGroups = LinkedHashMap<String, Category>()
        try {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw LoginException("O servidor respondeu com erro HTTP ${response.code}.")
                val body = response.body ?: throw LoginException("Lista vazia.")
                onStep("Lendo a lista")
                var pending: Map<String, String>? = null
                var pendingName = ""
                body.charStream().buffered().useLines { lines ->
                    for (raw in lines) {
                        val line = raw.trim()
                        when {
                            line.startsWith("#EXTINF", ignoreCase = true) -> {
                                pending = parseAttributes(line)
                                pendingName = line.substringAfterLast(',', "").trim()
                            }
                            line.isEmpty() || line.startsWith("#") -> Unit
                            pending != null -> {
                                val attrs = pending!!
                                val group = attrs["group-title"]?.takeIf { it.isNotBlank() } ?: "Outros"
                                val name = pendingName.ifEmpty { attrs["tvg-name"] ?: "Item" }
                                val logo = attrs["tvg-logo"]?.takeIf { it.startsWith("http") }
                                when {
                                    line.contains("/movie/") -> {
                                        movieGroups.getOrPut(group) { Category(group, group) }
                                        movies += Movie(id = "m${movies.size}", name = name, poster = logo, categoryId = group, url = line)
                                    }
                                    line.contains("/series/") -> {
                                        seriesGroups.getOrPut(group) { Category(group, group) }
                                        series += Series(id = "s${series.size}", name = name, cover = logo, categoryId = group, url = line)
                                    }
                                    else -> {
                                        liveGroups.getOrPut(group) { Category(group, group) }
                                        channels += Channel(
                                            id = channels.size.toString(),
                                            number = attrs["tvg-chno"]?.toIntOrNull() ?: (channels.size + 1),
                                            name = name,
                                            logo = logo,
                                            categoryId = group,
                                            epgId = attrs["tvg-id"],
                                            url = line,
                                        )
                                    }
                                }
                                pending = null
                            }
                        }
                    }
                }
            }
        } catch (e: LoginException) {
            throw e
        } catch (e: IOException) {
            throw LoginException("Não foi possível baixar a lista (${e.message ?: "erro de rede"}).")
        }
        if (channels.isEmpty() && movies.isEmpty() && series.isEmpty()) throw LoginException("A lista M3U está vazia.")
        return Content(
            categories = liveGroups.values.toList(),
            channels = channels,
            account = null,
            fetchedAt = System.currentTimeMillis(),
            movieCategories = movieGroups.values.toList(),
            movies = movies,
            seriesCategories = seriesGroups.values.toList(),
            series = series,
        )
    }

    private val attrRegex = Regex("""([\w-]+)="([^"]*)"""")

    private fun parseAttributes(line: String): Map<String, String> =
        attrRegex.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2] }

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private fun enc(value: String?): String = URLEncoder.encode(value.orEmpty(), "UTF-8")
}

/** Accepts "host:port", "http://host:port/", ".../player_api.php" or a get.php link. */
fun normaliseServer(input: String): String {
    var s = input.trim()
    if (!s.startsWith("http://", true) && !s.startsWith("https://", true)) s = "http://$s"
    s = s.substringBefore("/player_api.php").substringBefore("/get.php").substringBefore("/xmltv.php")
    return s.trimEnd('/')
}
