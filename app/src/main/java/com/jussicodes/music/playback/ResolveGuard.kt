package com.jussicodes.music.playback

import com.rcmiku.ncmapi.api.player.PlayUrlSkip

/**
 * 播放源校验状态：记住每首歌「这一次解析出来的直链」，以及被时长校验判定为错源而拉黑的直链。
 *
 * 为什么需要它：解析层（LX 脚本 / 内置直连 / 代理 / 官方兜底）只保证「返回了一个非空 url」，
 * 不等于「返回的是这首歌的完整音频」。典型坏源能正常开始播放，用户看到的现象却是
 * 「放一半没声 / 30 秒就断 / 部分歌这样，手动切下一首再切回来又好了」。
 * 所以播放器拿到真实音频时长后要核对一次，对不上就把这条链拉黑并换源重试。
 *
 * 约定：
 * - key 是解析用的字符串（`songId?fee=&pl=`），由 `MediaItemExt.toResolveKey()` 统一产出；
 * - 拉黑只影响客户端/代理音源的下一次解析：解析层拿到 skip 集合后逐个跳过，
 *   于是重试会自然升级到「下一个脚本 / 下一个直连源」，而不是把同一条坏链再刷一遍；
 * - 官方直连兜底不理会 skip（那是最后一道，没有更优解），所以真无源的歌最多重试
 *   [MAX_VERIFY_RETRIES] 次后接受现状，绝不无限重试；
 * - 状态只在内存里，进程重启即清空；条目数有上限，长时间播放不会无限增长。
 */
object ResolveGuard {

    /** 一首歌最多因为时长不符重来几次；用完就接受现状。 */
    const val MAX_VERIFY_RETRIES = 2

    /** 状态缓存上限；超出后从最旧的开始丢。 */
    private const val MAX_ENTRIES = 24

    private class State(val key: String) {
        val skipUrls = mutableSetOf<String>()
        var retries = 0
        var lastUrl: String? = null
        /** 最近这次交出去的直链本身就在拉黑集合里（说明已经没有更好的候选了）。 */
        var lastUrlAlreadyRejected = false
        /** 最近这次交出去的是试听片段。 */
        var lastWasTrial = false
        var warned = false
        var touchedAt = 0L
    }

    /** 一次解析结果快照，给播放器侧校验用。 */
    data class Observation(
        val key: String,
        val url: String,
        val alreadyRejected: Boolean,
        val retries: Int,
        /** 这次交出去的是 NCM 试听片段（30s 占位）。 */
        val trial: Boolean,
    )

    @Volatile
    private var clock = 0L

    private val states = LinkedHashMap<String, State>()

    private fun stateOf(key: String): State = synchronized(states) {
        states[key]?.let {
            it.touchedAt = ++clock
            return it
        }
        if (states.size >= MAX_ENTRIES) {
            states.minByOrNull { it.value.touchedAt }?.let { states.remove(it.key) }
        }
        State(key).also {
            it.touchedAt = ++clock
            states[key] = it
        }
    }

    /** 本次解析要跳过的直链（之前被判定为错源的那些）。 */
    fun skipUrls(key: String): Set<String> {
        val state = stateOf(key)
        return synchronized(state) { state.skipUrls.toSet() }
    }

    /** 记录这次真正交给了播放器的直链，供后续校验失败时拉黑。 */
    fun recordResolved(key: String, url: String, trial: Boolean = false) {
        val state = stateOf(key)
        synchronized(state) {
            state.lastUrl = url
            state.lastWasTrial = trial
            state.lastUrlAlreadyRejected = PlayUrlSkip.blocks(state.skipUrls, url)
        }
    }

    /**
     * 按 songId 找「最近一次解析结果」。
     *
     * 播放器侧只拿得到当前 MediaItem 的 mediaId（纯 songId），拿不到解析时用的原始 uri，
     * 所以这里按 songId 前缀反查。key 形如 `123456?fee=1&pl=320000`。
     */
    fun observe(songId: String): Observation? = synchronized(states) {
        states.values
            .filter { it.key == songId || it.key.startsWith("$songId?") }
            .maxByOrNull { it.touchedAt }
            ?.let { state ->
                val url = state.lastUrl ?: return null
                Observation(
                    key = state.key,
                    url = url,
                    alreadyRejected = state.lastUrlAlreadyRejected,
                    retries = state.retries,
                    trial = state.lastWasTrial,
                )
            }
    }

    /**
     * 时长校验失败：把这次用的直链拉黑。
     * @return true = 还值得换源重试；false = 候选已经试穿，接受现状。
     */
    fun rejectAndShouldRetry(observation: Observation): Boolean {
        val state = stateOf(observation.key)
        synchronized(state) {
            // 原串 + 去 query 的指纹都记上：直链常带一次性 token，只记原串等于没拉黑。
            state.skipUrls += PlayUrlSkip.forms(observation.url)
            state.retries += 1
            return state.retries <= MAX_VERIFY_RETRIES
        }
    }

    /** 只让「第一次判定失败」提示一次，别整首歌反复弹。 */
    fun shouldWarn(observation: Observation): Boolean {
        val state = stateOf(observation.key)
        synchronized(state) {
            if (state.warned) return false
            state.warned = true
            return true
        }
    }

    /** 换曲/重播：清掉这首歌的重试计数与提示标记，但保留已拉黑的坏链（坏源不会自愈）。 */
    fun resetRetries(songId: String) {
        val state = synchronized(states) {
            states.values
                .filter { it.key == songId || it.key.startsWith("$songId?") }
                .maxByOrNull { it.touchedAt }
        } ?: return
        synchronized(state) {
            state.retries = 0
            state.warned = false
        }
    }

    /**
     * 彻底忘掉一首歌的校验状态（含已拉黑的直链）。
     * 用户主动换音源时调用：换了源就应该按新源重新开始，不该继承上一轮的拉黑集合。
     */
    fun forgetSong(songId: String) {
        synchronized(states) {
            states.keys.removeAll { it == songId || it.startsWith("$songId?") }
        }
    }
}
