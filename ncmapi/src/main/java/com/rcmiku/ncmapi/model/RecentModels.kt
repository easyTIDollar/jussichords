package com.rcmiku.ncmapi.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

/**
 * 最近播放相关模型（对应 NCM 的 /record/recent/ 系列接口）。
 *
 * 统一外层结构为 data.total + data.list 数组，每项含 playTime（最近播放时间，毫秒）
 * 与 data（具体资源对象）。不同资源的 data 形状不同：歌曲是完整 Song，
 * 歌单/专辑是精简元数据 + lastSong。
 */

/** 最近播放条目外层容器（泛型 T 为 data 的具体资源类型）。 */
@Serializable
data class RecentResource<T>(
    @SerialName("resourceId") val resourceId: String = "",
    @SerialName("playTime") val playTime: Long = 0,
    val data: T
)

/** 最近播放统一响应外壳。 */
@Serializable
data class RecentListData<T>(
    val total: Int = 0,
    val list: List<RecentResource<T>> = emptyList()
)

@Serializable
data class RecentListResponse<T>(
    val data: RecentListData<T> = RecentListData()
)

// ---- 歌曲：data 就是完整的 Song ----
typealias RecentSongResource = RecentResource<Song>

// ---- 歌单：data 为精简歌单元数据 ----
@Serializable
data class RecentPlaylistData(
    val id: Long = 0,
    val name: String = "",
    @SerialName("coverImgUrl") val coverImgUrl: String = "",
    @SerialName("uiPlaylistType") val uiPlaylistType: Int = 0,
    val creator: PlaylistCreator? = null,
    val lastSong: Song = Song()
)

typealias RecentPlaylistResource = RecentResource<RecentPlaylistData>

// ---- 专辑：data 为精简专辑元数据 ----
@Serializable
data class RecentAlbumData(
    val id: Long = 0,
    val name: String = "",
    val type: String = "",
    val size: Int = 0,
    @SerialName("picUrl") val picUrl: String = "",
    val artists: List<Artist> = emptyList(),
    val lastSong: Song = Song()
)

typealias RecentAlbumResource = RecentResource<RecentAlbumData>
