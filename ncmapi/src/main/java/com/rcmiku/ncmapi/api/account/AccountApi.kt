package com.rcmiku.ncmapi.api.account

import com.rcmiku.ncmapi.api.apiGet
import com.rcmiku.ncmapi.api.apiPostFileOkHttp
import com.rcmiku.ncmapi.api.apiPostFile
import com.rcmiku.ncmapi.api.apiPost
import com.rcmiku.ncmapi.api.player.SongLevel
import com.rcmiku.ncmapi.model.*
import com.rcmiku.ncmapi.utils.CookieProvider
import io.ktor.http.ContentType
import java.io.File

object AccountApi {
    private const val USER_PLAYLIST_CACHE_TTL_MS = 60_000L
    private val userPlaylistRawCache = mutableMapOf<Long, Pair<Long, UserPlaylistRawResponse>>()

    suspend fun account(): Result<UserInfoBatch> {
        val result = apiGet<UserInfoBatch>("/user/account")
        return result.map { fixAccountProfile(it) }
    }

    suspend fun accountInfo(): Result<UserInfoBatch> {
        val result = apiGet<UserInfoBatch>("/user/account")
        return result.map { fixAccountProfile(it) }
    }

    suspend fun uploadAvatar(file: File, imgSize: Int = 800): Result<AvatarUploadResponse> =
        apiPostFileOkHttp(
            "/avatar/upload",
            "imgFile",
            file,
            contentType = ContentType.Image.JPEG,
            params = mapOf(
                "imgSize" to imgSize,
                "imgX" to 0,
                "imgY" to 0
            ),
            includeCommonParams = true
        )

    private fun fixAccountProfile(batch: UserInfoBatch): UserInfoBatch {
        return batch.copy(account = batch.account.copy(profile = batch.profile))
    }

    suspend fun favoriteSong(uid: Long): Result<FavoriteSongResponse> =
        apiGet("/likelist", mapOf("uid" to uid))

    suspend fun favoriteSongIds(): Result<FavoriteSongResponse> =
        apiGet("/likelist")

    suspend fun favoriteSongLikeChange(): Result<ApiCodeResponse> =
        favoriteSongIds().map { ApiCodeResponse(code = 200) }

    suspend fun songLike(like: Boolean, songId: Long, userId: Long? = null): Result<ApiCodeResponse> =
        apiPost<ApiCodeResponse>(
            "/song/like",
            buildMap {
                put("id", songId)
                put("like", like)
                userId?.takeIf { it > 0 }?.let { put("uid", it) }
            }
        ).mapCatching {
            if (it.code == 200) {
                it
            } else {
                throw IllegalStateException(it.message ?: it.msg ?: "Song like failed with code ${it.code}")
            }
        }

    suspend fun songDislike(songId: Long): Result<ApiCodeResponse> =
        songLike(false, songId)

    suspend fun userFollows(
        userId: Long,
        limit: Int = 30,
        offset: Int = 0
    ): Result<UserFollowResponse> =
        apiGet("/user/follows", mapOf("uid" to userId, "limit" to limit, "offset" to offset))

    /**
     * 粉丝列表（getfolloweds）。NCM 服务端分页只认 lasttime 游标（上一页最后一个粉丝的 time，毫秒），
     * offset/limit 被忽略、每页固定最多 30 条；首页 lastTime=0。
     * 代理的 module 端点 `/user/followeds` 不透传 lasttime，故改走代理现成的通用 `/api` 透传端点，
     * 把 lasttime 塞进 data JSON 交给 NCM（生产已验证可翻全）。极简 payload：不带 time/offset（带了游标会失效）。
     */
    suspend fun userFolloweds(
        userId: Long,
        lastTime: Long = 0
    ): Result<UserFollowResponse> {
        val data = """{"userId":"$userId","lasttime":"$lastTime","limit":30,"getcounts":"true"}"""
        return apiGet("/api", mapOf("uri" to "/api/user/getfolloweds/$userId", "data" to data))
    }

