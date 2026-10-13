package com.rcmiku.ncmapi.api

/**
 * LX 脚本音源桥：WebView 沙盒在 app 模块，ncmapi 拿不到 Context，
 * 由 app 侧在 Application 里注入一个挂起解析函数。
 *
 * 与 AudioSources 三选一里的 LX 选项配套，
 * 不额外引入优先级概念——选中 LX 就只走 LX，选中直连六源就只走直连。
 */
object LxSourceBridge {

    /**
     * 解析直链。参数为 NCM songId（纯数字，已剥掉 ?fee=&pl=）与音质档位 value。
     * 未注入（app 未初始化）或无插件可用时返回 null，交回 PlayerApi 的兜底。
     */
    var resolver: (suspend (songId: String, songLevel: String) -> String?)? = null

    val available: Boolean get() = resolver != null
}