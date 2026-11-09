package com.jussicodes.music.playback

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 播放解析链路：解析过程在这里逐事件打点，播放页的「播放解析链路」面板只是回放。
 *
 * 为什么要有：用户手机上没有电脑、拿不到 logcat，「点了歌要等多久才出声 / 这次到底走了哪条源」
 * 只能靠应用内可读的时间线回答。[PlaybackTrace] 只服务桌面组件冷启动那一小段（20s 窗口 + 环形日志），
 * 拿不到音质、音源、每一步的归属，也看不到「哪一步慢」。
 *
 * 约定：
 * - 打点只发生在真正做决策的地方（PlayerApi 分支 / LX 脚本逐次尝试 / 解析入口 / 播放器状态），
 *   面板永远不自己编步骤，否则时间线会和真实行为漂移；
 * - 时间戳一律相对「本次链条开始」的毫秒数，同一条链内单调；
 * - 同一首歌的重复 open（seek、播放器重试）不重置时间线，避免把一次起播拆成好几段；
 *   只有换歌、或用户手动「重新解析当前音乐」才重新开始；
 * - 条目有上限，长时间播放不会无限增长。
 */
object PlaybackPath {

    private const val MAX_STEPS = 80

    enum class Status { INFO, RUNNING, OK, FAIL }

    data class Step(
        val name: String,
        val detail: String = "",
        val tags: List<String> = emptyList(),
        /** 相对本次链条开始的毫秒数。 */
        val atMs: Long = 0L,
        /** 这一步自身耗时（批量尝试时才有意义）。 */
        val costMs: Long = 0L,
        val status: Status = Status.INFO,
    )

    data class Snapshot(
        val songId: String = "",
        /** 解析时请求的音质档位（SongLevel.value），面板负责翻译成中文。 */
        val quality: String = "",
        /** 「网易云 · 自动选择音源 → XX音源」这类路由摘要。 */
        val route: String = "",
        val outcome: String = "",
        val totalMs: Long = 0L,
        val steps: List<Step> = emptyList(),
        val finished: Boolean = false,
    )

    private val lock = Any()
    private var startedAt = 0L
    private var songId = ""
    private var quality = ""
    private var route = ""
    private var outcome = ""
    private var finished = false
    /** 当前真正在播的曲目：预取下一首也会走解析入口，不能让它把当前链路冲掉。 */
    private var focusSongId = ""
    private val steps = mutableListOf<Step>()

    private val _state = MutableStateFlow(Snapshot())
    val state: StateFlow<Snapshot> = _state.asStateFlow()

    /** 开始一条新的链路；同一首歌未结束的链路保持原样（seek / 重试会重复 open）。 */
    fun begin(songId: String, quality: String? = null): Boolean = synchronized(lock) {
        // 预取下一首同样会走到解析入口：焦点不在它身上时不打点，否则当前歌的时间线会被清空。
        if (focusSongId.isNotBlank() && !sameSong(songId, focusSongId)) return@synchronized false
        val sameRun = songId == this.songId && steps.isNotEmpty() && !finished
        if (!sameRun) {
            this.songId = songId
            this.quality = quality.orEmpty()
            startedAt = SystemClock.elapsedRealtime()
            steps.clear()
            route = ""
            outcome = ""
            finished = false
        } else if (this.quality.isBlank() && !quality.isNullOrBlank()) {
            this.quality = quality
        }
        publish()
        true
    }

    /**
     * 曲目切换时才重开链路（由播放器在 [onMediaItemTransition] 调用）。
     * 预先建立空链，解析第一行打点就落在新歌上。
     */
    fun focus(songId: String?) = synchronized(lock) {
        val id = songId.orEmpty()
        if (sameSong(id, focusSongId) && id.isNotBlank()) return@synchronized
        focusSongId = id
        this.songId = id
        startedAt = 0L
        quality = ""
        route = ""
        outcome = ""
        finished = false
        steps.clear()
        publish()
    }

    private fun sameSong(a: String, b: String): Boolean =
        a.substringBefore("?") == b.substringBefore("?") && a.isNotBlank() && b.isNotBlank()

    /** 路由摘要：谁发起的、最终落在哪个音源。 */
    fun route(text: String) = synchronized(lock) {
        route = text
        publish()
    }

    fun step(
        name: String,
        detail: String = "",
        tags: List<String> = emptyList(),
        status: Status = Status.INFO,
        costMs: Long = 0L,
    ) = synchronized(lock) {
        append(name, detail, tags, status, costMs)
        publish()
    }

    /**
     * 带曲目归属的打点：解析层（ncmapi）事件走这里。
     * 预取下一首也会经过同一条解析链路，焦点不在它身上时直接丢弃，避免污染当前歌的时间线。
     */
    fun stepFor(
        songId: String,
        name: String,
        detail: String = "",
        tags: List<String> = emptyList(),
        status: Status = Status.INFO,
        costMs: Long = 0L,
    ) = synchronized(lock) {
        if (focusSongId.isNotBlank() && !sameSong(songId, focusSongId)) return@synchronized
        if (this.songId.isBlank()) this.songId = songId
        append(name, detail, tags, status, costMs)
        publish()
    }

    private fun append(
        name: String,
        detail: String,
        tags: List<String>,
        status: Status,
        costMs: Long,
    ) {
        if (startedAt == 0L) startedAt = SystemClock.elapsedRealtime()
        if (steps.size >= MAX_STEPS) steps.removeAt(0)
        steps += Step(
            name = name,
            detail = detail,
            tags = tags,
            atMs = SystemClock.elapsedRealtime() - startedAt,
            costMs = costMs,
            status = status,
        )
    }

    /** 链路走完：解析拿到结果、或确定失败。后续的播放器事件（就绪/报错）仍然追加到同一条链。 */
    fun finish(result: String, status: Status = Status.OK) = synchronized(lock) {
        finishLocked(result, status)
    }

    /** 同 [finish]，但只对焦点曲目生效（解析层的结束事件走这里）。 */
    fun finishFor(songId: String, result: String, status: Status = Status.OK) = synchronized(lock) {
        if (focusSongId.isNotBlank() && !sameSong(songId, focusSongId)) return@synchronized
        finishLocked(result, status)
    }

    private fun finishLocked(result: String, status: Status) {
        outcome = result
        finished = status != Status.FAIL
        if (steps.size >= MAX_STEPS) steps.removeAt(0)
        steps += Step(
            name = if (status == Status.FAIL) "解析失败" else "已获取真实播放链接",
            detail = result,
            atMs = if (startedAt == 0L) 0 else SystemClock.elapsedRealtime() - startedAt,
            status = status,
        )
        publish()
    }

    /** 用户手动「重新解析当前音乐」：清空当前链，等下一次解析重新打点。 */
    fun reset() = synchronized(lock) {
        songId = ""
        quality = ""
        route = ""
        outcome = ""
        finished = false
        startedAt = 0L
        steps.clear()
        publish()
    }

    fun snapshot(): Snapshot = synchronized(lock) { build() }

    private fun publish() {
        _state.value = build()
    }

    private fun build(): Snapshot = Snapshot(
        songId = songId,
        quality = quality,
        route = route,
        outcome = outcome,
        totalMs = if (startedAt == 0L) 0L else SystemClock.elapsedRealtime() - startedAt,
        steps = steps.toList(),
        finished = finished,
    )
}
