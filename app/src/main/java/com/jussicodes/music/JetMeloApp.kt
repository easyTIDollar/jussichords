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
import com.jussicodes.music.constants.audioSourceKey
import com.jussicodes.music.constants.cacheAutoCleanEnabledKey
import com.jussicodes.music.constants.cacheAutoCleanThresholdKey
import com.jussicodes.music.constants.directSourceKey
import com.jussicodes.music.constants.fontScaleKey
import com.jussicodes.music.constants.localSourceKey
import com.jussicodes.music.constants.uiScaleKey
import com.jussicodes.music.constants.ncmCookieKey
import com.jussicodes.music.constants.resolveAllSongsKey
import com.jussicodes.music.constants.unblockSourceKey
import com.jussicodes.music.data.ExplorePreloader
import com.jussicodes.music.data.MsgSessionCache
import com.jussicodes.music.data.NcmCookieStore
import com.jussicodes.music.data.HomeBgTextStore
import com.jussicodes.music.data.HomeTransparentCardStore
import com.jussicodes.music.data.HomeUserInfoStore
import com.jussicodes.music.data.PinnedAlbumStore
import com.jussicodes.music.utils.AppVisibilityTracker
import com.jussicodes.music.utils.AppUpdateManager
import com.jussicodes.music.utils.CacheManager
import com.jussicodes.music.utils.UserAgentUtil
import com.jussicodes.music.utils.apiServers
import com.jussicodes.music.utils.dataStore
import com.rcmiku.ncmapi.api.API_BASE_URL
import com.jussicodes.music.playback.PlaybackReloadBus
import com.rcmiku.ncmapi.api.AudioSourceSettings
import com.rcmiku.ncmapi.api.AudioSources
import com.rcmiku.ncmapi.api.LocalSources
import com.rcmiku.ncmapi.api.player.PlayerApi
import com.rcmiku.ncmapi.api.UNBLOCK_SOURCE
import com.rcmiku.ncmapi.utils.CookieProvider
import com.rcmiku.ncmapi.utils.UserAgentProvider
import com.rcmiku.ncmapi.utils.json
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** 音源相关设置的五元组（一起 collect，任一变化都重新同步到 ncmapi 全局变量）。 */
private data class AudioSync(
    val apiUrl: String?,
    val unblockSource: String?,
    val audioSource: String?,
    val directSource: String?,
    val resolveAllSongs: Boolean?
)

@HiltAndroidApp
class JetMeloApp : Application(), SingletonImageLoader.Factory {

