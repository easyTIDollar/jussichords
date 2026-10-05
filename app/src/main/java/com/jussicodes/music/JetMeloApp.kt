package com.jussicodes.music

import android.app.Application
import androidx.datastore.preferences.core.edit
import androidx.media3.common.util.UnstableApi
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.request.CachePolicy
import coil3.request.crossfade
import com.jussicodes.music.constants.apiBaseUrlKey
import com.jussicodes.music.constants.apiServerListCleanedKey
import com.jussicodes.music.constants.apiServerListKey
import com.jussicodes.music.constants.localSourceKey
import com.jussicodes.music.constants.ncmCookieKey
import com.jussicodes.music.constants.unblockSourceKey
import com.jussicodes.music.data.ExplorePreloader
import com.jussicodes.music.data.MsgSessionCache
import com.jussicodes.music.data.PinnedAlbumStore
import com.jussicodes.music.utils.AppVisibilityTracker
import com.jussicodes.music.utils.AppUpdateManager
import com.jussicodes.music.utils.UserAgentUtil
import com.jussicodes.music.utils.apiServers
import com.jussicodes.music.utils.dataStore
import com.rcmiku.ncmapi.api.API_BASE_URL
import com.rcmiku.ncmapi.api.LocalSourceSettings
import com.rcmiku.ncmapi.api.UNBLOCK_SOURCE
import com.rcmiku.ncmapi.utils.CookieProvider
import com.rcmiku.ncmapi.utils.UserAgentProvider
import com.rcmiku.ncmapi.utils.json
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@HiltAndroidApp
class JetMeloApp : Application(), SingletonImageLoader.Factory {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @androidx.annotation.OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        AppVisibilityTracker.register(this)
        ExplorePreloader.init(this)
        PinnedAlbumStore.init(this)
        MsgSessionCache.init(this)
        UserAgentProvider.init(UserAgentUtil.DEFAULT_USER_AGENT)
        // Fill the explore flows from disk cache immediately, so the explore
        // tab renders content on first frame even while the cookie config
        // and network fetch are still in flight.
        applicationScope.launch { ExplorePreloader.loadCache() }
        applicationScope.launch { PinnedAlbumStore.loadCache() }
        // 一次性迁移（本版本起）：旧安装里可能留有自建的 API 服务器（例如 http://8.134.163.111:3000），
        // 共用裸 IP 会过载。首次启动时清掉所有非内置服务器，当前服务器若非内置一并清空；
        // 完成后置标记，之后每次启动不再重复清理。清理在下方同步协程之前，保证写回立即生效。
        applicationScope.launch {
            val alreadyCleaned = dataStore.data.first()[apiServerListCleanedKey] ?: false
            if (alreadyCleaned) return@launch
            val builtin = apiServers.toSet()
            dataStore.edit { prefs ->
                val currentList = prefs[apiServerListKey]
                    ?.let { runCatching { json.decodeFromString<List<String>>(it) }.getOrNull() }
                    ?: apiServers.toList()
                // 只保留内置 3 台；若被删到一台不剩则回落到内置全集，保证仍有可用官方源。
                val cleaned = currentList.filter { it in builtin }.ifEmpty { apiServers.toList() }
                prefs[apiServerListKey] = json.encodeToString(cleaned)
                val current = prefs[apiBaseUrlKey]
                if (!current.isNullOrEmpty() && current !in builtin) {
                    prefs[apiBaseUrlKey] = ""
                }
                prefs[apiServerListCleanedKey] = true
            }
        }
        applicationScope.launch {
            UserAgentProvider.init(UserAgentUtil.DEFAULT_USER_AGENT)
            dataStore.data
                .map { it[ncmCookieKey] }
                .distinctUntilChanged()
                .collect { ncmCookie ->
                    val cookieMap = ncmCookie
                        ?.takeIf { it.isNotEmpty() }
                        ?.let { runCatching { json.decodeFromString<Map<String, String>>(it) }.getOrNull() }
                    if (cookieMap?.containsKey("MUSIC_U") == true) {
                        CookieProvider.init(cookieMap)
                    } else {
                        CookieProvider.clear()
                    }
                    // Warm the explore page content as soon as the cookie
                    // config changes so it is cached before the user opens it.
                    ExplorePreloader.warmUp(this@JetMeloApp)
                }
        }
        applicationScope.launch {
            dataStore.data
                .map { prefs ->
                    Triple(
                        prefs[apiBaseUrlKey],
                        prefs[unblockSourceKey],
                        prefs[localSourceKey]
                    )
                }
                .distinctUntilChanged()
                .collect { (apiUrl, unblockSource, localSource) ->
                    // 无条件同步：用户选「不使用自建服务器」时 apiUrl 为空，内存变量也要清空，
                    // 否则残留旧代理地址继续走。空是 API_BASE_URL 的既有默认态（直连兜底）。
                    API_BASE_URL = apiUrl.orEmpty()
                    UNBLOCK_SOURCE = unblockSource ?: "AUTO"
                    LocalSourceSettings.SONG_SOURCE = localSource ?: "OFF"
                }
        }
    }

    override fun onTerminate() {
        applicationScope.cancel()
        super.onTerminate()
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .crossfade(true)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizeBytes(64 * 1024 * 1024L)
                    .strongReferencesEnabled(true)
                    .build()
            }
            .diskCachePolicy(CachePolicy.ENABLED)
            .diskCache {
                DiskCache.Builder()
                    .maxSizeBytes(512 * 1024 * 1024L)
                    .directory(cacheDir.resolve("coil"))
                    .build()
            }
            .build()
    }

}
