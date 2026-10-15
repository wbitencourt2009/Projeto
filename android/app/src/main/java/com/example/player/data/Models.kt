package com.example.player.data

import kotlinx.serialization.Serializable

@Serializable
data class Profile(
    val name: String,
    val type: String, // "xtream" | "m3u"
    val server: String? = null,
    val username: String? = null,
    val password: String? = null,
    val m3uUrl: String? = null,
) {
    val isXtream: Boolean get() = type == TYPE_XTREAM

    companion object {
        const val TYPE_XTREAM = "xtream"
        const val TYPE_M3U = "m3u"
    }
}

enum class Kind { LIVE, MOVIE, SERIES }

@Serializable
data class Category(
    val id: String,
    val name: String,
)

@Serializable
data class Channel(
    val id: String,
    val number: Int? = null,
    val name: String,
    val logo: String? = null,
    val categoryId: String? = null,
    val epgId: String? = null,
    /** Only for M3U profiles. Xtream urls are built at play time from the profile. */
    val url: String? = null,
)

@Serializable
data class Movie(
    val id: String,
    val name: String,
    val poster: String? = null,
    val categoryId: String? = null,
    val rating: String? = null,
    val ext: String? = null,
    val url: String? = null,
)

@Serializable
data class Series(
    val id: String,
    val name: String,
    val cover: String? = null,
    val categoryId: String? = null,
    val rating: String? = null,
    val plot: String? = null,
    /** M3U playlists list episodes as single entries; those play directly. */
    val url: String? = null,
)

@Serializable
data class Episode(
    val id: String,
    val season: Int,
    val number: Int,
    val title: String,
    val ext: String? = null,
    val image: String? = null,
    val plot: String? = null,
    val duration: String? = null,
)

data class SeriesDetail(
    val series: Series,
    val plot: String?,
    val backdrop: String?,
    val episodes: Map<Int, List<Episode>>,
)

@Serializable
data class AccountInfo(
    val status: String? = null,
    /** Epoch seconds, null when the account has no expiry. */
    val expDate: Long? = null,
    val maxConnections: Int? = null,
)

@Serializable
data class Content(
    val categories: List<Category>,
    val channels: List<Channel>,
    val account: AccountInfo? = null,
    val fetchedAt: Long = 0,
    val movieCategories: List<Category> = emptyList(),
    val movies: List<Movie> = emptyList(),
    val seriesCategories: List<Category> = emptyList(),
    val series: List<Series> = emptyList(),
) {
    val channelsByCategory: Map<String, List<Channel>> by lazy {
        channels.groupBy { it.categoryId ?: UNCATEGORISED }
    }
    val moviesByCategory: Map<String, List<Movie>> by lazy {
        movies.groupBy { it.categoryId ?: UNCATEGORISED }
    }
    val seriesByCategory: Map<String, List<Series>> by lazy {
        series.groupBy { it.categoryId ?: UNCATEGORISED }
    }

    /** Categories that actually have channels, in server order. */
    val nonEmptyCategories: List<Category> by lazy { nonEmpty(categories, channelsByCategory) }
    val nonEmptyMovieCategories: List<Category> by lazy { nonEmpty(movieCategories, moviesByCategory) }
    val nonEmptySeriesCategories: List<Category> by lazy { nonEmpty(seriesCategories, seriesByCategory) }

    private fun nonEmpty(cats: List<Category>, by: Map<String, List<*>>): List<Category> {
        val list = cats.filter { by[it.id]?.isNotEmpty() == true }
        return if (by.containsKey(UNCATEGORISED)) list + Category(UNCATEGORISED, "Outros") else list
    }

    companion object {
        const val UNCATEGORISED = "__none__"
    }
}

/** One playable entry, already resolved to a url. */
data class PlayItem(
    val key: String,
    val title: String,
    val subtitle: String? = null,
    val image: String? = null,
    val url: String,
    val number: Int? = null,
    /** Second url to try when the first keeps failing (live: .m3u8 instead of .ts). */
    val fallbackUrl: String? = null,
)

/** A tile in a grid or search result: a channel, a movie or a series. */
data class Tile(
    val kind: Kind,
    val id: String,
    val title: String,
    val image: String?,
    val categoryId: String?,
    val number: Int? = null,
) {
    val key: String get() = keyOf(kind, id)

    companion object {
        fun keyOf(kind: Kind, id: String) = "${kind.name.lowercase()}:$id"
    }
}

fun Channel.toItem() = Tile(Kind.LIVE, id, name, logo, categoryId, number)
fun Movie.toItem() = Tile(Kind.MOVIE, id, name, poster, categoryId)
fun Series.toItem() = Tile(Kind.SERIES, id, name, cover, categoryId)

class LoginException(message: String) : Exception(message)

/** One EPG entry. Times are epoch milliseconds. */
data class Programme(
    val start: Long,
    val end: Long,
    val title: String,
    val description: String? = null,
    val hasArchive: Boolean = false,
) {
    fun isNow(now: Long) = now in start until end
}
