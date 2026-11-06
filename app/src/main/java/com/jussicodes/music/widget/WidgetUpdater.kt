package com.jussicodes.music.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
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
     * 进程内实时快照。合成组件时优先读它，其次才是 SharedPreferences。
     * 播放器还活着的时候，这边一定是当前帧——不依赖落盘是否成功。
     */
    @Volatile
    var live: WidgetSnapshot? = null
        private set

    /** 组件合成时要用的状态：先看内存里的实时帧，没有再退回落盘快照。 */
    fun current(context: Context): WidgetSnapshot = live ?: WidgetStateStore.read(context)

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
                val snapshot = current(app)
                val loggedIn = !cookie(app).isNullOrBlank()
                // 没有播放会话时也必须同步登录标记：否则空状态会一直停在「未登录」，
                // 用户明明已在 App 内登录，组件却永远提示去登录。
                val liked = if (snapshot.hasSession && snapshot.songId != 0L) {
                    snapshot.songId in favoriteIds(app)
                } else {
                    false
                }
                if (liked == snapshot.isLiked && loggedIn == snapshot.loggedIn) return@runCatching
                publish(app, snapshot.copy(isLiked = liked, loggedIn = loggedIn), redraw = true)
            }
        }
    }

    /** 登录 / 登出 / 切号变化时立即同步登录标记，不依赖播放事件，也不必等回到前台。 */
    suspend fun syncLoginFlag(context: Context) {
        val app = context.applicationContext
        mutex.withLock {
            runCatching {
                val snapshot = current(app)
                val loggedIn = !cookie(app).isNullOrBlank()
                if (loggedIn == snapshot.loggedIn) return@runCatching
                publish(app, snapshot.copy(loggedIn = loggedIn), redraw = true)
            }
        }
    }

    /** 乐观播放/暂停翻转：点按钮立刻把图标翻过来，返回当前是否“正在播放”供调用方决定下一步。 */
    fun optimisticPlayPause(context: Context): Boolean {
        val app = context.applicationContext
        val snapshot = current(app)
        val next = !snapshot.isPlaying
        publish(app, snapshot.copy(isPlaying = next), redraw = true)
        return next
    }

    /**
     * 真实状态到达后对齐：把 [isPlaying]（播放器真实态）覆盖回快照。
     * 调用方在点按钮 300ms 后兜底调用一次，避免乐观帧没被真实事件覆盖。
     */
    fun pushPlayingFrame(context: Context, isPlaying: Boolean) {
        val app = context.applicationContext
        scope.launch {
            delay(300)
            val snapshot = current(app)
            // 已经是真实态就跳过，避免多推一次 RemoteViews。
            if (snapshot.isPlaying == isPlaying) return@launch
            publish(app, snapshot.copy(isPlaying = isPlaying), redraw = true)
        }
    }

    /**
     * 取消待推进的 drain 协程（乐观点击先清掉旧的合并帧，
     * 避免稍后旧事件把刚翻好的图标又翻回去）。
     */
    fun cancelPending() {
        pending?.cancel()
        pending = null
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
        tickProgress(app, false)
        val snapshot = current(app)
        // 只清播放态，歌曲信息留着：关掉 App 后组件仍显示「上次播放的歌」。
        // 这里走同步落盘 + 调用方在 onDestroy 尾部补一次重绘，不靠 scope.launch：
        // scope 可能已被 cancel，协程里的写盘/重绘不跑就会丢这一帧。
        if (!snapshot.isPlaying && !snapshot.isLoading) return
        publishNow(
            app,
            snapshot.copy(
                isPlaying = false,
                isLoading = false,
                progressAt = SystemClock.elapsedRealtime(),
            ),
        )
        scope.launch { runCatching { WidgetHub.updateAll(app) } }
    }

    private fun capture(player: Player): PlayerSnapshot? = try {
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
    } catch (t: Throwable) {
        // 读播放器失败（线程不对 / 播放器已释放）：留个痕迹，别静默吞。
        WidgetDebug.log("capture failed: $t")
        null
    }

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
            delay(60)
            val target = version
            val snapshot = latest ?: break
            mutex.withLock { runCatching { commit(context, snapshot) } }
            committed = target
        }
    }

    private suspend fun commit(context: Context, snapshot: PlayerSnapshot) {
        // 空快照防护：播放器刚起来（队列未恢复）或被清空时，capture 会读到
        //songId=0/title="" 的空快照。旧实现直接把这个空帧 commit() 写盘，
        // 把磁盘上带歌名/封面的快照覆盖掉——“关掉 App 后暂无播放歌曲、
        // 播放控件全没了”正是这里。
        //
        // 判空口径和 hasSong 对齐（title 非空）。mediaId 回填晚于 title 时，
        // 只看 songId 会把中间帧误认为“有歌”，新帧刚切好的歌会被覆盖回去。
        // 规则：新帧没歌、旧帧有歌时，保留旧歌曲信息，只把播放态清零。
        if (snapshot.title.isBlank() && snapshot.songId == 0L) {
            val prev = current(context)
            if (prev.title.isNotBlank()) {
                publish(
                    context,
                    prev.copy(isPlaying = false, isLoading = false),
                    redraw = true,
                )
                tickProgress(context, false)
                return
            }
        }
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
        WidgetDebug.log(
            "commit playing=${next.isPlaying} song=${next.songId} title=${next.title} " +
                "dur=${next.durationMs} liked=${next.isLiked} loggedIn=$loggedIn"
        )
        publish(context, next, redraw = true)
        tickProgress(context, next.isPlaying)
    }

    /**
     * 统一发布点：先更新内存帧（组件合成直接读它），再落盘，最后重绘。
     *
     * 不做「内容没变就跳过」：切歌/暂停这类事件本来就该重绘一次，
     * 而多推一帧 RemoteViews 的成本远低于漏掉一帧导致组件停在旧状态的代价。
     */
    private fun publish(context: Context, snapshot: WidgetSnapshot, redraw: Boolean) {
        live = snapshot
        // write() 内部是 commit()（同步落盘）：服务被划掉/进程被杀时
        // apply 的异步落盘会丢，组件会退回旧帧或被空帧覆盖。这里同步写盘，
        // 不走协程序号门也不异步竞速。重绘也同步走：上一帧必定落地，
        // 不再有中间帧丢弃导致的“组件停在旧状态”。
        runCatching { WidgetStateStore.write(context, snapshot) }
        if (redraw) runCatching { scope.launch { WidgetHub.updateAll(context) } }
    }

    /**
     * 同步发布：服务销毁路径专用。scope 正在 cancel 时协程里的重绘
     * 或落盘可能不跑。这里直接（调用线程、通常主线程）同步写盘，
     * 返回后由调用方负责调 [WidgetHub.updateAll] 一次。
     */
    fun publishNow(context: Context, snapshot: WidgetSnapshot) {
        live = snapshot
        runCatching { WidgetStateStore.write(context, snapshot) }
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
            val songId = current(context).songId
            while (isActive) {
                delay(1000)
                val snapshot = current(context)
                // 切歌/暂停/时长未知都停：宁可少推帧，也不要把别的歌的进度写回去。
                if (!snapshot.isPlaying || snapshot.durationMs <= 0L) break
                if (songId != 0L && snapshot.songId != songId) break
                val now = SystemClock.elapsedRealtime()
                val position = (snapshot.positionMs + (now - snapshot.progressAt).coerceAtLeast(0L))
                    .coerceAtMost(snapshot.durationMs)
                val next = snapshot.copy(positionMs = position, progressAt = now)
                live = next
                WidgetStateStore.write(context, next)
                runCatching { WidgetHub.updateCapsule(context) }
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

    /**
     * 逐实例刷新。
     *
     * 不用 `GlanceAppWidget.updateAll(context)`：它靠 Glance 内部的「widget 类 → receiver」注册表
     * 反查 id，注册表缺失时返回空列表且不报错——组件就再也不刷新了。
     * 这里直接按 receiver 组件名向 AppWidgetManager 取 id，链路短、失败可见。
     */
    suspend fun updateAll(context: Context) {
        val app = context.applicationContext
        pairings.forEach { (widget, receiver) -> refresh(app, widget, receiver) }
    }

    /** 只刷新胶囊：进度条每秒推一次，没必要连带另外两款一起重绘。 */
    suspend fun updateCapsule(context: Context) {
        refresh(context.applicationContext, CapsuleWidget, CapsuleWidgetReceiver::class.java)
    }

    private suspend fun refresh(
        context: Context,
        widget: GlanceAppWidget,
        receiver: Class<out GlanceAppWidgetReceiver>,
    ) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = runCatching {
            manager.getAppWidgetIds(ComponentName(context, receiver))
        }.getOrElse {
            WidgetDebug.log("getAppWidgetIds(${receiver.simpleName}) failed: ${it.message}")
            IntArray(0)
        }
        if (ids.isEmpty()) {
            WidgetDebug.log("${receiver.simpleName}: 桌面上没有实例")
            return
        }
        val glanceManager = GlanceAppWidgetManager(context)
        ids.forEach { rawId ->
            runCatching {
                widget.update(context, glanceManager.getGlanceIdBy(rawId))
            }.onFailure { WidgetDebug.log("update #$rawId failed: $it") }
        }
    }

    private val pairings: List<Pair<GlanceAppWidget, Class<out GlanceAppWidgetReceiver>>>
        get() = listOf(
            ClassicWidget to ClassicWidgetReceiver::class.java,
            SquareWidget to SquareWidgetReceiver::class.java,
            CapsuleWidget to CapsuleWidgetReceiver::class.java,
        )
}
