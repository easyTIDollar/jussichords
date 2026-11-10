package com.jussicodes.music.playback

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 可视化播放解析：解析过程在这里逐事件打点，播放页的同名面板只是回放。
 *
 * 为什么要有：用户手机上没有电脑、拿不到 logcat，「点了歌要等多久才出声 / 这次到底走了哪条源」
 * 只能靠应用内可读的时间线回答。[PlaybackTrace] 只服务桌面组件冷启动那一小段（20s 窗口 + 环形日志），
 * 拿不到音质、音源、每一步的归属，也看不到「哪一步慢」。
 *
 * 约定：
 * - 打点只发生在真正做决策的地方（PlayerApi 分支 / LX 脚本逐次尝试 / 解析入口 / 播放器状态），
 *   面板永远不自己编步骤，否则时间线会和真实行为漂移；
 * - 时间戳一律相对「该曲目这条链开始」的毫秒数，同一条链内单调；
 * - 同一首歌的重复 open（seek、播放器重试）不重置时间线，避免把一次起播拆成好几段；
 *   只有换歌（且上一轮已播过）、或用户手动「重新解析当前音乐」才重新开始；
 * - **按曲目分开存链**：下一首的预取解析同样是一条真实链路（走了哪个脚本、请求了什么音质、
 *   花了多久），它必须留在那首歌自己的链上 —— 切过去时面板直接展示这次预取的结果，
 *   而不是空着让人以为「没解析过」。焦点只决定「面板现在展示哪条链」。
 * - 链条数量有上限，长时间播放不会无限增长。
 */
object PlaybackPath {

    private const val MAX_STEPS = 80

    /** 同时保留几条曲目链：当前 + 预取的下一首 + 最近点过的几首足够。 */
    private const val MAX_CHAINS = 8

    enum class Status { INFO, RUNNING, OK, FAIL }

    data class Step(
        val name: String,
        val detail: String = "",
        val tags: List<String> = emptyList(),
        /** 相对这条链开始的毫秒数。 */
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

    /** 一首歌的一条解析链。 */
    private class Chain(val key: String) {
        var songId: String = key
        var quality: String = ""
        var route: String = ""
        var outcome: String = ""
        var startedAt: Long = 0L
        var finished: Boolean = false

        /** 这一轮已经播过（焦点离开过它）：下次再点它算新的一轮，重新开链。 */
        var playedOut: Boolean = false

        /** 用户手动「重新解析」：下一次 begin 必须重开，但保留起点时间。 */
        var forceRestart: Boolean = false

        val steps: MutableList<Step> = mutableListOf()
    }

    private val lock = Any()

    /** 按曲目 id 存链，LRU 上限 [MAX_CHAINS]。 */
    private val chains = object : LinkedHashMap<String, Chain>(MAX_CHAINS, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Chain>?): Boolean =
            size > MAX_CHAINS
    }

    /** 面板当前展示的曲目（真正在播的那首）。 */
    private var focusKey: String = ""

    private val _state = MutableStateFlow(Snapshot())
    val state: StateFlow<Snapshot> = _state.asStateFlow()

    /** `123?fee=8&pl=…` 与 `123` 必须落到同一条链上（解析层与播放器侧算出的 key 不同）。 */
    private fun keyOf(songId: String): String = songId.substringBefore("?")

    private fun chainOf(songId: String): Chain {
        val key = keyOf(songId)
        return chains.getOrPut(key) { Chain(key) }
    }

    /**
     * 开始（或续上）某首歌的链路。
     *
     * 返回 true 表示「这次打点可以入库」。以前这里会丢弃非焦点曲目的打点（预取因此整条链消失），
     * 现在每首歌各有自己的链，预取那次解析原样保留，切过去就能看到。
     */
    fun begin(songId: String, quality: String? = null): Boolean = synchronized(lock) {
        if (songId.isBlank()) return@synchronized true
        val existing = chains[keyOf(songId)]
        val reuse = existing != null && !existing.playedOut &&
            (existing.steps.isNotEmpty() || existing.forceRestart)
        val chain = if (reuse) existing!! else Chain(keyOf(songId)).also {
            it.startedAt = SystemClock.elapsedRealtime()
            chains[it.key] = it
        }
        chain.forceRestart = false
        chain.songId = songId
        if (chain.steps.isEmpty()) chain.finished = false
        if (chain.quality.isBlank() && !quality.isNullOrBlank()) chain.quality = quality
        if (chain.startedAt == 0L) chain.startedAt = SystemClock.elapsedRealtime()
        if (focusKey.isBlank()) focusKey = chain.key
        publish(chain)
        true
    }

    /**
     * 曲目切换（[androidx.media3.common.Player.Listener.onMediaItemTransition]）：
     * 只切「面板展示哪条链」，不清同曲已有的链 —— 预取的链在切歌前就写好了。
     */
    fun focus(songId: String?) = synchronized(lock) {
        val key = keyOf(songId.orEmpty())
        if (focusKey.isNotBlank() && focusKey != key) chains[focusKey]?.playedOut = true
        focusKey = key
        val chain = if (key.isBlank()) null else chainOf(key)
        publish(chain)
    }

