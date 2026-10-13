package com.rcmiku.ncmapi.api

/**
 * 三选一音源选择的唯一来源：LX 脚本 / 内置直连 / 代理解灰。
 *
 * 三个选项互斥——玩家只会按当前选择走一条路，不做链式回退：
 *  - [LX]：客户端 WebView 沙盒跑脚本（由 app 侧 LxPluginManager 执行，见 LxSourceBridge）
 *  - [DIRECT]：客户端 GET 内置第三方直连接口（LocalSourceFetcher）
 *  - [PROXY]：把 NCM 请求转给自建 api-enhanced 服务器解灰
 *
 * UI 选择器与 PlayerApi 共用这一份，避免两处漂移（同 UnblockSources 模式）。
 */
object AudioSources {
    /** LX 脚本音源：app 侧沙盒执行用户/内置 JS 脚本。 */
    const val LX = "lx"

    /** 内置直连：客户端直接 GET 第三方公开接口。 */
    const val DIRECT = "direct"

    /** 代理解灰：由自建 api-enhanced 服务器代解。 */
    const val PROXY = "proxy"

    /** 新装/升级后的默认选择（用户明确要求：新版本一律回到内置 LX 脚本）。 */
    const val DEFAULT = LX

    data class Option(val value: String, val label: String, val description: String = "")

    val OPTIONS = listOf(
        Option(LX, "LX音源", "运行 JS 脚本解析"),
        Option(DIRECT, "直连音源", "请求内置解析接口"),
        Option(PROXY, "API代理服务器", "由自建服务器代解"),
    )

    fun fromKey(key: String): Option? = OPTIONS.find { it.value == key }
}

/** 运行期设置，由 App 侧从 datastore 同步（同 UNBLOCK_SOURCE 模式）。 */
object AudioSourceSettings {
    /** 当前选择的音源（AudioSources 三选一之一）。 */
    var SELECTED: String = AudioSources.DEFAULT
}