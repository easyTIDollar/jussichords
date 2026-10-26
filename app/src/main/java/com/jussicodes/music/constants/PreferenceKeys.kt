package com.jussicodes.music.constants

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

val ncmCookieKey = stringPreferencesKey("ncmCookie")
val use40DpIconKey = booleanPreferencesKey("use40DpIcon")
val desktopLyricEnabledKey = booleanPreferencesKey("desktopLyricEnabled")
// 桌面歌词外观与位置（Service 与设置页共用）。字号单位 sp；颜色是 ARGB Int。
val desktopLyricFontSizeKey = floatPreferencesKey("desktopLyricFontSize")
val desktopLyricTextColorKey = intPreferencesKey("desktopLyricTextColor")
val desktopLyricStrokeColorKey = intPreferencesKey("desktopLyricStrokeColor")
// 描边粗细倍数：0=不描边，1=默认，最大 2。
val desktopLyricStrokeScaleKey = floatPreferencesKey("desktopLyricStrokeScale")
val desktopLyricLockedKey = booleanPreferencesKey("desktopLyricLocked")
// 位置存归一化比例（0..1，相对可移动范围），换机/横竖屏/分屏都能还原。
val desktopLyricXFractionKey = floatPreferencesKey("desktopLyricXFraction")
val desktopLyricYFractionKey = floatPreferencesKey("desktopLyricYFraction")
// 设置页预览背景：用户自选图片的本地路径，空=用内置默认图。
val desktopLyricPreviewBgKey = stringPreferencesKey("desktopLyricPreviewBg")
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
/** 上次成功/失败检查更新的时间戳（毫秒）。用于自动检查节流，避免每次启动都打 GitHub API。 */
val updateLastCheckAtKey = longPreferencesKey("updateLastCheckAt")
/** 上次检查更新的失败原因（成功时清空）。设置页展示，让用户知道为什么没提示更新。 */
val updateLastErrorKey = stringPreferencesKey("updateLastError")
/** 上次检查到的更新信息（UpdateInfo 的 JSON）。用于免网络的即时弹窗 + 后台检查命中后复用。 */
val updateCacheJsonKey = stringPreferencesKey("updateCacheJson")
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
/** 缓存自动清理开关（默认关）。 */
val cacheAutoCleanEnabledKey = booleanPreferencesKey("cacheAutoCleanEnabled")
/** 缓存自动清理阈值（字节）；默认 1GB。仅在开关打开时生效。 */
val cacheAutoCleanThresholdKey = longPreferencesKey("cacheAutoCleanThresholdBytes")
/**
 * 定时播放（睡眠定时）用户自定义的快捷时长，逗号分隔的分钟数（如 "25,45"）。
 * 顶部快捷选择里除了内置档位，还会把这里保存的档位一并列出。
 */
val sleepTimerShortcutsKey = stringPreferencesKey("sleepTimerShortcuts")
