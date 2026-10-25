package com.example.player.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Fills in what the IPTV provider did not send (synopsis, trailer, cast...).
 * - TMDB (themoviedb.org) with the user's own free API key: best data, pt-BR.
 * - Without a key: Portuguese Wikipedia summary for the synopsis.
 * Only empty fields are filled; the provider's data always wins.
 */
class MetadataClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun enrich(title: String, isSeries: Boolean, base: MediaInfo, tmdbKey: String?): MediaInfo = withContext(Dispatchers.IO) {
        val (name, year) = cleanTitle(title)
        var info = base
        if (!tmdbKey.isNullOrBlank()) {
            runCatching { tmdb(name, year, isSeries, base.tmdbId, tmdbKey.trim()) }.getOrNull()?.let { info = merge(info, it) }
        }
        if (info.plot.isNullOrBlank()) {
            runCatching { wikipedia(name, year, isSeries) }.getOrNull()?.let { info = merge(info, it) }
        }
        info
    }

    private fun merge(have: MediaInfo, found: MediaInfo) = have.copy(
        plot = have.plot?.takeIf { it.isNotBlank() } ?: found.plot,
        genre = have.genre ?: found.genre,
        cast = have.cast ?: found.cast,
        director = have.director ?: found.director,
        releaseDate = have.releaseDate ?: found.releaseDate,
        duration = have.duration ?: found.duration,
        rating = have.rating ?: found.rating,
        backdrop = have.backdrop ?: found.backdrop,
        trailer = have.trailer ?: found.trailer,
        source = if (have.plot.isNullOrBlank() && !found.plot.isNullOrBlank()) found.source else have.source,
    )

    // ---- TMDB ----

    private fun tmdb(name: String, year: String?, isSeries: Boolean, knownId: String?, key: String): MediaInfo? {
        val type = if (isSeries) "tv" else "movie"
        val id = knownId ?: run {
            val search = "https://api.themoviedb.org/3/search/$type".toHttpUrl().newBuilder()
                .addQueryParameter("api_key", key)
                .addQueryParameter("language", "pt-BR")
                .addQueryParameter("query", name)
                .apply { if (year != null) addQueryParameter(if (isSeries) "first_air_date_year" else "year", year) }
                .build().toString()
            val results = (get(search) as? JsonObject)?.get("results") as? JsonArray
            (results?.firstOrNull() as? JsonObject)?.str("id")
        } ?: return null

        val url = "https://api.themoviedb.org/3/$type/$id".toHttpUrl().newBuilder()
            .addQueryParameter("api_key", key)
            .addQueryParameter("language", "pt-BR")
            .addQueryParameter("append_to_response", "videos,credits")
            .addQueryParameter("include_video_language", "pt,en,null")
            .build().toString()
        val d = get(url) as? JsonObject ?: return null

        val genres = (d["genres"] as? JsonArray)?.mapNotNull { (it as? JsonObject)?.str("name") }?.joinToString(", ")
        val credits = d["credits"] as? JsonObject
        val cast = (credits?.get("cast") as? JsonArray)?.take(6)?.mapNotNull { (it as? JsonObject)?.str("name") }?.joinToString(", ")
        val director = (credits?.get("crew") as? JsonArray)?.mapNotNull { it as? JsonObject }
            ?.filter { it.str("job") == "Director" }?.mapNotNull { it.str("name") }?.joinToString(", ")
            ?: (d["created_by"] as? JsonArray)?.mapNotNull { (it as? JsonObject)?.str("name") }?.joinToString(", ")
        val videos = ((d["videos"] as? JsonObject)?.get("results") as? JsonArray)?.mapNotNull { it as? JsonObject }
            ?.filter { it.str("site") == "YouTube" && it.str("type") in setOf("Trailer", "Teaser") }
            .orEmpty()
        val trailer = (videos.firstOrNull { it.str("iso_639_1") == "pt" && it.str("type") == "Trailer" }
            ?: videos.firstOrNull { it.str("type") == "Trailer" }
            ?: videos.firstOrNull())?.str("key")
        val runtime = d.str("runtime")?.toIntOrNull()
            ?: (d["episode_run_time"] as? JsonArray)?.firstOrNull()?.let { (it as? JsonPrimitive)?.contentOrNull?.toIntOrNull() }
        val rating = d.str("vote_average")?.toDoubleOrNull()?.takeIf { it > 0 }?.let { "%.1f".format(it) }

        return MediaInfo(
            plot = d.str("overview")?.takeIf { it.isNotBlank() },
            genre = genres?.takeIf { it.isNotBlank() },
            cast = cast?.takeIf { it.isNotBlank() },
            director = director?.takeIf { it.isNotBlank() },
            releaseDate = d.str(if (isSeries) "first_air_date" else "release_date")?.takeIf { it.isNotBlank() },
            duration = runtime?.takeIf { it > 0 }?.toString(),
            rating = rating,
            backdrop = d.str("backdrop_path")?.let { "https://image.tmdb.org/t/p/w1280$it" },
            trailer = trailer,
            source = "TMDB",
        )
    }

    // ---- Wikipedia (no key needed) ----

    private fun wikipedia(name: String, year: String?, isSeries: Boolean): MediaInfo? {
        val query = buildString {
            append('"').append(name).append('"')
            append(if (isSeries) " série" else " filme")
            if (year != null) append(' ').append(year)
        }
        val search = "https://pt.wikipedia.org/w/api.php".toHttpUrl().newBuilder()
            .addQueryParameter("action", "query")
            .addQueryParameter("list", "search")
            .addQueryParameter("srsearch", query)
            .addQueryParameter("srlimit", "1")
            .addQueryParameter("format", "json")
            .build().toString()
        val hit = (((get(search) as? JsonObject)?.get("query") as? JsonObject)?.get("search") as? JsonArray)
            ?.firstOrNull() as? JsonObject ?: return null
        val page = hit.str("title") ?: return null
        // Avoid unrelated pages: the page title must contain the first word of the movie name.
        val firstWord = normalise(name).split(' ').firstOrNull { it.length > 2 } ?: normalise(name)
        if (!normalise(page).contains(firstWord)) return null

        val summaryUrl = "https://pt.wikipedia.org/api/rest_v1/page/summary/".toHttpUrl().newBuilder()
            .addPathSegment(page.replace(' ', '_'))
            .build().toString()
        val summary = get(summaryUrl) as? JsonObject ?: return null
        val extract = summary.str("extract")?.trim()?.takeIf { it.length > 40 } ?: return null
        if (summary.str("type") == "disambiguation") return null
        return MediaInfo(plot = extract, source = "Wikipédia")
    }

    // ---- helpers ----

    private fun get(url: String): JsonElement? {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        http.newCall(request).execute().use { r ->
            if (!r.isSuccessful) return null
            val body = r.body?.string() ?: return null
            return json.parseToJsonElement(body)
        }
    }

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private fun normalise(s: String): String =
        java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD).replace(ACCENTS, "")

    companion object {
        private val ACCENTS = Regex("\\p{Mn}+")
        private val YEAR = Regex("""\b(19[2-9]\d|20[0-4]\d)\b""")
        private val TAGS = Regex(
            """\b(4k|uhd|fhd|hd|sd|1080p|720p|480p|h\.?265|hevc|x264|dual|dublado|dub|legendado|leg|nacional|cam|ts|hdr|3d|imax)\b""",
            RegexOption.IGNORE_CASE,
        )

        /** "PT | Duna: Parte 2 (2024) [4K] DUB" → ("Duna: Parte 2", "2024"). */
        fun cleanTitle(raw: String): Pair<String, String?> {
            var s = raw
            // Provider prefixes like "BR |", "PT-BR -", "[VOD]".
            if (s.contains('|')) s = s.substringAfterLast('|')
            s = s.replace(Regex("""^\s*[A-Z]{2}(-[A-Z]{2})?\s*[:\-]\s+"""), "")
            val year = YEAR.findAll(s).lastOrNull()?.value
            s = s.replace(Regex("""[\[(][^\])]*[\])]"""), " ")
            if (year != null) s = s.replace(year, " ")
            s = s.replace(TAGS, " ")
            s = s.replace(Regex("""[_.]+"""), " ").replace(Regex("""\s+"""), " ").trim(' ', '-', ':')
            return (s.ifBlank { raw.trim() }) to year
        }
    }
}
