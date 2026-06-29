package com.jussicodes.music

import android.app.Application
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
import com.jussicodes.music.constants.unblockBaseUrlKey
import com.jussicodes.music.utils.UserAgentUtil
import com.jussicodes.music.utils.dataStore
import com.rcmiku.ncmapi.api.API_BASE_URL
import com.rcmiku.ncmapi.api.UNBLOCK_BASE_URL
import com.rcmiku.ncmapi.utils.CookieProvider
import com.rcmiku.ncmapi.utils.UserAgentProvider
import com.rcmiku.ncmapi.utils.json
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@HiltAndroidApp
class JetMeloApp : Application(), SingletonImageLoader.Factory {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @androidx.annotation.OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        UserAgentProvider.init(UserAgentUtil.DEFAULT_USER_AGENT)
        applicationScope.launch {
            delay(300)
            UserAgentProvider.init(UserAgentUtil.DEFAULT_USER_AGENT)
            dataStore.data
                .map { it[ncmCookieKey] }
                .distinctUntilChanged()
                .collect { ncmCookie ->
                    if (ncmCookie?.isNotEmpty() == true)
                        CookieProvider.init(json.decodeFromString(ncmCookie))
                }
        }
        applicationScope.launch {
            delay(300)
            dataStore.data
                .map { prefs ->
                    prefs[apiBaseUrlKey] to prefs[unblockBaseUrlKey]
                }
                .distinctUntilChanged()
                .collect { (apiUrl, unblockUrl) ->
                    if (!apiUrl.isNullOrEmpty()) API_BASE_URL = apiUrl
                    if (!unblockUrl.isNullOrEmpty()) UNBLOCK_BASE_URL = unblockUrl
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
