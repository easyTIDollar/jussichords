package com.rcmiku.ncmapi.api

/**
 * 本地直连音源定义：客户端直接向第三方公开接口发起 GET 获取 NCM 歌曲直链，
 * 不经代理、不经 NCM 加密（照抄 Melodia 的 UnmModule / NativeSourceFetcher 六源）。
 *
 * UI 选择器与 PlayerApi 共用这一份选项，避免两处漂移（同 UnblockSources 模式）。
 */
object LocalSources {
    /** 关闭本地直连：维持现状走 API 代理解灰链。 */
    const val OFF = "OFF"

    data class Option(val value: String, val label: String, val description: String = "")

    /** 本地源 key（单个源直连选项的取值；「关闭」为 OFF）。 */
    val LOCAL_KEYS = listOf("byfuns", "ddyr", "gdmusic", "oi", "qijieya", "msls")

    /** UI 选择器列表：关闭 + 单个源（无"智能切换"；默认源 byfuns = 列表第一个非关闭项）。 */
    val OPTIONS = listOf(
        Option(OFF, "关闭", "不使用本地直连，维持 API 代理解灰"),
        Option("byfuns", "Byfuns", "网易云高品质/无损直连解析"),
        Option("ddyr", "Ddyr", "网易云高品质直连解析"),
        Option("gdmusic", "Gdmusic", "GD 音乐台直连解析"),
        Option("oi", "Oi", "OiAPI 网易云直链解析"),
        Option("qijieya", "Qijieya", "七街 Meting 网易云解析"),
        Option("msls", "Msls", "聚合直连解析"),
    )

    /** 本地源默认值：第一个非关闭选项（byfuns）。 */
    const val DEFAULT = "byfuns"

    fun fromKey(key: String): Option? = OPTIONS.find { it.value.equals(key, ignoreCase = true) }
}

/** 运行期设置，由 App 侧从 datastore 同步（同 UNBLOCK_SOURCE 模式）。 */
object LocalSourceSettings {
    var SONG_SOURCE: String = LocalSources.OFF
}
