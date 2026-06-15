package com.leekleak.trafficlight.database

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.leekleak.trafficlight.model.SshProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

class SshProfileRepo(private val context: Context) {
    private val Context.sshProfiles: DataStore<Preferences> by preferencesDataStore(name = "ssh_profiles")
    private val dataStore get() = context.sshProfiles

    val profiles: Flow<List<SshProfile>> = dataStore.data.map { prefs ->
        prefs[PROFILES]?.let { json ->
            runCatching { Json.decodeFromString<List<SshProfile>>(json) }.getOrDefault(emptyList())
        } ?: emptyList()
    }

    suspend fun save(profile: SshProfile) {
        dataStore.edit { prefs ->
            val current = readProfiles(prefs)
            val withoutOld = current.filterNot { it.id == profile.id }
            prefs[PROFILES] = Json.encodeToString(withoutOld + profile)
        }
    }

    suspend fun remove(id: String) {
        dataStore.edit { prefs ->
            val current = readProfiles(prefs)
            prefs[PROFILES] = Json.encodeToString(current.filterNot { it.id == id })
        }
    }

    private fun readProfiles(prefs: Preferences): List<SshProfile> {
        return prefs[PROFILES]?.let { json ->
            runCatching { Json.decodeFromString<List<SshProfile>>(json) }.getOrDefault(emptyList())
        } ?: emptyList()
    }

    private companion object {
        val PROFILES = stringPreferencesKey("profiles")
    }
}
