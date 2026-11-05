package com.jussicodes.music.widget

import android.content.Context
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 统一刷新入口：只在播放状态 / 歌曲 / 红心变化时推一次，不做任何定时轮询。 */
object WidgetUpdater {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private var pending: Job? = null

    /** 播放器事件会成串到来（metadata → item → isPlaying），合并成一次刷新。 */
    fun refresh(context: Context, player: Player?) {
        val app = context.applicationContext
        pending?.cancel()
        pending = scope.launch {
            delay(180)
            mutex.withLock {
                runCatching { updateFromPlayer(app, player) }
            }
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

    private suspend fun updateFromPlayer(context: Context, player: Player?) {
        val item = player?.currentMediaItem
        val metadata = item?.mediaMetadata
        val extras = metadata?.extras
        val song = extras?.getString("song")?.let {
            runCatching { json.decodeFromString<Song>(it) }.getOrNull()
        }
        val ids = favoriteIds(context)
        val cookie = cookie(context)
        val songId = item?.mediaId?.toLongOrNull() ?: 0L

        val snapshot = WidgetSnapshot(
            songId = songId,
            title = metadata?.title?.toString().orEmpty(),
            artist = metadata?.artist?.toString().orEmpty(),
            album = song?.al?.name.orEmpty(),
            coverUrl = metadata?.artworkUri?.toString().orEmpty(),
            isPlaying = player?.isPlaying == true,
            isFm = extras?.getString(MediaSessionConstants.EXTRA_SOURCE_NAME) ==
                MediaSessionConstants.SOURCE_PERSONAL_FM,
            isLoading = player?.isLoading == true,
            isLiked = songId != 0L && songId in ids,
            hasSession = songId != 0L,
            loggedIn = !cookie.isNullOrBlank(),
        )
        WidgetStateStore.write(context, snapshot)
        WidgetHub.updateAll(context)
    }

    private suspend fun favoriteIds(context: Context): List<Long> =
        runCatching { context.favoriteSongIdsDatastore.data.first().songIdsList }
            .getOrDefault(emptyList())

    private suspend fun cookie(context: Context): String? =
        runCatching { context.dataStore.data.first()[ncmCookieKey] }.getOrNull()
}

object WidgetHub {
    val widgets: List<GlanceAppWidget>
        get() = listOf(ClassicWidget, SquareWidget, CapsuleWidget, FmWidget, VinylWidget)

    suspend fun updateAll(context: Context) {
        widgets.forEach { widget ->
            runCatching { widget.updateAll(context) }
        }
    }
}
