package com.jussicodes.music.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.media3.common.Player
import com.jussicodes.music.constants.MediaSessionConstants
import com.jussicodes.music.constants.ncmCookieKey
import com.jussicodes.music.data.favoriteSongIdsDatastore
import com.jussicodes.music.utils.AppVisibilityTracker
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
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference

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
    /** 原始 song JSON：解析放到后台线程做，播放器线程（= 主线程）上只搬运字符串。 */
    val songJson: String? = null,
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
     * 播放器弱引用。
     *
     * 进度线不能靠「快照外推」：seek、缓冲、倍速、缓冲卡顿都不会由 Player.Events 通知到组件，
     * 按旧快照往前推就会越漂越远。tick 时直接问真实播放器要一次 currentPosition。
     * 弱引用 + 只在播放器线程读，不持有服务实例。
     */
    @Volatile
    private var playerRef: WeakReference<Player>? = null

    /**
     * 进程内实时快照。合成组件时优先读它，其次才是 SharedPreferences。
     * 播放器还活着的时候，这边一定是当前帧——不依赖落盘是否成功。
     */
    @Volatile
    var live: WidgetSnapshot? = null
        private set

    /** 组件合成时要用的状态：先看内存里的实时帧，没有再退回落盘快照。 */
    fun current(context: Context): WidgetSnapshot {
        val snapshot = live ?: WidgetStateStore.read(context)
        // 播放服务已经不在（正常销毁、被系统回收、进程被杀后重新合成）：
        // 磁盘上很可能还留着上一帧的「播放中」，这里以「没在播」为准。
        // 光靠 onDestroy 清态不够——进程被直接杀掉时 onDestroy 根本不会跑。
        if (!serviceRunning && snapshot.isPlaying) {
            return snapshot.copy(isPlaying = false, isLoading = false)
        }
        return snapshot
    }

    /**
     * 播放服务是否还活着。Glance 合成跑在同一个进程里，所以这个标志是可靠的：
     * 进程死了标志自然回到 false，不需要任何 IPC。
     */
    @Volatile
    private var serviceRunning = false

    fun markServiceRunning() {
        serviceRunning = true
    }

    fun markServiceStopped() {
        serviceRunning = false
    }

    /** 桌面上到底有没有组件实例。结果缓存 10s，避免每次播放事件都问一遍 AppWidgetManager。 */
    @Volatile
    private var activeAt = 0L

    @Volatile
    private var activeValue = false

    /** 合成跑过就不再怀疑「有没有组件」：本轮进程内一直按有组件处理。 */
    @Volatile
    private var proven = false

    /** 组件被添加/删除后清掉缓存，下一次判断重新查。 */
    fun invalidateActive() {
        activeAt = 0L
    }

    /**
     * 合成跑过 = 桌面上确实有实例。用它把门禁直接置真，
     * 免得上游 AppWidgetManager 查询在个别 ROM 上抽风，把整条刷新链路掐死。
     */
    fun markAlive() {
        proven = true
        activeValue = true
        activeAt = SystemClock.elapsedRealtime()
    }

    private fun isActive(context: Context): Boolean {
        if (proven) return true
        val now = SystemClock.elapsedRealtime()
        if (activeAt != 0L && now - activeAt < 10_000L) return activeValue
        val next = WidgetHub.hasAnyInstance(context)
        if (next != activeValue) WidgetDebug.event(context, "active=$next")
        activeValue = next
        activeAt = now
        return next
    }

    /**
     * 播放事件入口。
     *
     * Player.Listener 的回调就在播放器线程上，这里必须当场把状态读出来，再交给后台去落盘/重绘。
     * 把 player 引用直接丢进 Dispatchers.Default 的协程里再读，会命中
     * "Player is accessed on the wrong thread" 并被 runCatching 静默吞掉，组件因此永远拿不到新快照。
     */
    fun refresh(context: Context, player: Player?) {
        if (player == null) return
        val app = context.applicationContext
        // 桌面上没有组件实例就整条链路空转：采集、落盘、重绘都不做。
        if (!isActive(app)) {
            WidgetDebug.log("refresh skipped: no widget instance")
            return
        }
        playerRef = WeakReference(player)
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
        if (!isActive(app)) return
        mutex.withLock {
            runCatching {
                val snapshot = current(app)
                val loggedIn = !cookie(app, force = true).isNullOrBlank()
                // 没有播放会话时也必须同步登录标记：否则空状态会一直停在「未登录」。
                val liked = if (snapshot.hasSession && snapshot.songId != 0L) {
                    snapshot.songId in favoriteIds(app, force = true)
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
        if (!isActive(app)) return
        mutex.withLock {
            runCatching {
                val snapshot = current(app)
                val loggedIn = !cookie(app, force = true).isNullOrBlank()
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

    /** 乐观翻转前/后的版本号：真实播放事件每次 submit 都会 +1。 */
    fun currentVersion(): Int = version

    /**
     * 真实状态到达后对齐：把 [isPlaying]（播放器真实态）覆盖回快照。
     * 调用方在点按钮 300ms 后兜底调用一次，避免乐观帧没被真实事件覆盖。
     *
     * [sinceVersion] 是乐观翻转时的版本号：这之后只要有任何真实播放事件提交过帧
     * （比如播放失败自动停下、服务已回真实 isPlaying），就绝不再用乐观值覆盖真实状态。
     */
    fun pushPlayingFrame(context: Context, isPlaying: Boolean, sinceVersion: Int = Int.MIN_VALUE) {
        val app = context.applicationContext
        scope.launch {
            delay(300)
            if (sinceVersion != Int.MIN_VALUE && version != sinceVersion) return@launch
            val snapshot = current(app)
            // 已经是真实态就跳过，避免多推一次 RemoteViews。
            if (snapshot.isPlaying == isPlaying) return@launch
            publish(app, snapshot.copy(isPlaying = isPlaying), redraw = true)
        }
    }

    /**
     * 切歌后兜底重绘：真实事件回来后 refresh 会按 0 延迟 drain 立即推；
     * 若事件被吞（空帧防护拦截、监听器未注册等），这里 800ms 后强制 updateAll 一次。
     */
    fun poke(context: Context, delayMs: Long = 800) {
        val app = context.applicationContext
        scope.launch {
            delay(delayMs)
            runCatching { WidgetHub.updateAll(app) }
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
        if (!isActive(app)) return
        mutex.withLock { runCatching { WidgetHub.updateAll(app) } }
    }

    /**
     * 播放服务销毁（划掉 App / 停止播放）：把「正在播放」清掉，
     * 否则组件会永久停在上一帧的播放态，和服务真实状态脱节。
     */
    fun markStopped(context: Context) {
        val app = context.applicationContext
        tickProgress(app, false)
        playerRef = null
        markServiceStopped()
        // 无条件写盘 + 重绘：以前「内存帧已经是暂停态」就直接 return，
        // 结果磁盘和桌面上残留的播放态没人覆盖，组件会一直显示「正在播放」。
        val stopped = current(app).copy(
            isPlaying = false,
            isLoading = false,
            progressAt = SystemClock.elapsedRealtime(),
        )
        publishNow(app, stopped)
        scope.launch { runCatching { WidgetHub.updateAll(app) } }
    }

    private fun capture(player: Player): PlayerSnapshot? = try {
        val item = player.currentMediaItem
        val metadata = item?.mediaMetadata
        val extras = metadata?.extras
        PlayerSnapshot(
            songId = item?.mediaId?.toLongOrNull() ?: 0L,
            title = metadata?.title?.toString().orEmpty(),
            artist = metadata?.artist?.toString().orEmpty(),
            // 专辑名要从 song JSON 里取，但反序列化不能放在播放器线程上：
            // 这个回调就在播放器线程（= 主线程），点歌那一下会连着来一串事件。
            songJson = extras?.getString("song"),
            coverUrl = metadata?.artworkUri?.toString().orEmpty(),
            isPlaying = player.playWhenReady && player.playbackState != Player.STATE_IDLE,
            isFm = extras?.getString(MediaSessionConstants.EXTRA_SOURCE_NAME) ==
                MediaSessionConstants.SOURCE_PERSONAL_FM,
            // 不用 player.isLoading：它读的是 PlaybackInfo.loading，切歌/预加载窗口里
            // 经常已经正常出声了还挂着 true，组件就会一直显示「缓冲中」。
            // 只认真正的 STATE_BUFFERING。
            isLoading = player.playbackState == Player.STATE_BUFFERING && player.playWhenReady,
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
            // 切歌/下一首是离散按钮：这里不加 sleep 节流，靠 version 循环收敛同轮事件，
            // 否则「我点了但组件没变」的 150ms 滞后是能被用户感觉出来的。
            val target = version
            val snapshot = latest ?: break
            var ok = false
            // commit 失败（DataStore 抖动、瞬时 Binder 失败）不能静默丢帧：记日志 + 有限次数退避重试，
            // 重试期间不标记 committed。
            for (attempt in 0 until COMMIT_MAX_ATTEMPTS) {
                runCatching { mutex.withLock { commit(context, snapshot) } }
                    .onSuccess { ok = true }
                    .onFailure {
                        WidgetDebug.error("widget commit failed (attempt $attempt)", it)
                        WidgetDebug.event(
                            context,
                            "commit FAIL ${it.javaClass.simpleName}: ${it.message?.take(80)}"
                        )
                    }
                if (ok) break
                delay(200L shl attempt)
            }
            if (!ok) break
            committed = target
        }
    }

    private suspend fun commit(context: Context, snapshot: PlayerSnapshot) {
        // 空快照防护：播放器刚起来（队列未恢复）或被清空时，capture 会读到
        // songId=0/title="" 的空快照。直接把这个空帧写盘，会把磁盘上带歌名/封面的
        // 快照覆盖掉——“关掉 App 后暂无播放歌曲、播放控件全没了”正是这里。
        // 判空口径和 hasSong 对齐（title 非空）。
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
        // 专辑名在这里解（Dispatchers.Default），不在播放器线程上解。
        val album = snapshot.album.ifBlank {
            snapshot.songJson?.let { raw ->
                runCatching { json.decodeFromString<Song>(raw).al?.name }.getOrNull()
            }.orEmpty()
        }
        val next = WidgetSnapshot(
            songId = snapshot.songId,
            title = snapshot.title,
            artist = snapshot.artist,
            album = album,
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
        // 一帧里 buffering→ready→playing 会连着来好几次事件，内容没变就别再组一遍、
        // 再往 launcher 推一次 RemoteViews：点歌那一下的卡顿有一半来自这些重复帧。
        if (sameContent(current(context), next)) {
            // 内容没变不等于位置没变（拖动进度条 / seek 只改位置）：更新基准，
            // 让进度线下一帧就从新位置画起，别等下一次暂停/切歌。
            live = next
            WidgetStateStore.write(context, next)
            if (!next.isPlaying) scope.launch { runCatching { WidgetHub.updateAll(context) } }
            tickProgress(context, next.isPlaying)
            return
        }
        WidgetDebug.event(
            context,
            "commit playing=${next.isPlaying} song=${next.songId} title=${next.title.take(14)} " +
                "dur=${next.durationMs} liked=${next.isLiked} loggedIn=$loggedIn"
        )
        publish(context, next, redraw = true)
        tickProgress(context, next.isPlaying)
    }

    /** 只比「组件会画出来的东西」，播放位置/采样时刻不参与。 */
    private fun sameContent(a: WidgetSnapshot, b: WidgetSnapshot): Boolean =
        a.songId == b.songId && a.title == b.title && a.artist == b.artist &&
            a.album == b.album && a.coverUrl == b.coverUrl && a.isPlaying == b.isPlaying &&
            a.isFm == b.isFm && a.isLoading == b.isLoading && a.isLiked == b.isLiked &&
            a.hasSession == b.hasSession && a.loggedIn == b.loggedIn && a.durationMs == b.durationMs

    /**
     * 统一发布点：先更新内存帧（组件合成直接读它），再落盘，最后重绘。
     *
     * 落盘用 apply()：它同步更新进程内缓存、异步刷盘，读侧（组件合成）立刻能看到新值，
     * 又不会把磁盘 fsync 压在播放事件的热路径上。
     */
    private fun publish(context: Context, snapshot: WidgetSnapshot, redraw: Boolean) {
        live = snapshot
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
     * 组件刚被添加到桌面（或系统重建组件）时补一下进度 ticker：
     * 否则用户在播放中途加组件时进度线会停住不动。
     */
    fun ensureProgressTicker(context: Context) {
        if (ticker?.isActive == true) return
        val app = context.applicationContext
        tickProgress(app, current(app).isPlaying)
    }

    /**
     * 进度线：播放中每秒对齐一次真实播放位置，重绘组件。
     *
     * 不能靠「按快照外推」：seek、缓冲停顿、倍速、循环回开头都不会产生组件监听的状态事件，
     * 外推出来的位置会越漂越远——这正是「组件进度和 App 不同步」。所以每轮 tick 都向
     * 播放器要一次 currentPosition，读不到才退回外推。
     */
    private fun tickProgress(context: Context, playing: Boolean) {
        // 每次状态变化（暂停/切歌/加载/切回前台）都重启 ticker，
        // 这样每一帧都是新快照的基准，不会被上一首的偏移带跑。
        ticker?.cancel()
        ticker = null
        if (!playing) return
        // 桌面上没有实例就没人要这条进度：不为空转的 1s 重组买单。
        // 门禁走 isActive()：它带 proven 标志（合成跑过就认定有实例），
        // 不像 getAppWidgetIds 那样在个别 ROM 上返回空、把 ticker 掐死。
        if (!isActive(context)) return
        ticker = scope.launch {
            while (isActive) {
                delay(1000)
                val snapshot = current(context)
                // 暂停/时长未知就停：不让错误进度污染组件。
                if (!snapshot.isPlaying || snapshot.durationMs <= 0L) break
                // 息屏时没人看进度线：跳过这一轮重绘，别在后台纯烧 CPU/Binder。
                if (!screenOn(context)) continue
                // App 在前台时同样没人看组件：每秒 3 次 Glance 全量重组 + RemoteViews
                // 推送正好砸在用户的拖动/转场动画帧里（实测上滑播放页掉到个位数帧率）。
                // 跳过写盘与重绘，回桌面的第一秒 tick 会立刻补上真实位置。
                if (AppVisibilityTracker.isAppInForeground.value) continue
                val now = SystemClock.elapsedRealtime()
                // 以播放器真实位置为准；读不到（播放器已释放）才退回外推。
                val realPosition = readPlayerPosition()
                val position = realPosition
                    ?: (snapshot.positionMs + (now - snapshot.progressAt).coerceAtLeast(0L))
                val next = snapshot.copy(
                    positionMs = position.coerceIn(0L, snapshot.durationMs),
                    progressAt = now,
                )
                live = next
                WidgetStateStore.write(context, next)
                // 只重绘画进度线的组件（Card/Tile/Banner）；Capsule 等真实事件来时再刷。
                runCatching { WidgetHub.updateAll(context, progressOnly = true) }
            }
        }
    }

    /** 息屏判断：息屏时跳过进度重绘。 */
    private fun screenOn(context: Context): Boolean =
        (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isInteractive ?: true

    /** 在播放器线程上读一次真实播放位置；读失败返回 null（不影响 ticker 继续走外推）。 */
    private suspend fun readPlayerPosition(): Long? {
        val player = playerRef?.get() ?: return null
        return runCatching {
            // 播放器是 PlaybackService 在主线程 build 的，applicationLooper 就是主线程。
            withContext(Dispatchers.Main) {
                player.currentPosition.coerceAtLeast(0L)
            }
        }.getOrNull()
    }

    private suspend fun favoriteIds(context: Context, force: Boolean = false): List<Long> {
        val now = SystemClock.elapsedRealtime()
        // 事件成串到达时不用每次都去 DataStore 取一遍收藏列表：短 TTL 缓存即可，
        // 真正收藏/取消收藏的路径会 force=true 覆盖。
        if (!force && likedAt != 0L && now - likedAt < 5_000L) return likedCache
        likedCache = runCatching { context.favoriteSongIdsDatastore.data.first().songIdsList }
            .getOrDefault(likedCache)
        likedAt = now
        return likedCache
    }

    private suspend fun cookie(context: Context, force: Boolean = false): String? {
        val now = SystemClock.elapsedRealtime()
        if (!force && cookieAt != 0L && now - cookieAt < 10_000L) return cookieCache
        cookieCache = runCatching { context.dataStore.data.first()[ncmCookieKey] }.getOrNull()
        cookieAt = now
        return cookieCache
    }

    /** 收藏 / 登录态的短 TTL 缓存：避免每个播放事件都打一遍 DataStore。 */
    private var likedCache: List<Long> = emptyList()
    private var likedAt = 0L
    private var cookieCache: String? = null
    private var cookieAt = 0L

    /** 单帧提交失败的最大重试次数（200ms / 400ms 退避），重试完仍失败才放弃这一帧。 */
    private const val COMMIT_MAX_ATTEMPTS = 3
}

object WidgetHub {
    /** 单实例更新失败的最大重试次数（250ms / 500ms 退避），仍失败则保留上一帧。 */
    private const val UPDATE_MAX_ATTEMPTS = 3

    /**
     * 帧令牌。
     *
     * Glance 的重组由**组件自己的 state** 驱动：session 收到 UpdateGlanceState 才重跑
     * provideGlance，而 `GlanceAppWidget.update()` 本身只意味着“去读一次 state”。
     * 只改进程内快照 / SharedPreferences、不写 Glance state，画面就会永远停在首帧——
     * 典型症状正是「点红心能变（红心会写 state），切歌不变」。
     * 所以每次推送前必须先写一个变化的令牌。
     */
    private val FrameToken = intPreferencesKey("widget_frame_token")
    private val frameSeq = java.util.concurrent.atomic.AtomicInteger(0)

    /** 上一次推送时记下的帧摘要：只在内容真的变了时写一条日志，避免每秒 tick 冲掉环形缓冲。 */
    @Volatile
    private var lastPushDigest: String = ""

    val widgets: List<GlanceAppWidget>
        get() = WidgetRegistry.all.map { it.first }

    /**
     * 逐实例刷新。
     *
     * 不用 `GlanceAppWidget.updateAll(context)`：它靠 Glance 内部的「widget 类 → receiver」注册表
     * 反查 id，注册表缺失时返回空列表且不报错——组件就再也不刷新了。
     * 这里直接按 receiver 组件名向 AppWidgetManager 取 id，链路短、失败可见。
     */
    suspend fun updateAll(context: Context, progressOnly: Boolean = false) {
        val app = context.applicationContext
        // 进度 tick 专用：只推布局里真的画了进度线的那几档（见 WidgetRegistry.progressTickers）。
        val targets = if (progressOnly) WidgetRegistry.progressTickers else pairings
        targets.forEach { (widget, receiver) -> refresh(app, widget, receiver) }
    }

    /** 桌面上是否有这款组件的实例。给「没人用就别干活」的省电判断用。 */
    fun hasInstance(context: Context, receiver: Class<out GlanceAppWidgetReceiver>): Boolean =
        runCatching {
            AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, receiver))
                .isNotEmpty()
        }.getOrDefault(false)

    /** 组件在桌面上有没有实例。 */
    fun hasAnyInstance(context: Context): Boolean =
        WidgetRegistry.all.any { hasInstance(context, it.second) }

    private suspend fun refresh(
        context: Context,
        widget: GlanceAppWidget,
        receiver: Class<out GlanceAppWidgetReceiver>,
    ) {
        val manager = AppWidgetManager.getInstance(context)
        val rawIds = runCatching {
            manager.getAppWidgetIds(ComponentName(context, receiver))
        }.getOrElse {
            WidgetDebug.log("getAppWidgetIds(${receiver.simpleName}) failed: ${it.message}")
            IntArray(0)
        }
        val glanceManager = GlanceAppWidgetManager(context)
        var glanceIds: List<GlanceId> = rawIds.toList().mapNotNull { rawId ->
            runCatching { glanceManager.getGlanceIdBy(rawId) }.getOrNull()
        }
        if (glanceIds.isEmpty()) {
            // getAppWidgetIds 为空或 id 映射失败时，退回 Glance 自己的实例表。
            @Suppress("UNCHECKED_CAST")
            val widgetClass = widget.javaClass as Class<GlanceAppWidget>
            val fallback: List<GlanceId> = runCatching { glanceManager.getGlanceIds(widgetClass) }
                .getOrElse { emptyList() }
            if (fallback.isNotEmpty()) {
                WidgetDebug.event(context, "fallback ${receiver.simpleName} ids=${fallback.size}")
                glanceIds = fallback
            }
        }
        if (glanceIds.isEmpty()) {
            WidgetDebug.event(context, "no ids ${receiver.simpleName} raw=${rawIds.size}")
            return
        }
        glanceIds.forEach { glanceId ->
            var done = false
            // 单个实例更新失败做有限次数退避重试（瞬时 Binder / 事务失败可恢复）；
            // 一个实例失败不影响其他实例，也不会把整体刷新拖太久。
            for (attempt in 0 until UPDATE_MAX_ATTEMPTS) {
                runCatching {
                    // 先写令牌触发重组，再让 Glance 推一帧。
                    updateAppWidgetState(context, glanceId) { prefs ->
                        prefs[FrameToken] = frameSeq.incrementAndGet()
                    }
                    widget.update(context, glanceId)
                }.onSuccess {
                    done = true
                    val frame = WidgetUpdater.current(context)
                    val digest = "${frame.songId}|${frame.isPlaying}|${frame.title.take(12)}"
                    if (digest != lastPushDigest) {
                        lastPushDigest = digest
                        // 和各组件里的 "compose ..." 对读：有 push 没 compose = 没重组。
                        WidgetDebug.event(context, "push ${receiver.simpleName} $digest")
                    }
                }.onFailure {
                    // RemoteViews 超 Binder 上限、合成异常都在这里；至少要在 logcat 里是 error 级别。
                    WidgetDebug.error("update ${receiver.simpleName} failed (attempt $attempt)", it)
                    WidgetDebug.event(
                        context,
                        "upd ${receiver.simpleName} FAIL ${it.javaClass.simpleName}: ${it.message?.take(80)}"
                    )
                }
                if (done) break
                delay(250L * (attempt + 1))
            }
        }
    }

    private val pairings: List<Pair<GlanceAppWidget, Class<out GlanceAppWidgetReceiver>>>
        get() = WidgetRegistry.all
}