    /** 路由摘要：谁发起的、最终落在哪个音源。 */
    fun route(text: String) = synchronized(lock) {
        val chain = chains[focusKey] ?: return@synchronized
        chain.route = text
        publish(chain)
    }

    /** 同 [route]，但明确归属某首歌 —— 预取下一首的路由必须落在它自己那条链上。 */
    fun routeFor(songId: String, text: String) = synchronized(lock) {
        val chain = chainOf(songId)
        chain.route = text
        publish(chain)
    }

    fun step(
        name: String,
        detail: String = "",
        tags: List<String> = emptyList(),
        status: Status = Status.INFO,
        costMs: Long = 0L,
    ) = synchronized(lock) {
        val chain = chains[focusKey] ?: return@synchronized
        append(chain, name, detail, tags, status, costMs)
        publish(chain)
    }

    /** 带曲目归属的打点：解析层（ncmapi / LX）事件走这里，落在那首歌自己的链上。 */
    fun stepFor(
        songId: String,
        name: String,
        detail: String = "",
        tags: List<String> = emptyList(),
        status: Status = Status.INFO,
        costMs: Long = 0L,
    ) = synchronized(lock) {
        if (songId.isBlank()) return@synchronized
        val chain = chainOf(songId)
        chain.songId = songId
        append(chain, name, detail, tags, status, costMs)
        publish(chain)
    }

    private fun append(
        chain: Chain,
        name: String,
        detail: String,
        tags: List<String>,
        status: Status,
        costMs: Long,
    ) {
        if (chain.startedAt == 0L) chain.startedAt = SystemClock.elapsedRealtime()
        if (chain.steps.size >= MAX_STEPS) chain.steps.removeAt(0)
        chain.steps += Step(
            name = name,
            detail = detail,
            tags = tags,
            atMs = SystemClock.elapsedRealtime() - chain.startedAt,
            costMs = costMs,
            status = status,
        )
    }

    /** 链路走完：解析拿到结果、或确定失败。后续的播放器事件（就绪/报错）仍然追加到同一条链。 */
    fun finish(result: String, status: Status = Status.OK) = synchronized(lock) {
        val chain = chains[focusKey] ?: return@synchronized
        finishLocked(chain, result, status)
    }

    /** 同 [finish]，但归属指定曲目。 */
    fun finishFor(songId: String, result: String, status: Status = Status.OK) = synchronized(lock) {
        if (songId.isBlank()) return@synchronized
        finishLocked(chainOf(songId), result, status)
    }

    /**
     * 解析成功：只更新结果摘要（菜单行状态 / 面板头部），不再追加一行。
     * 命中哪条分支、哪个域名，前面「官方接口命中 / 第三方音源命中 / 脚本命中」已经写过，
     * 再补一行「已获取真实播放链接」只是把同一个 url 重复播报第三遍。
     */
    fun resolved(result: String) = synchronized(lock) {
        val chain = chains[focusKey] ?: return@synchronized
        chain.outcome = result
        chain.finished = true
        publish(chain)
    }

    fun resolvedFor(songId: String, result: String) = synchronized(lock) {
        if (songId.isBlank()) return@synchronized
        val chain = chainOf(songId)
        chain.outcome = result
        chain.finished = true
        publish(chain)
    }

    private fun finishLocked(chain: Chain, result: String, status: Status) {
        chain.outcome = result
        chain.finished = status != Status.FAIL
        if (chain.steps.size >= MAX_STEPS) chain.steps.removeAt(0)
        chain.steps += Step(
            name = if (status == Status.FAIL) "解析失败" else "已获取真实播放链接",
            detail = result,
            atMs = if (chain.startedAt == 0L) 0 else SystemClock.elapsedRealtime() - chain.startedAt,
            status = status,
        )
        publish(chain)
    }

    /** 用户手动「重新解析当前音乐」：清空这首歌的链，等下一次解析重新打点。 */
    fun reset(songId: String = "") = synchronized(lock) {
        val key = keyOf(songId.ifBlank { focusKey })
        if (key.isBlank()) {
            chains.clear()
            focusKey = ""
            publish(null)
            return@synchronized
        }
        val chain = Chain(key)
        // 清空但保留起点：紧跟其后的「重新解析当前音乐」打点仍算这一轮的开头。
        chain.startedAt = SystemClock.elapsedRealtime()
        chain.forceRestart = true
        chains[key] = chain
        focusKey = key
        publish(chain)
    }

    fun snapshot(): Snapshot = synchronized(lock) { build(chains[focusKey]) }

    private fun publish(chain: Chain?) {
        // 只有「面板正在看的那条链」才推送更新：预取写的是下一首的链，
        // 边听这首边把面板切到下一首是错的。
        val key = chain?.key.orEmpty()
        if (key.isNotEmpty() && key != focusKey) return
        _state.value = build(chain)
    }

    private fun build(chain: Chain?): Snapshot = if (chain == null) {
        Snapshot()
    } else Snapshot(
        songId = chain.songId,
        quality = chain.quality,
        route = chain.route,
        outcome = chain.outcome,
        totalMs = if (chain.startedAt == 0L) 0L else SystemClock.elapsedRealtime() - chain.startedAt,
        steps = chain.steps.toList(),
        finished = chain.finished,
    )
}
