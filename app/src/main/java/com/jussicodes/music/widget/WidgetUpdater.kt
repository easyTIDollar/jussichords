package com.jussicodes.music.widget

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.updateAll
import androidx.media3.common.Player
import com.jussicodes.music.constants.MediaSessionConstants
import com.jussicodes.music.constants.ncmCookieKey
import com.jussicodes.music.data.favoriteSongIdsDatastore
import com.jussicodes.music.utils.dataStore
import com.rcmiku.ncmapi.model.Song
import com.rcmiku.ncmapi.utils.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 播放器侧读出来的纯内存快照。
 *
 * 必须在播放器自己的线程上采集：ExoPlayer 的每个 getter 都会校验调用线程
 * （media3 的 verifyApplicationThread），换到协程线程再读会抛 IllegalStateException。
 */
private data class PlayerSnapshot(
    val songId: Long = 0L,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val coverUrl: String = "",
    val isPlaying: Boolean = false,
    val isFm: Boolean = false,
    val isLoading: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val progressAt: Long = 0L,
) {
    val hasSession: Boolean get() = songId != 0L || title.isNotBlank()
}

/** 统一刷新入口：只在播放状态 / 歌曲 / 红心变化时推一次，不做任何定时轮询。 */
object WidgetUpdater {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private var pending: Job? = null
    private var ticker: Job? = null

    /** 最新一次采集结果 + 版本号：事件成串到达时只保证收敛到最新一帧，不排队重放。 */
    @Volatile
    private var latest: PlayerSnapshot? = null

    @Volatile
    private var version = 0

    /**
     * 播放事件入口。
     *
     * Player.Listener 的回调就在播放器线程上，这里必须当场把状态读出来，再交给后台去落盘/重绘。
     * 旧实现把 player 引用直接丢进 Dispatchers.Default 的协程里再读，会命中
     * "Player is accessed on the wrong thread" 并被 runCatching 静默吞掉，
     * 组件因此永远拿不到新快照——表现就是不同步当前播放状态。
     */
    fun refresh(context: Context, player: Player?) {
        if (player == null) return
        val app = context.applicationContext
        val looper = runCatching { player.applicationLooper }.getOrNull() ?: Looper.getMainLooper()
        if (Looper.myLooper() == looper) {
            submit(app, capture(player))
        } else {
            Handler(looper).post { submit(app, capture(player)) }
        }
    }

    /** 红心状态变化：只重算收藏/登录标记，不覆盖正在播放的歌曲信息。 */
    suspend fun syncFavoriteFlags(context: Context) {
        val app = context.applicationContext
        mutex.withLock {
            runCatching {
                val snapshot = WidgetStateStore.read(app)
                val cookie = cookie(app)
                val loggedIn = !cookie.isNullOrBlank()
                // 没有播放会话时也必须同步登录标记：否则空状态会一直停在「未登录」，
                // 用户明明已在 App 内登录，组件却永远提示去登录。
                if (!snapshot.hasSession) {
                    if (loggedIn != snapshot.loggedIn) {
                        WidgetStateStore.write(app, snapshot.copy(loggedIn = loggedIn))
                        WidgetHub.updateAll(app)
                    }
                    return@runCatching
                }
                val ids = favoriteIds(app)
                val liked = snapshot.songId != 0L && snapshot.songId in ids
                if (liked == snapshot.isLiked && loggedIn == snapshot.loggedIn) return@runCatching
                WidgetStateStore.write(
                    app,
                    snapshot.copy(isLiked = liked, loggedIn = loggedIn)
                )
                WidgetHub.updateAll(app)
            }
        }
    }

    /** 登录 / 登出 / 切号变化时立即同步登录标记，不依赖播放事件，也不必等回到前台。 */
    suspend fun syncLoginFlag(context: Context) {
        val app = context.applicationContext
        mutex.withLock {
            runCatching {
                val snapshot = WidgetStateStore.read(app)
                val loggedIn = !cookie(app).isNullOrBlank()
                if (loggedIn == snapshot.loggedIn) return@runCatching
                WidgetStateStore.write(app, snapshot.copy(loggedIn = loggedIn))
                WidgetHub.updateAll(app)
            }
        }
    }

    /** 仅重绘（系统配置变化 / 用户手动刷新），不重新读播放器。 */
    suspend fun redraw(context: Context) {
        val app = context.applicationContext
        mutex.withLock { runCatching { WidgetHub.updateAll(app) } }
    }

