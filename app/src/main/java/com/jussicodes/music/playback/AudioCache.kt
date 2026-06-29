package com.jussicodes.music.playback

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache

@UnstableApi
object AudioCache {
    private const val MAX_CACHE_SIZE_BYTES = 512L * 1024 * 1024
    private val lock = Any()

    @Volatile
    private var cache: SimpleCache? = null

    fun get(context: Context): SimpleCache {
        cache?.let { return it }

        return synchronized(lock) {
            cache ?: SimpleCache(
                context.cacheDir.resolve("audio"),
                LeastRecentlyUsedCacheEvictor(MAX_CACHE_SIZE_BYTES),
                StandaloneDatabaseProvider(context)
            ).also { cache = it }
        }
    }
}
