package com.jussicodes.music.widget

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.jussicodes.music.MainActivity
import com.jussicodes.music.constants.MediaSessionConstants
import com.jussicodes.music.constants.libraryPlaylistRefreshTokenKey
import com.jussicodes.music.constants.userIdKye
import com.jussicodes.music.extensions.toMediaItemList
import com.jussicodes.music.playback.PlaybackService
import com.jussicodes.music.ui.screen.PersonalFmQueueLoader
import com.jussicodes.music.utils.FavoriteSongIdsUtil
import com.jussicodes.music.utils.dataStore
import com.rcmiku.ncmapi.api.account.AccountApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** 红心乐观状态：只在本组件实例上立即翻转，同步结果回来后清掉 / 由真实数据覆盖。 */
object WidgetLikeKeys {
    val SongId = longPreferencesKey("widget_like_song_id")
    val Value = booleanPreferencesKey("widget_like_value")
}

object WidgetIntents {
    fun app(context: Context, openPlayer: Boolean = false): Intent =
        Intent(context, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            if (openPlayer) putExtra(MainActivity.EXTRA_OPEN_PLAYER, true)
        }
}

fun openPlayerAction(context: Context): Action = actionStartActivity(WidgetIntents.app(context, true))

fun openAppAction(context: Context): Action = actionStartActivity(WidgetIntents.app(context))

/**
 * 小组件动作统一走 MediaController：连的就是 App 正在用的 PlaybackService / MediaSession，
 * 播放队列、换源、私人 FM 续播全都复用同一套逻辑。
 */
@UnstableApi
object WidgetMedia {

    suspend fun <T> withController(context: Context, block: suspend (MediaController) -> T): T? {
        val app = context.applicationContext
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = withContext(Dispatchers.Main) {
            MediaController.Builder(app, token).buildAsync()
        }
        return try {
            val controller = withContext(Dispatchers.IO) { future.get(6, TimeUnit.SECONDS) }
            try {
                withContext(Dispatchers.Main) { block(controller) }
            } finally {
                withContext(Dispatchers.Main) { runCatching { controller.release() } }
            }
        } catch (t: Throwable) {
            null
        } finally {
            withContext(Dispatchers.Main) { runCatching { MediaController.releaseFuture(future) } }
        }
    }

    suspend fun openApp(context: Context, openPlayer: Boolean = true) {
        runCatching { context.startActivity(WidgetIntents.app(context, openPlayer)) }
    }
}

class WidgetPlayPauseAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        WidgetMedia.withController(context) { controller ->
            if (controller.isPlaying || controller.playWhenReady) {
                controller.pause()
            } else {
                if (controller.playbackState == Player.STATE_IDLE) controller.prepare()
                controller.play()
            }
        }
    }
}

class WidgetNextAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        WidgetMedia.withController(context) { controller ->
            controller.seekToNextMediaItem()
            if (controller.playbackState == Player.STATE_IDLE) controller.prepare()
            controller.play()
        }
    }
}

class WidgetPrevAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        WidgetMedia.withController(context) { controller ->
            controller.seekToPreviousMediaItem()
            if (controller.playbackState == Player.STATE_IDLE) controller.prepare()
            controller.play()
        }
    }
}

class WidgetLikeAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val app = context.applicationContext
        val snapshot = WidgetUpdater.current(app)
        val songId = snapshot.songId
        if (songId == 0L) return
        val target = !snapshot.isLiked

        // 1. 立即反馈：本组件先翻转。
        updateAppWidgetState(app, glanceId) { prefs ->
            prefs[WidgetLikeKeys.SongId] = songId
            prefs[WidgetLikeKeys.Value] = target
        }
        WidgetHub.updateAll(app)

        // 2. 真正落库（同一个 /song/like 接口），失败则回滚。
        val ok = WidgetLike.setLiked(app, songId, target)

        // 3. 清掉乐观值，以真实收藏状态重绘；失败时自然回到原样。
        updateAppWidgetState(app, glanceId) { prefs ->
            if (prefs[WidgetLikeKeys.SongId] == songId) {
                prefs.remove(WidgetLikeKeys.SongId)
                prefs.remove(WidgetLikeKeys.Value)
            }
        }
        WidgetUpdater.syncFavoriteFlags(app)
        if (!ok) WidgetHub.updateAll(app)
    }
}

object WidgetLike {
    suspend fun setLiked(context: Context, songId: Long, like: Boolean): Boolean {
        val uid = runCatching { context.dataStore.data.first()[userIdKye] }.getOrNull()
        val result = runCatching {
            AccountApi.songLike(like = like, songId = songId, userId = uid)
        }.getOrNull() ?: return false
        if (result.isFailure) return false
        runCatching {
            if (like) {
                FavoriteSongIdsUtil.addSongId(context, songId)
            } else {
                FavoriteSongIdsUtil.removeSongId(context, songId)
            }
            context.dataStore.edit { prefs ->
                prefs[libraryPlaylistRefreshTokenKey] = System.currentTimeMillis()
            }
        }
        return true
    }
}

/** 私人 FM 入口：已经在放 FM 就打开播放页，否则沿用 App 的 FM 队列加载器开播。 */
class WidgetPersonalFmAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val app = context.applicationContext
        val songs = runCatching { PersonalFmQueueLoader.load().getOrThrow() }.getOrNull()
        if (songs.isNullOrEmpty()) {
            WidgetMedia.openApp(app)
            return
        }
        val started = WidgetMedia.withController(app) { controller ->
            controller.setMediaItems(
                songs.toMediaItemList(
                    sourceName = MediaSessionConstants.SOURCE_PERSONAL_FM,
                    sourceType = MediaSessionConstants.SOURCE_TYPE_ROAM,
                )
            )
            controller.prepare()
            controller.play()
        } != null
        if (!started) WidgetMedia.openApp(app)
    }
}

/** 卡片空白处 / 空状态点击：进 App。 */
class WidgetOpenAppAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        WidgetMedia.openApp(context, openPlayer = false)
    }
}

internal fun playPauseAction(): Action = actionRunCallback<WidgetPlayPauseAction>()
internal fun nextAction(): Action = actionRunCallback<WidgetNextAction>()
internal fun prevAction(): Action = actionRunCallback<WidgetPrevAction>()
internal fun likeAction(): Action = actionRunCallback<WidgetLikeAction>()
internal fun fmAction(): Action = actionRunCallback<WidgetPersonalFmAction>()