    /**
     * 播放服务销毁（划掉 App / 停止播放）：把「正在播放」清掉，
     * 否则组件会永久停在上一帧的播放态，和服务真实状态脱节。
     */
    fun markStopped(context: Context) {
        val app = context.applicationContext
        scope.launch {
            mutex.withLock {
                runCatching {
                    val snapshot = WidgetStateStore.read(app)
                    tickProgress(app, false)
                    if (!snapshot.isPlaying && !snapshot.isLoading) return@runCatching
                    WidgetStateStore.write(app, snapshot.copy(isPlaying = false, isLoading = false))
                    WidgetHub.updateAll(app)
                }
            }
        }
    }

    private fun capture(player: Player): PlayerSnapshot? = runCatching {
        val item = player.currentMediaItem
        val metadata = item?.mediaMetadata
        val extras = metadata?.extras
        val song = extras?.getString("song")?.let {
            runCatching { json.decodeFromString<Song>(it) }.getOrNull()
        }
        PlayerSnapshot(
            songId = item?.mediaId?.toLongOrNull() ?: 0L,
            title = metadata?.title?.toString().orEmpty(),
            artist = metadata?.artist?.toString().orEmpty(),
            album = song?.al?.name.orEmpty(),
            coverUrl = metadata?.artworkUri?.toString().orEmpty(),
            isPlaying = player.isPlaying,
            isFm = extras?.getString(MediaSessionConstants.EXTRA_SOURCE_NAME) ==
                MediaSessionConstants.SOURCE_PERSONAL_FM,
            isLoading = player.isLoading,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = player.duration.coerceAtLeast(0L),
            progressAt = SystemClock.elapsedRealtime(),
        )
    }.getOrNull()

    private fun submit(context: Context, snapshot: PlayerSnapshot?) {
        // 读失败（线程不对 / 播放器已释放）就保持上一帧，绝不把组件清空。
        if (snapshot == null) return
        latest = snapshot
        version++
        if (pending?.isActive == true) return
        pending = scope.launch { drain(context) }
    }

    /** 合并同一轮事件（item → metadata → isPlaying），但保证最后一定落最新一帧。 */
    private suspend fun drain(context: Context) {
        var committed = -1
        while (committed != version) {
            delay(150)
            val target = version
            val snapshot = latest ?: break
            mutex.withLock { runCatching { commit(context, snapshot) } }
            committed = target
        }
    }

    private suspend fun commit(context: Context, snapshot: PlayerSnapshot) {
        val ids = favoriteIds(context)
        val loggedIn = !cookie(context).isNullOrBlank()
        val next = WidgetSnapshot(
            songId = snapshot.songId,
            title = snapshot.title,
            artist = snapshot.artist,
            album = snapshot.album,
            coverUrl = snapshot.coverUrl,
            isPlaying = snapshot.isPlaying,
            isFm = snapshot.isFm,
            isLoading = snapshot.isLoading,
            isLiked = snapshot.songId != 0L && snapshot.songId in ids,
            hasSession = snapshot.hasSession,
            loggedIn = loggedIn,
            positionMs = snapshot.positionMs,
            durationMs = snapshot.durationMs,
            progressAt = snapshot.progressAt,
        )
        // 内容没变就不重绘：回到前台、收藏列表刷新等事件不必再推一次 RemoteViews。
        if (WidgetStateStore.read(context) == next) return
        WidgetStateStore.write(context, next)
        WidgetHub.updateAll(context)
        tickProgress(context, next.isPlaying)
    }

    /**
     * 4×1 胶囊的背景层是实时进度条：播放中每秒推进一次快照里的播放位置，只重绘胶囊。
     * 暂停/停止立刻停掉，不空转。
     */
    private fun tickProgress(context: Context, playing: Boolean) {
        if (!playing) {
            ticker?.cancel()
            ticker = null
            return
        }
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            while (isActive) {
                delay(1000)
                val snapshot = WidgetStateStore.read(context)
                if (!snapshot.isPlaying || snapshot.durationMs <= 0L) break
                val now = SystemClock.elapsedRealtime()
                val position = (snapshot.positionMs + (now - snapshot.progressAt).coerceAtLeast(0L))
                    .coerceAtMost(snapshot.durationMs)
                WidgetStateStore.write(context, snapshot.copy(positionMs = position, progressAt = now))
                runCatching { CapsuleWidget.updateAll(context) }
            }
        }
    }

    private suspend fun favoriteIds(context: Context): List<Long> =
        runCatching { context.favoriteSongIdsDatastore.data.first().songIdsList }
            .getOrDefault(emptyList())

    private suspend fun cookie(context: Context): String? =
        runCatching { context.dataStore.data.first()[ncmCookieKey] }.getOrNull()
}

object WidgetHub {
    val widgets: List<GlanceAppWidget>
        get() = listOf(ClassicWidget, SquareWidget, CapsuleWidget)

    suspend fun updateAll(context: Context) {
        widgets.forEach { widget ->
            runCatching { widget.updateAll(context) }
        }
    }
}