    suspend fun followUser(userId: Long, follow: Boolean): Result<ApiCodeResponse> =
        apiPost<ApiCodeResponse>(
            "/follow",
            buildMap {
                put("id", userId)
                put("t", if (follow) 1 else 0)
                put("timestamp", System.currentTimeMillis())
                CookieProvider.cookie.takeIf { it.isNotBlank() }?.let { put("cookie", it) }
            }
        )

    suspend fun userPlaylists(userId: Long): Result<UserPlaylistResponse> {
        val result = userPlaylistsRaw(userId)
        return result.map { raw ->
            UserPlaylistResponse(data = UserPlaylistData(playlist = raw.playlist.map { it.toPlaylist() }))
        }
    }

    private suspend fun userPlaylistsRaw(userId: Long): Result<UserPlaylistRawResponse> {
        userPlaylistRawCache[userId]
            ?.takeIf { System.currentTimeMillis() - it.first < USER_PLAYLIST_CACHE_TTL_MS }
            ?.let { return Result.success(it.second) }

        return apiGet<UserPlaylistRawResponse>(
            "/user/playlist",
            mapOf("uid" to userId, "limit" to 1000, "offset" to 0)
        ).onSuccess { response ->
            userPlaylistRawCache[userId] = System.currentTimeMillis() to response
        }
    }

    fun invalidateUserPlaylistCache(userId: Long? = null) {
        if (userId == null) {
            userPlaylistRawCache.clear()
        } else {
            userPlaylistRawCache.remove(userId)
        }
    }

    suspend fun userPlaylist(
        userId: Long,
        userPlaylistType: UserPlaylistType
    ): Result<UserPlaylistResponse> {
        return userPlaylists(userId).map { response ->
            val playlists = response.data.playlist.filter { playlist ->
                when (userPlaylistType) {
                    UserPlaylistType.CREATE -> !playlist.subscribed
                    UserPlaylistType.COLLECT -> playlist.subscribed
                }
            }
            response.copy(data = UserPlaylistData(playlist = playlists))
        }
    }

    suspend fun userPlaylistV1(
        userId: Long,
        trackIds: List<Long>
    ): Result<UserPlaylistV1Response> {
        // getOrNull: a dead/misconfigured API server surfaces as Result.failure
        // instead of throwing getOrThrow() into the caller's coroutine scope.
        val raw = userPlaylistsRaw(userId).getOrNull()
            ?: return Result.failure(Exception("API 服务器不可用"))
        val playlistsV1 = raw.playlist.map { item ->
            PlaylistV1(
                id = item.id,
                name = item.name,
                coverImgUrl = item.coverImgUrl,
                trackCount = item.trackCount,
                containsTracks = item.trackIds.any { it in trackIds },
                playCount = item.playCount,
                creator = item.creator,
                description = item.description
            )
        }.filter { playlist ->
            raw.playlist.firstOrNull { it.id == playlist.id }?.let { item ->
                !item.subscribed && item.specialType != 5 && item.creator?.userId == userId
            } == true
        }
        return Result.success(UserPlaylistV1Response(playlist = playlistsV1))
    }

    suspend fun playlistManipulate(
        playlistId: Long,
        songIds: List<Long>,
        manipulateType: PlayManipulateType = PlayManipulateType.ADD
    ): Result<ApiCodeResponse> {
        val commonParams = buildMap<String, Any> {
            put("op", if (manipulateType == PlayManipulateType.ADD) "add" else "del")
            put("pid", playlistId)
            put("tracks", songIds.joinToString(","))
            put("timestamp", System.currentTimeMillis())
            CookieProvider.cookie.takeIf { it.isNotBlank() }?.let { put("cookie", it) }
        }
        return apiGet<ApiCodeResponse>("/playlist/tracks", commonParams)
            .onSuccess { userPlaylistRawCache.clear() }
    }

