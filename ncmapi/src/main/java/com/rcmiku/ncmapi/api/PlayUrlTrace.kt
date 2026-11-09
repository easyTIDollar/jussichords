package com.rcmiku.ncmapi.api

/**
 * 可视化播放解析的埋点出口。
 *
 * ncmapi 只负责把「这次解析走了哪条分支、代价多少」吐出来，怎么展示由 app 决定
 * （app 侧把 sink 接到 `PlaybackPath`，播放页的「可视化播放解析」面板就是它的回放）。
 * 默认 sink 为空，不做任何事 —— 单元测试/纯 ncmapi 使用时零开销。
 *
 * 每条事件都带 songId：解析不只为「正在播的那首」服务，下一首预取也会走同一条链路，
 * 没有 songId 就没法把它们分开，面板会被别的歌的事件污染。
 */
object PlayUrlTrace {

    /** (songId, 步骤名, 说明, 标签)。 */
    var sink: ((String, String, String, List<String>) -> Unit)? = null

    fun emit(songId: String, name: String, detail: String = "", tags: List<String> = emptyList()) {
        runCatching { sink?.invoke(songId, name, detail, tags) }
    }

    /** 直链只露域名：面板里没必要（也不应该）铺开一次性 token。 */
    fun hostOf(url: String?): String {
        val raw = url?.takeIf { it.isNotBlank() } ?: return ""
        return runCatching { java.net.URI(raw).host ?: raw.take(40) }.getOrDefault(raw.take(40))
    }
}
