package com.jussicodes.music.utils

import android.content.Context
import com.jussicodes.music.data.favoriteSongIdsDatastore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

object FavoriteSongIdsUtil {

    suspend fun addSongId(context: Context, songId: Long) {
        context.favoriteSongIdsDatastore.updateData { currentData ->
            if (songId !in currentData.songIdsList) {
                currentData.toBuilder().addSongIds(songId).build()
            } else {
                currentData
            }
        }
    }

    suspend fun removeSongId(context: Context, songId: Long) {
        context.favoriteSongIdsDatastore.updateData { currentData ->
            val newSongIds = currentData.songIdsList.filter { it != songId }
            currentData.toBuilder().clearSongIds().addAllSongIds(newSongIds).build()
        }
    }

    // 空结果不清空本地：/likelist 在 cookie 未就绪或服务端瞬时异常时会返回空数组，
    // 直接覆盖会抹掉用户全部红心。全量清空属极端场景，宁可保留本地。
    suspend fun updateSongIds(context: Context, songIds: List<Long>) {
        if (songIds.isEmpty()) return
        context.favoriteSongIdsDatastore.updateData { currentData ->
            currentData.toBuilder().clearSongIds().addAllSongIds(songIds).build()
        }
    }

    fun getAllSongIds(context: Context): MutableList<Long> {
        return runBlocking(Dispatchers.IO) {
            context.favoriteSongIdsDatastore.data.first().songIdsList
                ?: emptyList<Long>().toMutableList()
        }
    }
}
