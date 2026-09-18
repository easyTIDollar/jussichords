package com.jussicodes.music.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.rcmiku.ncmapi.api.artist.ArtistApi
import com.rcmiku.ncmapi.model.Song

class ArtistSongsPagingSource(
    private val artistId: Long,
    private val order: String
) : PagingSource<Int, Song>() {

    override fun getRefreshKey(state: PagingState<Int, Song>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(1)
        }
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Song> {
        return try {
            val offset = params.key ?: 0
            val limit = params.loadSize
            val response = ArtistApi.artistSongs(artistId, order = order, limit = limit, offset = offset)
            if (response.isSuccess) {
                val artistSongs = response.getOrThrow()
                val data = artistSongs.songs
                val nextKey = if (artistSongs.more) offset + limit else null
                LoadResult.Page(
                    data = data,
                    prevKey = if (offset == 0) null else offset - limit,
                    nextKey = nextKey
                )
            } else {
                LoadResult.Error(Exception("Load data failed"))
            }
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }
}
