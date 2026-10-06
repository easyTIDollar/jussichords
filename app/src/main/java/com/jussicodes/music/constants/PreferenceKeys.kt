package com.jussicodes.music.constants

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

val ncmCookieKey = stringPreferencesKey("ncmCookie")
val use40DpIconKey = booleanPreferencesKey("use40DpIcon")
val desktopLyricEnabledKey = booleanPreferencesKey("desktopLyricEnabled")
val currentPlayMediaIdKey = longPreferencesKey("currentPlayMediaId")
val autoSkipNextOnErrorKey = booleanPreferencesKey("autoSkipNextOnError")
val audioQualityKey = stringPreferencesKey("audioQuality")
val audioEffectModeKey = stringPreferencesKey("audioEffectMode")
val audioEffectEightDEnabledKey = booleanPreferencesKey("audioEffectEightDEnabled")
val audioEffectReverbEnabledKey = booleanPreferencesKey("audioEffectReverbEnabled")
val audioEffectIntensityKey = floatPreferencesKey("audioEffectIntensity")
val audioEffectSpeedKey = floatPreferencesKey("audioEffectSpeed")
val themeColorSourceKey = stringPreferencesKey("themeColorSource")
val lyricTranslationEnabledKey = booleanPreferencesKey("lyricTranslationEnabled")
val wordLyricEnabledKey = booleanPreferencesKey("wordLyricEnabled")
val userIdKye = longPreferencesKey("userId")
val pinnedAlbumIdsKey = stringPreferencesKey("pinnedAlbumIds")
val pinnedAlbumsCacheKey = stringPreferencesKey("pinnedAlbumsCache")
val pinnedAlbumsAccountKey = stringPreferencesKey("pinnedAlbumsAccount")
val pinnedAlbumColumnsKey = intPreferencesKey("pinnedAlbumColumns")
val pinnedAlbumsHiddenKey = booleanPreferencesKey("pinnedAlbumsHidden")
val libraryUserInfoCacheKey = stringPreferencesKey("libraryUserInfoCache")
val libraryFavoriteSongCacheKey = stringPreferencesKey("libraryFavoriteSongCache")
val libraryUserPlaylistsCacheKey = stringPreferencesKey("libraryUserPlaylistsCache")
val libraryPlaylistRefreshTokenKey = longPreferencesKey("libraryPlaylistRefreshToken")
val searchTopListFilterIdsKey = stringPreferencesKey("searchTopListFilterIds")
val topListEnabledKey = booleanPreferencesKey("topListEnabled")
val apiBaseUrlKey = stringPreferencesKey("apiBaseUrl")
val apiServerListKey = stringPreferencesKey("apiServerList")
// 一次性迁移标记（本版本起）：旧安装里自建的 API 服务器列表/当前服务器被清掉后置 true，不再重复清理。
val apiServerListCleanedKey = booleanPreferencesKey("apiServerListCleaned")
val unblockSourceKey = stringPreferencesKey("unblockSource")
val localSourceKey = stringPreferencesKey("localSource")
val ignoredUpdateVersionKey = stringPreferencesKey("ignoredUpdateVersion")
val uiScaleKey = floatPreferencesKey("uiScale")
val githubDownloadSourceKey = stringPreferencesKey("githubDownloadSource")
val playerGestureTutorialVersionKey = intPreferencesKey("playerGestureTutorialVersion")