    suspend fun cloudSong(offset: Int, limit: Int): Result<CloudSongResponse> =
        apiGet("/user/cloud", mapOf("offset" to offset, "limit" to limit))

    suspend fun albumSublist(offset: Int, limit: Int): Result<AlbumSublistResponse> =
        apiGet("/album/sublist", mapOf("offset" to offset, "limit" to limit))

    suspend fun songRecord(uid: Long, type: SongRecordType): Result<RecordResponse> =
        apiGet("/user/record", mapOf("uid" to uid, "type" to type.type))

    /**
     * 提交听歌打卡（原版 /scrobble feedback/weblog）。
     * 该链路对应官方客户端的听歌记录写入，sourceId 缺失时回退为歌曲自身 ID。
     */
    suspend fun scrobble(
        songId: Long,
        time: Int,
        sourceId: Long? = null
    ): Result<ScrobbleResponse> =
        apiGet(
            "/scrobble",
            buildMap {
                put("id", songId)
                put("sourceid", sourceId?.takeIf { it > 0 } ?: songId)
                put("time", time.coerceAtLeast(1))
                put("timestamp", System.currentTimeMillis())
            }
        )

    /**
     * 提交播放状态（会话追踪 + 播放模式）。未传 sessionId 时后端自动生成。
     * 该接口无需 MUSIC_U 鉴权即可调用（与 scrobble 不同）。
     */
    suspend fun playStateSubmit(
        songId: Long,
        sessionId: String? = null,
        progress: Int = 0,
        playMode: String = "list_loop",
        type: String = "song"
    ): Result<ApiCodeResponse> =
        apiGet(
            "/relay/play/state/submit",
            buildMap {
                put("id", songId)
                sessionId?.takeIf { it.isNotBlank() }?.let { put("sessionId", it) }
                put("progress", progress.coerceAtLeast(0))
                put("playMode", playMode)
                put("type", type)
            }
        )

    @kotlinx.serialization.Serializable
    data class ScrobbleResponse(
        val code: Int = 0,
        val data: String? = null,
        val message: String? = null,
        val msg: String? = null,
        val details: ScrobbleDetails? = null
    )

    @kotlinx.serialization.Serializable
    data class ScrobbleDetails(
        val startplay: ScrobbleUpstreamResult? = null,
        val play: ScrobbleUpstreamResult? = null
    )

    @kotlinx.serialization.Serializable
    data class ScrobbleUpstreamResult(
        val code: Int = 0,
        val data: String? = null,
        val message: String? = null,
        val msg: String? = null
    )

    @kotlinx.serialization.Serializable
    data class UserPlaylistRawResponse(
        val playlist: List<PlaylistRawItem> = emptyList()
    )

    @kotlinx.serialization.Serializable
    data class PlaylistRawItem(
        val id: Long = 0,
        val name: String = "",
        @kotlinx.serialization.SerialName("coverImgUrl") val coverImgUrl: String = "",
        @kotlinx.serialization.SerialName("trackCount") val trackCount: Int = 0,
        @kotlinx.serialization.SerialName("trackIds") val trackIds: List<Long> = emptyList(),
        @kotlinx.serialization.SerialName("playCount") val playCount: Double = 0.0,
        val creator: PlaylistCreator? = null,
        val description: String = "",
        val subscribed: Boolean = false,
        @kotlinx.serialization.SerialName("specialType") val specialType: Int = 0,
        @kotlinx.serialization.SerialName("createTime") val createTime: Long = 0
    ) {
        fun toPlaylist() = Playlist(
            id = id,
            name = name,
            coverImgUrl = coverImgUrl,
            trackCount = trackCount,
            playCount = playCount,
            creator = creator,
            description = description,
            subscribed = subscribed,
            specialType = specialType,
            createTime = createTime
        )
    }
}
