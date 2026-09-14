package com.rcmiku.ncmapi.api.recent

import com.rcmiku.ncmapi.api.apiGet
import com.rcmiku.ncmapi.model.RecentAlbumResource
import com.rcmiku.ncmapi.model.RecentListResponse
import com.rcmiku.ncmapi.model.RecentPlaylistResource
import com.rcmiku.ncmapi.model.RecentSongResource

/**
 * 最近播放相关接口（对应 NCM 的 /record/recent/ 系列端点）。
 *
 * 每个端点都可选 limit 参数（默认 100），统一返回 data.total + data.list。
 */
object RecentListenApi {

    /** 最近播放歌曲（data.list 中每项 data 为完整 Song）。 */
    suspend fun recentSongs(limit: Int = 50): Result<RecentListResponse<RecentSongResource>> =
        apiGet("/record/recent/song", mapOf("limit" to limit))

    /** 最近播放歌单。 */
    suspend fun recentPlaylists(limit: Int = 50): Result<RecentListResponse<RecentPlaylistResource>> =
        apiGet("/record/recent/playlist", mapOf("limit" to limit))

    /** 最近播放专辑。 */
    suspend fun recentAlbums(limit: Int = 50): Result<RecentListResponse<RecentAlbumResource>> =
        apiGet("/record/recent/album", mapOf("limit" to limit))
}
