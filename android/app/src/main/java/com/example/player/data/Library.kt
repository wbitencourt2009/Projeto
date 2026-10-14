package com.example.player.data

import android.content.Context
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** Favourites and recently watched, stored as item keys ("live:12", "movie:7", "series:3"). */
class Library(context: Context) {
    private val prefs = context.getSharedPreferences("library", Context.MODE_PRIVATE)
    private val listSerializer = ListSerializer(String.serializer())

    fun favourites(): List<String> = read(KEY_FAV)
    fun recent(): List<String> = read(KEY_RECENT)

    fun saveFavourites(keys: List<String>) = write(KEY_FAV, keys)
    fun saveRecent(keys: List<String>) = write(KEY_RECENT, keys)

    fun clear() = prefs.edit().clear().apply()

    private fun read(key: String): List<String> =
        prefs.getString(key, null)?.let { runCatching { Json.decodeFromString(listSerializer, it) }.getOrNull() }.orEmpty()

    private fun write(key: String, keys: List<String>) {
        prefs.edit().putString(key, Json.encodeToString(listSerializer, keys)).apply()
    }

    companion object {
        const val MAX_RECENT = 60
        private const val KEY_FAV = "favourites"
        private const val KEY_RECENT = "recent"
    }
}
