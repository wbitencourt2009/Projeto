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
) {
    val channelsByCategory: Map<String, List<Channel>> by lazy {
        channels.groupBy { it.categoryId ?: UNCATEGORISED }
    }

    /** Categories that actually have channels, in server order. */
    val nonEmptyCategories: List<Category> by lazy {
        val cats = categories.filter { channelsByCategory[it.id]?.isNotEmpty() == true }
        if (channelsByCategory.containsKey(UNCATEGORISED)) cats + Category(UNCATEGORISED, "Outros") else cats
    }

    companion object {
        const val UNCATEGORISED = "__none__"
    }
}

class LoginException(message: String) : Exception(message)
