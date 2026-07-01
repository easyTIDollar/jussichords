package com.jussicodes.music.constants

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

val ncmCookieKey = stringPreferencesKey("ncmCookie")
val use40DpIconKey = booleanPreferencesKey("use40DpIcon")
val currentPlayMediaIdKey = longPreferencesKey("currentPlayMediaId")
val autoSkipNextOnErrorKey = booleanPreferencesKey("autoSkipNextOnError")
val audioQualityKey = stringPreferencesKey("audioQuality")
val dynamicThemeColorKey = booleanPreferencesKey("dynamicThemeColor")
val lyricTranslationEnabledKey = booleanPreferencesKey("lyricTranslationEnabled")
val wordLyricEnabledKey = booleanPreferencesKey("wordLyricEnabled")
val themeSeedColorKey = stringPreferencesKey("themeSeedColor")
val userIdKye = longPreferencesKey("userId")
val pinnedAlbumIdKey = longPreferencesKey("pinnedAlbumId")
val pinnedAlbumIdsKey = stringPreferencesKey("pinnedAlbumIds")
val pinnedAlbumsCacheKey = stringPreferencesKey("pinnedAlbumsCache")
val libraryUserInfoCacheKey = stringPreferencesKey("libraryUserInfoCache")
val libraryFavoriteSongCacheKey = stringPreferencesKey("libraryFavoriteSongCache")
val libraryUserPlaylistsCacheKey = stringPreferencesKey("libraryUserPlaylistsCache")
val libraryPlaylistRefreshTokenKey = longPreferencesKey("libraryPlaylistRefreshToken")
val apiBaseUrlKey = stringPreferencesKey("apiBaseUrl")
val unblockBaseUrlKey = stringPreferencesKey("unblockBaseUrl")
val githubDownloadProxyKey = stringPreferencesKey("githubDownloadProxy")
