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

const val USER_AGENT = "Player/0.1 (Android)"

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

    fun liveUrl(profile: Profile, channel: Channel): String =
        if (profile.isXtream) {
            "${profile.server}/live/${enc(profile.username)}/${enc(profile.password)}/${channel.id}.ts"
        } else {
            channel.url.orEmpty()
        }

    // ---- Xtream Codes ----

    private fun fetchXtream(profile: Profile, onStep: (String) -> Unit): Content {
        val base = "${profile.server}/player_api.php?username=${enc(profile.username)}&password=${enc(profile.password)}"

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

        onStep("Baixando categorias")
        val categories = (getJson("$base&action=get_live_categories") as? JsonArray).orEmpty()
            .mapNotNull { it as? JsonObject }
            .mapNotNull { o ->
                val id = o.str("category_id") ?: return@mapNotNull null
                Category(id = id, name = o.str("category_name") ?: "Categoria $id")
            }

        onStep("Baixando canais")
        val channels = (getJson("$base&action=get_live_streams") as? JsonArray).orEmpty()
            .mapNotNull { it as? JsonObject }
            .mapNotNull { o ->
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
        if (channels.isEmpty()) throw LoginException("Login feito, mas o servidor não enviou nenhum canal.")

        return Content(categories, channels, account, System.currentTimeMillis())
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
        val groups = LinkedHashMap<String, Category>()
        try {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw LoginException("O servidor respondeu com erro HTTP ${response.code}.")
                val body = response.body ?: throw LoginException("Lista vazia.")
                onStep("Lendo canais")
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
                                groups.getOrPut(group) { Category(group, group) }
                                channels += Channel(
                                    id = channels.size.toString(),
                                    number = attrs["tvg-chno"]?.toIntOrNull() ?: (channels.size + 1),
                                    name = pendingName.ifEmpty { attrs["tvg-name"] ?: "Canal ${channels.size + 1}" },
                                    logo = attrs["tvg-logo"]?.takeIf { it.startsWith("http") },
                                    categoryId = group,
                                    epgId = attrs["tvg-id"],
                                    url = line,
                                )
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
        if (channels.isEmpty()) throw LoginException("A lista M3U não tem nenhum canal.")
        return Content(groups.values.toList(), channels, null, System.currentTimeMillis())
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
