package com.rcmiku.ncmapi.api.account

import com.rcmiku.ncmapi.api.apiGet
import com.rcmiku.ncmapi.api.apiPost
import com.rcmiku.ncmapi.api.player.SongLevel
import com.rcmiku.ncmapi.model.*
import com.rcmiku.ncmapi.utils.CookieProvider

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

    suspend fun userFolloweds(
        userId: Long,
        limit: Int = 20,
        offset: Int = 0
    ): Result<UserFollowResponse> =
        apiGet("/user/followeds", mapOf("uid" to userId, "limit" to limit, "offset" to offset))

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
        val raw = userPlaylistsRaw(userId).getOrThrow()
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
        return if (manipulateType == PlayManipulateType.ADD) {
            apiGet("/playlist/track/add", commonParams)
        } else {
            apiGet("/playlist/track/delete", commonParams)
        }
    }

    suspend fun cloudSong(offset: Int, limit: Int): Result<CloudSongResponse> =
        apiGet("/user/cloud", mapOf("offset" to offset, "limit" to limit))

    suspend fun albumSublist(offset: Int, limit: Int): Result<AlbumSublistResponse> =
        apiGet("/album/sublist", mapOf("offset" to offset, "limit" to limit))

    suspend fun songRecord(uid: Long, type: SongRecordType): Result<RecordResponse> =
        apiGet("/user/record", mapOf("uid" to uid, "type" to type.type))

    suspend fun scrobble(
        songId: Long,
        time: Int,
        total: Int? = null,
        sourceId: Long? = null,
        sourceName: String? = null,
        songName: String? = null,
        artistName: String? = null,
        songLevel: SongLevel? = null
    ): Result<ApiCodeResponse> =
        apiGet(
            "/scrobble/v1",
            buildMap {
                put("id", songId)
                put("time", time.coerceAtLeast(1))
                total?.takeIf { it > 0 }?.let { put("total", it) }
                sourceId?.takeIf { it > 0 }?.let { put("sourceid", it) }
                sourceName?.takeIf { it.isNotBlank() }?.let { put("source", it) }
                songName?.takeIf { it.isNotBlank() }?.let { put("name", it) }
                artistName?.takeIf { it.isNotBlank() }?.let { put("artist", it) }
                songLevel?.let { put("level", it.value) }
                put("timestamp", System.currentTimeMillis())
            }
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
        @kotlinx.serialization.SerialName("specialType") val specialType: Int = 0
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
            specialType = specialType
        )
    }
}
