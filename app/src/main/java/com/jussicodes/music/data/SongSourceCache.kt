package com.jussicodes.music.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.jussicodes.music.utils.dataStore
import com.rcmiku.ncmapi.utils.json
import kotlinx.coroutines.flow.first

/**
 * Per-song unblock-source override, persisted in the app settings DataStore.
 *
 * Maps a Netease song id to the unblock source the user explicitly picked from
 * the player page (e.g. "qq", "kugou"). When a song is (re)resolved for
 * playback, the resolver consults this cache so a previously chosen source is
 * applied again without the user having to re-pick. "AUTO"/"跟随全局" entries are
 * stored as null (key removed), which means "follow the global setting".
 *
 * All access is suspend-based (the resolver runs inside a blocking context),
 * so this object must be initialised with an application context once at
 * startup before the first read.
 */
object SongSourceCache {

    private val KEY = stringPreferencesKey("song_unblock_source")

    @Volatile
    private var store: DataStore<Preferences>? = null

    /** Must be called once with the application context before use. */
    fun init(context: Context) {
        if (store == null) store = context.applicationContext.dataStore
    }

    /**
     * Returns the per-song source override, or null when none is set / not
     * initialised yet.
     */
    suspend fun getForSong(songId: Long): String? {
        val s = store ?: return null
        val raw = s.data.first()[KEY] ?: return null
        return runCatching { json.decodeFromString<Map<String, String>>(raw) }
            .getOrDefault(emptyMap())[songId.toString()]
    }

    /**
     * Persists a per-song source override. Pass null (or "AUTO") to clear it,
     * i.e. go back to following the global setting.
     */
    suspend fun setForSong(songId: Long, source: String?) {
        val s = store ?: return
        s.edit { prefs ->
            val raw = prefs[KEY]
            val current = (raw?.let {
                runCatching { json.decodeFromString<Map<String, String>>(it) }.getOrNull()
            } ?: emptyMap()).toMutableMap()
            val idKey = songId.toString()
            val effective = source?.takeIf { it != "AUTO" }
            if (effective == null) current.remove(idKey) else current[idKey] = effective
            prefs[KEY] = json.encodeToString(current)
        }
    }
}
