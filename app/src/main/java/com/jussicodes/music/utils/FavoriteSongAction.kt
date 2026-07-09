package com.jussicodes.music.utils

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.jussicodes.music.constants.libraryPlaylistRefreshTokenKey
import com.jussicodes.music.constants.userIdKye
import com.rcmiku.ncmapi.api.account.AccountApi
import com.rcmiku.ncmapi.model.Song
import kotlinx.coroutines.flow.first

object FavoriteSongAction {
    suspend fun toggle(
        context: Context,
        songId: Long,
        likedSongIds: List<Long>,
        song: Song? = null
    ) {
        setLiked(
            context = context,
            songId = songId,
            like = songId !in likedSongIds,
            song = song
        )
    }

    suspend fun setLiked(
        context: Context,
        songId: Long,
        like: Boolean,
        song: Song? = null
    ) {
        val appContext = context.applicationContext
        val userId = resolveUserId(appContext)
        AccountApi.songLike(like, songId, userId).onSuccess {
            if (like) {
                FavoriteSongIdsUtil.addSongId(appContext, songId)
            } else {
                FavoriteSongIdsUtil.removeSongId(appContext, songId)
            }
            appContext.dataStore.edit { prefs ->
                prefs[libraryPlaylistRefreshTokenKey] = System.currentTimeMillis()
            }
            song?.let { FavoriteSongSyncBus.setLiked(it, like) }
        }
    }

    private suspend fun resolveUserId(context: Context): Long? {
        context.dataStore.data.first()[userIdKye]?.takeIf { it > 0 }?.let {
            return it
        }
        val userId = AccountApi.accountInfo().getOrNull()?.account?.profile?.userId?.takeIf { it > 0 }
        if (userId != null) {
            context.dataStore.edit { prefs ->
                prefs[userIdKye] = userId
            }
        }
        return userId
    }
}