    companion object {
        lateinit var instance: JetMeloApp
            private set
        val context: android.content.Context get() = instance.applicationContext
    }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        // 系统深浅色切换时重染桌面小组件（RemoteViews 不会自动跟着换色）
        applicationScope.launch {
            com.jussicodes.music.widget.WidgetUpdater.redraw(this@JetMeloApp)
        }
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        instance = this
        // 封面请求尺寸按屏幕密度换算：所有 toCoverImageUrl 调用点默认读这里。
        com.jussicodes.music.utils.CoverDensity.init(this)
        // 主题种子色缓存要在首帧前可用（同步读一张极小的 SharedPreferences 表）。
        com.jussicodes.music.ui.theme.ArtworkSeedCache.preload(this)
        // 播放直链磁盘缓存：冷启动（桌面组件/蓝牙续播）直接命中上次解析结果，
        // 省掉起播前那次 /song/url 往返。这里只拿 prefs 引用，不读盘。
        com.jussicodes.music.playback.PlayUrlCache.attach(this)
        // 音频分片索引库（SimpleCache）在后台预热：它原本在 PlaybackService.onCreate
        // 主线程上打开，冷启动点播放时要白等一段磁盘 IO。
        applicationScope.launch {
            runCatching { com.jussicodes.music.utils.AudioCache.get(this@JetMeloApp) }
        }
        AppVisibilityTracker.register(this)
        ExplorePreloader.init(this)
        PinnedAlbumStore.init(this)
        PinnedAlbumStore.watchHidden(this)
        PinnedAlbumStore.watchColumns(this)
        HomeTransparentCardStore.watch(this)
        HomeBgTextStore.watch(this)
        NcmCookieStore.watch(this)
        HomeUserInfoStore.init(this)
        MsgSessionCache.init(this)
        com.jussicodes.music.data.download.DownloadStore.init(this)
        com.jussicodes.music.data.external.ExternalPlaylistStore.init(this)
        UserAgentProvider.init(UserAgentUtil.DEFAULT_USER_AGENT)
        // LX 脚本音源：脚本列表（DataStore）+ 沙盒引擎在进程级单例，ncmapi 侧只留桥。
        com.jussicodes.music.lx.LxPluginStore.attach(this)
        com.jussicodes.music.lx.LxPluginManager.init(this)
        com.rcmiku.ncmapi.api.LxSourceBridge.resolver = { songId, songLevel, skipUrls ->
            com.jussicodes.music.lx.LxPluginManager.resolveUrl(songId, songLevel, skipUrls)
        }
        // ncmapi 里的解析分支（官方/客户端音源/解灰/兜底）只负责吐事件，
        // 统一汇进 PlaybackPath —— 播放页「可视化播放解析」面板就是这条链的回放。
        // 事件带 songId：预取下一首也会走解析，不能让它混进当前歌的时间线。
        com.rcmiku.ncmapi.api.PlayUrlTrace.sink = { songId, name, detail, tags ->
            com.jussicodes.music.playback.PlaybackPath.stepFor(songId, name, detail, tags)
        }
        // 关键路径让路首屏：WebView 初始化在主线程耗时极高（数百毫秒），
        // 启动延迟 4 秒等待首屏完全渲染并静止后，再后台预热沙盒；
        // 若用户 4 秒内点播，LxPluginEngine 内部原本就有懒建机制兜底。
        applicationScope.launch {
            delay(4000)
            com.jussicodes.music.lx.LxPluginManager.warmUp()
        }
        // 关键路径优化：单次读取 DataStore 根快照，顺序注入 Explore/PinnedAlbum/HomeUserInfo 各 Store，
        // 彻底消除启动阶段多协程并发 .first() 产生的 DataStore 锁排队与 I/O 阻塞。
        applicationScope.launch {
            val prefs = dataStore.data.first()
            ExplorePreloader.loadCache(prefs)
            PinnedAlbumStore.loadCache(prefs)
            HomeUserInfoStore.applySnapshot(prefs)
            prefs[uiScaleKey]?.let { com.jussicodes.music.ui.components.ScaledDialogDensity.scale = it }
            prefs[fontScaleKey]?.let { com.jussicodes.music.ui.components.ScaledDialogDensity.fontScale = it }

            val needAudioSourceMigration = prefs[audioSourceKey] == null
            val alreadyCleaned = prefs[apiServerListCleanedKey] ?: false
            if (needAudioSourceMigration || !alreadyCleaned) {
                dataStore.edit { editPrefs ->
                    if (needAudioSourceMigration) {
                        editPrefs.remove(localSourceKey)
                        editPrefs[audioSourceKey] = AudioSources.LX
                    }
                    if (!alreadyCleaned) {
                        val builtin = apiServers.toSet()
                        val currentList = editPrefs[apiServerListKey]
                            ?.let { runCatching { json.decodeFromString<List<String>>(it) }.getOrNull() }
                            ?: apiServers.toList()
                        val cleaned = currentList.filter { it in builtin }.ifEmpty { apiServers.toList() }
                        editPrefs[apiServerListKey] = json.encodeToString(cleaned)
                        val current = editPrefs[apiBaseUrlKey]
                        if (!current.isNullOrEmpty() && current !in builtin) {
                            editPrefs[apiBaseUrlKey] = ""
                        }
                        editPrefs[apiServerListCleanedKey] = true
                    }
                }
            }
        }
        applicationScope.launch {
            // 缓存自动清理错峰 6 秒：避免启动瞬间扫描整个 cacheDir 抢占 I/O 导致首屏顿挫
            delay(6000)
            val prefs = dataStore.data.first()
            val autoClean = prefs[cacheAutoCleanEnabledKey] ?: false
            if (!autoClean) return@launch
            val threshold = prefs[cacheAutoCleanThresholdKey]
                ?: (1024L * 1024 * 1024)
            val snapshot = CacheManager.measure(this@JetMeloApp)
            if (snapshot.totalBytes > threshold) {
                CacheManager.clear(this@JetMeloApp)
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
                    // 登录态一变就同步到桌面小组件：否则没播放过歌的用户加组件后会一直看到「未登录」。
                    com.jussicodes.music.widget.WidgetUpdater.syncLoginFlag(this@JetMeloApp)
                    // 关键路径让路首屏：冷启动时首屏在「我的」页，
                    // 探索页已有 loadCache() 磁盘缓存铺底，预热延迟 2.5 秒错峰，
                    // 避免冷启动瞬间与首屏个人信息/歌单请求争夺网络与 CPU。
                    delay(2500)
                    ExplorePreloader.warmUp(this@JetMeloApp)
                }
        }
        applicationScope.launch {
            dataStore.data
                .map { prefs ->
                    AudioSync(
                        prefs[apiBaseUrlKey],
                        prefs[unblockSourceKey],
                        prefs[audioSourceKey],
                        prefs[directSourceKey],
                        prefs[resolveAllSongsKey]
                    )
                }
                .distinctUntilChanged()
                .collect { (apiUrl, unblockSource, audioSource, directSource, resolveAllSongs) ->
                    // 无条件同步：用户选「不使用自建服务器」时 apiUrl 为空，内存变量也要清空，
                    // 否则残留旧代理地址继续走。空是 API_BASE_URL 的既有默认态（直连兜底）。
                    API_BASE_URL = apiUrl.orEmpty()
                    UNBLOCK_SOURCE = unblockSource ?: "AUTO"
                    // 音源三选一（LX / 内置直连 / 代理）。用户要求新版本一律回到内置 LX，
                    // 因此不再迁移旧值：旧键 localSource/unblockSource 的选择在此作废。
                    val selected =
                        AudioSources.fromKey(audioSource.orEmpty())?.value ?: AudioSources.DEFAULT
                    AudioSourceSettings.SELECTED = selected
                    AudioSourceSettings.RESOLVE_ALL = resolveAllSongs ?: false
                    val direct = LocalSources.fromKey(directSource.orEmpty())?.value
                        ?: LocalSources.AUTO
                    PlayerApi.DirectSourceSettings.SOURCE = direct
                    // cacheKey 需要它：换源后旧直链缓存才会失效
                    PlaybackReloadBus.sourceTag = "$selected/$direct"
                }
        }
    }

    override fun onTerminate() {
        applicationScope.cancel()
        super.onTerminate()
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            // 全局关闭淡入：列表/封面大多命中磁盘或内存缓存，crossfade 会让「已经有的图」
            // 也从透明渐显 100ms，滚动和切页观感上就是慢。需要动画的单个位置自己开。
            .crossfade(false)
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
