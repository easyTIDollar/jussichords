package com.rcmiku.ncmapi.api.search

import com.rcmiku.ncmapi.api.apiGet
import com.rcmiku.ncmapi.model.*

object SearchApi {
    suspend fun search(
        offset: Int,
        limit: Int,
        keyword: String,
        type: SearchType
    ): Result<SearchResultWrapper> {
        val rawResult = apiGet<SearchRawResponse>(
            "/cloudsearch",
            mapOf(
                "keywords" to keyword,
                "type" to type.type,
                "limit" to limit,
                "offset" to offset
            )
        ).getOrThrow()

        return Result.success(rawResult.toSearchResultWrapper(offset, limit))
    }

    suspend fun searchSuggestKeyword(keyword: String): Result<SearchSuggestKeywordResponse> {
        val raw = apiGet<SearchSuggestRawResponse>(
            "/search/suggest",
            mapOf("keywords" to keyword)
        ).getOrNull() ?: return Result.success(
            SearchSuggestKeywordResponse(data = SearchSuggestData(suggests = emptyList()))
        )

        val suggests = raw.result.toKeywordItems()
        return Result.success(
            SearchSuggestKeywordResponse(data = SearchSuggestData(suggests = suggests))
        )
    }
}

@kotlinx.serialization.Serializable
data class SearchRawResponse(
    val result: SearchRawResult = SearchRawResult()
)

@kotlinx.serialization.Serializable
data class SearchRawResult(
    val songs: List<Song> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<SearchArtist> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val voices: List<VoiceBaseInfo> = emptyList(),
    val djRadios: List<VoiceBaseInfo> = emptyList(),
    val userprofiles: List<SearchUser> = emptyList(),
    val songCount: Int = 0,
    val albumCount: Int = 0,
    val artistCount: Int = 0,
    val playlistCount: Int = 0,
    val djRadiosCount: Int = 0,
    val userprofileCount: Int = 0
)

fun SearchRawResponse.toSearchResultWrapper(offset: Int, limit: Int): SearchResultWrapper {
    val resources = mutableListOf<SearchResources>()
    result.songs.forEach { song ->
        resources.add(SearchResources(song = song))
    }
    result.albums.forEach { album ->
        resources.add(SearchResources(album = album))
    }
    result.artists.forEach { artist ->
        resources.add(SearchResources(artist = artist))
    }
    result.playlists.forEach { playlist ->
        resources.add(SearchResources(playlist = playlist))
    }
    result.userprofiles.forEach { user ->
        resources.add(SearchResources(user = user))
    }
    result.voices.forEach { voice ->
        resources.add(SearchResources(voice = voice, baseInfo = voice))
    }
    result.djRadios.forEach { radio ->
        resources.add(SearchResources(voice = radio, baseInfo = radio))
    }
    val totalCount = result.songCount +
        result.albumCount +
        result.artistCount +
        result.playlistCount +
        result.djRadiosCount +
        result.userprofileCount
    val more = if (totalCount > 0) offset + limit < totalCount else resources.isNotEmpty()
    return SearchResultWrapper(
        data = SearchData(
            resources = resources,
            more = more
        )
    )
}

@kotlinx.serialization.Serializable
data class SearchSuggestRawResponse(
    val result: SearchSuggestRawResult = SearchSuggestRawResult()
)

@kotlinx.serialization.Serializable
data class SearchSuggestRawResult(
    val songs: List<SearchSuggestRawItem> = emptyList(),
    val albums: List<SearchSuggestRawItem> = emptyList(),
    val artists: List<SearchSuggestRawItem> = emptyList(),
    val playlists: List<SearchSuggestRawItem> = emptyList()
) {
    fun toKeywordItems(): List<SearchSuggestItem> {
        val items = mutableListOf<SearchSuggestItem>()
        songs.forEach { items.add(SearchSuggestItem(keyword = it.name)) }
        albums.forEach { items.add(SearchSuggestItem(keyword = it.name)) }
        artists.forEach { items.add(SearchSuggestItem(keyword = it.name)) }
        playlists.forEach { items.add(SearchSuggestItem(keyword = it.name)) }
        return items
    }
}

@kotlinx.serialization.Serializable
data class SearchSuggestRawItem(
    val id: Long = 0,
    val name: String = "",
    val keyword: String = ""
)
