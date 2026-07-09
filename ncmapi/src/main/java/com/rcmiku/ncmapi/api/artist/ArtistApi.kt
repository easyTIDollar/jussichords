package com.rcmiku.ncmapi.api.artist

import com.rcmiku.ncmapi.api.apiGet
import com.rcmiku.ncmapi.api.apiPost
import com.rcmiku.ncmapi.model.*

object ArtistApi {
    suspend fun userDetail(userId: Long): Result<UserDetailResponse> =
        apiGet("/user/detail", mapOf("uid" to userId))

    suspend fun artistSongs(artistId: Long, order: String = "hot", limit: Int = 50, offset: Int = 0): Result<ArtistSongsResponse> =
        apiGet("/artist/songs", mapOf("id" to artistId, "order" to order, "limit" to limit, "offset" to offset))

    suspend fun artistSublist(offset: Int = 0, limit: Int = 50): Result<ArtistSublistResponse> {
        val now = System.currentTimeMillis()
        return apiGet(
            "/artist/sublist",
            mapOf(
                "offset" to offset,
                "limit" to limit,
                "timestamp" to now
            )
        )
    }

    suspend fun artistSublistAll(limit: Int = 25): Result<List<SearchArtist>> {
        return try {
            val artists = linkedMapOf<Long, SearchArtist>()
            var offset = 0
            while (true) {
                val response = artistSublist(offset = offset, limit = limit).getOrThrow()
                val previousSize = artists.size
                response.data.forEach { artist ->
                    artists[artist.id] = artist
                }
                val addedCount = artists.size - previousSize
                val hasNextPage = response.hasMore || response.more
                if (!hasNextPage || response.data.isEmpty() || addedCount == 0) break
                offset += limit
            }
            Result.success(artists.values.toList())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun artistSub(artistId: Long, subscribe: Boolean): Result<ApiCodeResponse> =
        apiPost<ApiCodeResponse>(
            "/artist/sub",
            mapOf(
                "id" to artistId,
                "t" to if (subscribe) 1 else -1,
                "timestamp" to System.currentTimeMillis()
            )
        ).mapCatching {
            if (it.code == 200) {
                it
            } else {
                throw IllegalStateException(it.message ?: it.msg ?: "Artist subscribe failed with code ${it.code}")
            }
        }

    suspend fun artistFollowCount(artistId: Long): Result<ArtistFollowCountResponse> =
        apiGet("/artist/follow/count", mapOf("id" to artistId, "timestamp" to System.currentTimeMillis()))

    suspend fun simiArtist(artistId: Long): Result<SimiArtistResponse> =
        apiGet("/simi/artist", mapOf("id" to artistId))

    suspend fun artistHeadInfo(artistId: Long): Result<ArtistHeadInfoResponse> =
        apiGet("/artist/detail", mapOf("id" to artistId))

    suspend fun artistTopSong(artistId: Long): Result<ArtistTopSong> =
        apiGet("/artist/top/song", mapOf("id" to artistId))

    suspend fun artistAlbum(id: Long, limit: Int, offset: Int): Result<ArtistAlbumResponse> =
        apiGet("/artist/album", mapOf("id" to id, "limit" to limit, "offset" to offset))
}
