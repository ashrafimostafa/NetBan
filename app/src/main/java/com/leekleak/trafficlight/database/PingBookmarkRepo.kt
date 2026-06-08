package com.leekleak.trafficlight.database

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

class PingBookmarkRepo(private val context: Context) {
    private val Context.pingBookmarks: DataStore<Preferences> by preferencesDataStore(name = "ping_bookmarks")
    private val dataStore get() = context.pingBookmarks

    val bookmarks: Flow<List<String>> = dataStore.data.map { prefs ->
        prefs[BOOKMARKS]?.let { json ->
            runCatching { Json.decodeFromString<List<String>>(json) }.getOrDefault(emptyList())
        } ?: emptyList()
    }

    suspend fun add(host: String) {
        val normalized = host.trim()
        if (normalized.isBlank()) return
        dataStore.edit { prefs ->
            val current = prefs[BOOKMARKS]?.let { json ->
                runCatching { Json.decodeFromString<List<String>>(json) }.getOrDefault(emptyList())
            } ?: emptyList()
            if (current.any { it.equals(normalized, ignoreCase = true) }) return@edit
            prefs[BOOKMARKS] = Json.encodeToString(current + normalized)
        }
    }

    suspend fun remove(host: String) {
        dataStore.edit { prefs ->
            val current = prefs[BOOKMARKS]?.let { json ->
                runCatching { Json.decodeFromString<List<String>>(json) }.getOrDefault(emptyList())
            } ?: emptyList()
            prefs[BOOKMARKS] = Json.encodeToString(
                current.filterNot { it.equals(host, ignoreCase = true) }
            )
        }
    }

    private companion object {
        val BOOKMARKS = stringPreferencesKey("bookmarks")
    }
}
