package com.rcmiku.ncmapi.api.recent

import com.rcmiku.ncmapi.api.apiGet
import com.rcmiku.ncmapi.model.RecentAlbumData
import com.rcmiku.ncmapi.model.RecentListResponse
import com.rcmiku.ncmapi.model.RecentPlaylistData
import com.rcmiku.ncmapi.model.Song

/**
 * 最近播放相关接口（对应 NCM 的 /record/recent/ 系列端点）。
 *
 * 每个端点都可选 limit 参数（默认 100），统一返回 data.total + data.list。
 * 泛型参数是列表里每项的资源 data 类型（由 RecentListData 包成 RecentResource）。
 */
object RecentListenApi {

    /** 最近播放歌曲（data.list 中每项 data 为完整 Song）。 */
    suspend fun recentSongs(limit: Int = 50): Result<RecentListResponse<Song>> =
        apiGet("/record/recent/song", mapOf("limit" to limit))

    /** 最近播放歌单。 */
    suspend fun recentPlaylists(limit: Int = 50): Result<RecentListResponse<RecentPlaylistData>> =
        apiGet("/record/recent/playlist", mapOf("limit" to limit))

    /** 最近播放专辑。 */
    suspend fun recentAlbums(limit: Int = 50): Result<RecentListResponse<RecentAlbumData>> =
        apiGet("/record/recent/album", mapOf("limit" to limit))
}
