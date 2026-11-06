package com.jussicodes.music.widget

import android.content.Context
import android.os.SystemClock

/**
 * 小组件要展示的播放快照。
 *
 * 由 [WidgetUpdater] 在播放服务侧写入 SharedPreferences，Glance 组合时同步读取——
 * 这样即使 App 进程被杀、系统只是重新 inflate RemoteViews，组件也能立刻画出上一帧内容，
 * 不需要为了一次重绘去拉起 MediaSession。
 */
data class WidgetSnapshot(
    val songId: Long = 0L,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val coverUrl: String = "",
    val isPlaying: Boolean = false,
    val isFm: Boolean = false,
    val isLoading: Boolean = false,
    val isLiked: Boolean = false,
    val hasSession: Boolean = false,
    val loggedIn: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    /** 采样时刻（SystemClock.elapsedRealtime），播放中据此向前推算进度。 */
    val progressAt: Long = 0L,
) {
    // 只看 title：mediaId 回填晚于 title，单看 songId 会把只有 id 没歌名的
    // 中间帧当成“有歌”。空帧防护也用这个口径（见 WidgetUpdater.commit）。
    val hasSong: Boolean get() = title.isNotBlank()

    /** 0f..1f 的播放进度；播放中按采样时间补上已经过去的毫秒数。 */
    fun progress(now: Long = SystemClock.elapsedRealtime()): Float {
        if (durationMs <= 0L) return 0f
        val drift = if (isPlaying && progressAt > 0L) (now - progressAt).coerceAtLeast(0L) else 0L
        return ((positionMs + drift).toFloat() / durationMs).coerceIn(0f, 1f)
    }

    /** 空状态文案：未登录 / 无歌曲 / 加载中。 */
    fun statusTitle(context: Context): String = when {
        !loggedIn && !hasSession -> context.getString(com.jussicodes.music.R.string.widget_empty_not_logged_in)
        isLoading && !hasSong -> context.getString(com.jussicodes.music.R.string.widget_empty_loading)
        else -> context.getString(com.jussicodes.music.R.string.widget_empty_no_song)
    }

    fun statusHint(context: Context): String = when {
        !loggedIn && !hasSession ->
            context.getString(com.jussicodes.music.R.string.widget_empty_not_logged_in_hint)
        isLoading && !hasSong ->
            context.getString(com.jussicodes.music.R.string.widget_empty_loading_hint)
        else -> context.getString(com.jussicodes.music.R.string.widget_empty_no_song_hint)
    }

    fun artistLine(context: Context): String {
        val name = artist.ifBlank { context.getString(com.jussicodes.music.R.string.widget_unknown_artist) }
        // 缓冲/换源时给出可见反馈，别让组件看起来「点了没反应」。
        return if (isLoading && isPlaying) {
            context.getString(com.jussicodes.music.R.string.widget_buffering, name)
        } else {
            name
        }
    }

    fun artistAlbumLine(context: Context): String {
        val a = artist.ifBlank { context.getString(com.jussicodes.music.R.string.widget_unknown_artist) }
        val b = album.ifBlank { context.getString(com.jussicodes.music.R.string.widget_unknown_album) }
        return "$a · $b"
    }
}

object WidgetStateStore {
    private const val PREFS = "jussi_widget_state"
    private const val K_SONG_ID = "song_id"
    private const val K_TITLE = "title"
    private const val K_ARTIST = "artist"
    private const val K_ALBUM = "album"
    private const val K_COVER = "cover_url"
    private const val K_PLAYING = "is_playing"
    private const val K_FM = "is_fm"
    private const val K_LOADING = "is_loading"
    private const val K_LIKED = "is_liked"
    private const val K_SESSION = "has_session"
    private const val K_LOGGED_IN = "logged_in"
    private const val K_POSITION = "position_ms"
    private const val K_DURATION = "duration_ms"
    private const val K_PROGRESS_AT = "progress_at"

    fun read(context: Context): WidgetSnapshot {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return WidgetSnapshot(
            songId = prefs.getLong(K_SONG_ID, 0L),
            title = prefs.getString(K_TITLE, "").orEmpty(),
            artist = prefs.getString(K_ARTIST, "").orEmpty(),
            album = prefs.getString(K_ALBUM, "").orEmpty(),
            coverUrl = prefs.getString(K_COVER, "").orEmpty(),
            isPlaying = prefs.getBoolean(K_PLAYING, false),
            isFm = prefs.getBoolean(K_FM, false),
            isLoading = prefs.getBoolean(K_LOADING, false),
            isLiked = prefs.getBoolean(K_LIKED, false),
            hasSession = prefs.getBoolean(K_SESSION, false),
            loggedIn = prefs.getBoolean(K_LOGGED_IN, false),
            positionMs = prefs.getLong(K_POSITION, 0L),
            durationMs = prefs.getLong(K_DURATION, 0L),
            progressAt = prefs.getLong(K_PROGRESS_AT, 0L),
        )
    }

    /**
     * 写快照。
     *
     * 用 apply()：进程内缓存同步更新（组件合成立刻能读到新帧），磁盘异步刷盘。
     * 之前的 commit() 是每次播放事件、每秒进度 tick 都同步 fsync 一次，
     * 等于把磁盘 IO 顶在点歌/切歌的热路径上。真正的收尾场景（服务销毁）
     * 由系统在 service onDestroy 之后执行 QueuedWork.waitToFinish() 保证落盘。
     */
    fun write(context: Context, snapshot: WidgetSnapshot) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(K_SONG_ID, snapshot.songId)
            .putString(K_TITLE, snapshot.title)
            .putString(K_ARTIST, snapshot.artist)
            .putString(K_ALBUM, snapshot.album)
            .putString(K_COVER, snapshot.coverUrl)
            .putBoolean(K_PLAYING, snapshot.isPlaying)
            .putBoolean(K_FM, snapshot.isFm)
            .putBoolean(K_LOADING, snapshot.isLoading)
            .putBoolean(K_LIKED, snapshot.isLiked)
            .putBoolean(K_SESSION, snapshot.hasSession)
            .putBoolean(K_LOGGED_IN, snapshot.loggedIn)
            .putLong(K_POSITION, snapshot.positionMs)
            .putLong(K_DURATION, snapshot.durationMs)
            .putLong(K_PROGRESS_AT, snapshot.progressAt)
            .apply()
    }
}
