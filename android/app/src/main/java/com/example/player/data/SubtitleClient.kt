package com.example.player.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

/** One subtitle found online. */
data class SubtitleResult(val fileId: Long, val label: String, val language: String, val downloads: Int)

class SubtitleException(message: String) : Exception(message)

/**
 * Portuguese subtitles from OpenSubtitles.com, with the user's own free API key
 * (and optionally their account, which raises the daily download limit).
 */
class SubtitleClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private var token: String? = null
    private var tokenUser: String? = null
    private var downloadBase = API

    suspend fun search(query: SubtitleQuery, apiKey: String): List<SubtitleResult> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) throw SubtitleException("Coloque a chave do OpenSubtitles em Ajustes para buscar legendas.")
        val (name, cleanYear) = MetadataClient.cleanTitle(query.title)
        val year = query.year ?: cleanYear
        val isEpisode = query.season != null && query.episode != null
        // By TMDB id first (exact), then by name.
        val attempts = buildList {
            if (query.tmdbId != null) add(params(query, isEpisode, byId = true, name = name, year = year))
            add(params(query, isEpisode, byId = false, name = name, year = year))
        }
        for (p in attempts) {
            val url = "$API/subtitles".toHttpUrl().newBuilder().apply {
                // The API wants the parameters lowercase and in alphabetical order.
                p.toSortedMap().forEach { (k, v) -> addQueryParameter(k, v) }
            }.build().toString()
            val body = call(Request.Builder().url(url).get(), apiKey)
            val results = parseResults(body)
            if (results.isNotEmpty()) return@withContext results
        }
        emptyList()
    }

    private fun params(q: SubtitleQuery, isEpisode: Boolean, byId: Boolean, name: String, year: String?): Map<String, String> = buildMap {
        put("languages", "pt-br,pt-pt")
        put("order_by", "download_count")
        if (isEpisode) {
            put("type", "episode")
            put("season_number", q.season.toString())
            put("episode_number", q.episode.toString())
            if (byId) put("parent_tmdb_id", q.tmdbId!!) else put("query", name.lowercase())
        } else {
            put("type", "movie")
            if (byId) put("tmdb_id", q.tmdbId!!) else {
                put("query", name.lowercase())
                if (year != null) put("year", year)
            }
        }
    }

    private fun parseResults(body: String): List<SubtitleResult> {
        val data = (json.parseToJsonElement(body) as? JsonObject)?.get("data") as? JsonArray ?: return emptyList()
        return data.mapNotNull { item ->
            val a = (item as? JsonObject)?.get("attributes") as? JsonObject ?: return@mapNotNull null
            val file = (a["files"] as? JsonArray)?.firstOrNull() as? JsonObject ?: return@mapNotNull null
            val id = file.str("file_id")?.toLongOrNull() ?: return@mapNotNull null
            val lang = a.str("language").orEmpty()
            val release = a.str("release")?.takeIf { it.isNotBlank() } ?: file.str("file_name").orEmpty()
            SubtitleResult(
                fileId = id,
                label = release.take(70),
                language = if (lang.equals("pt-br", true)) "Português (BR)" else "Português (PT)",
                downloads = a.str("download_count")?.toIntOrNull() ?: 0,
            )
        }.distinctBy { it.fileId }.take(12)
    }

    /** Downloads the .srt into [dir] and returns the file. */
    suspend fun download(fileId: Long, apiKey: String, user: String, password: String, dir: File, name: String): File =
        withContext(Dispatchers.IO) {
            if (user.isNotBlank() && password.isNotBlank() && (token == null || tokenUser != user)) login(apiKey, user, password)
            val payload = buildJsonObject { put("file_id", fileId); put("sub_format", "srt") }.toString()
            val req = Request.Builder().url("$downloadBase/download")
                .post(payload.toRequestBody(JSON))
                .apply { token?.let { header("Authorization", "Bearer $it") } }
            val body = call(req, apiKey)
            val link = (json.parseToJsonElement(body) as? JsonObject)?.str("link")
                ?: throw SubtitleException("O OpenSubtitles não liberou o download desta legenda.")
            val bytes = http.newCall(Request.Builder().url(link).header("User-Agent", AGENT).build()).execute().use { r ->
                if (!r.isSuccessful) throw SubtitleException("Falha ao baixar a legenda (${r.code}).")
                r.body?.bytes() ?: throw SubtitleException("Legenda vazia.")
            }
            dir.mkdirs()
            File(dir, name).apply { writeText(decode(bytes)) }
        }

    private fun login(apiKey: String, user: String, password: String) {
        val payload = buildJsonObject { put("username", user); put("password", password) }.toString()
        val body = call(Request.Builder().url("$API/login").post(payload.toRequestBody(JSON)), apiKey)
        val o = json.parseToJsonElement(body) as? JsonObject
        token = o?.str("token") ?: throw SubtitleException("Usuário ou senha do OpenSubtitles incorretos.")
        tokenUser = user
        downloadBase = o.str("base_url")?.takeIf { it.isNotBlank() }?.let { "https://$it/api/v1" } ?: API
    }

    private fun call(builder: Request.Builder, apiKey: String): String {
        val request = builder
            .header("Api-Key", apiKey.trim())
            .header("User-Agent", AGENT)
            .header("Accept", "application/json")
            .build()
        http.newCall(request).execute().use { r ->
            val text = r.body?.string().orEmpty()
            if (r.isSuccessful) return text
            val serverMessage = runCatching { (json.parseToJsonElement(text) as? JsonObject)?.str("message") }.getOrNull()
            throw SubtitleException(
                when (r.code) {
                    401 -> if (request.url.encodedPath.endsWith("/login")) "Usuário ou senha do OpenSubtitles incorretos." else "Chave do OpenSubtitles inválida. Confira em Ajustes."
                    403 -> "Chave do OpenSubtitles recusada. Confira em Ajustes."
                    406, 429 -> "Limite diário de downloads do OpenSubtitles atingido. Entre com sua conta em Ajustes ou tente amanhã."
                    else -> serverMessage ?: "OpenSubtitles indisponível (${r.code})."
                }
            )
        }
    }

    /** Most subtitles are UTF-8; older Brazilian ones are often Windows-1252. */
    private fun decode(bytes: ByteArray): String {
        val utf8 = String(bytes, Charsets.UTF_8)
        return if (utf8.contains('�')) String(bytes, charset("windows-1252")) else utf8.removePrefix("﻿")
    }

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private companion object {
        const val API = "https://api.opensubtitles.com/api/v1"
        const val AGENT = "PlayerTV v0.5"
        val JSON = "application/json".toMediaType()
    }
}
