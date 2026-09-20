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
import com.jussicodes.music.constants.ncmCookieKey
import com.jussicodes.music.constants.unblockSourceKey
import com.jussicodes.music.data.ExplorePreloader
import com.jussicodes.music.data.SongSourceCache
import com.jussicodes.music.utils.AppVisibilityTracker
import com.jussicodes.music.utils.AppUpdateManager
import com.jussicodes.music.utils.UserAgentUtil
import com.jussicodes.music.utils.dataStore
import com.rcmiku.ncmapi.api.API_BASE_URL
import com.rcmiku.ncmapi.api.UNBLOCK_SOURCE
import com.rcmiku.ncmapi.utils.CookieProvider
import com.rcmiku.ncmapi.utils.UserAgentProvider
import com.rcmiku.ncmapi.utils.json
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
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
        SongSourceCache.init(this)
        ExplorePreloader.init(this)
        UserAgentProvider.init(UserAgentUtil.DEFAULT_USER_AGENT)
        // Fill the explore flows from disk cache immediately, so the explore
        // tab renders content on first frame even while the cookie config
        // and network fetch are still in flight.
        applicationScope.launch { ExplorePreloader.loadCache() }
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
                    Pair(
                        prefs[apiBaseUrlKey],
                        prefs[unblockSourceKey]
                    )
                }
                .distinctUntilChanged()
                .collect { (apiUrl, unblockSource) ->
                    if (!apiUrl.isNullOrEmpty()) API_BASE_URL = apiUrl
                    UNBLOCK_SOURCE = unblockSource ?: "AUTO"
                }
        }
        // First launch (no API server ever configured): auto-select the fastest
        // of the three preset backends so the app's default is the lowest-latency
        // one instead of a hardcoded address. A user-set server always wins.
        applicationScope.launch {
            val configured = runCatching { dataStore.data.first()[apiBaseUrlKey] }.getOrNull()
            if (configured != null) return@launch
            val fastest = AppUpdateManager.measureApiServers().let { statuses ->
                AppUpdateManager.pickFastestApiServer(statuses)
            }
            if (fastest != null) {
                dataStore.edit { it[apiBaseUrlKey] = fastest }
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
