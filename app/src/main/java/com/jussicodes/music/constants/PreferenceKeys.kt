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
val libraryUserDetailCacheKey = stringPreferencesKey("libraryUserDetailCache")
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
// 音源三选一：lx / direct / proxy（取代旧的 localSource 单选）
val audioSourceKey = stringPreferencesKey("audioSource")
// 直连音源档位下的六源单选（AUTO 或具体 key）
val directSourceKey = stringPreferencesKey("directSource")
val ignoredUpdateVersionKey = stringPreferencesKey("ignoredUpdateVersion")
val uiScaleKey = floatPreferencesKey("uiScale")
// 主页背景图（"我的"/"探索"页）：base64 存图，缩放/透明度/模糊度各一个键。
val homeBgImageKey = stringPreferencesKey("homeBgImage")
val homeBgScaleKey = floatPreferencesKey("homeBgScale")
val homeBgOpacityKey = floatPreferencesKey("homeBgOpacity")
val homeBgBlurKey = floatPreferencesKey("homeBgBlur")
val homeBgOffsetXKey = floatPreferencesKey("homeBgOffsetX")
val homeBgOffsetYKey = floatPreferencesKey("homeBgOffsetY")
/** 主页（我的/探索）文字颜色："auto"（默认，跟随主题）/"white"/"black"。 */
val homeBgTextColorKey = stringPreferencesKey("homeBgTextColor")
val githubDownloadSourceKey = stringPreferencesKey("githubDownloadSource")
val playerGestureTutorialVersionKey = intPreferencesKey("playerGestureTutorialVersion")
val homeTransparentCardKey = booleanPreferencesKey("homeTransparentCard")
