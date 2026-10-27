package com.rcmiku.ncmapi.api

/**
 * 本地直连音源定义：客户端直接向第三方公开接口发起 GET 获取 NCM 歌曲直链，
 * 不经代理、不经 NCM 加密（照抄 Melodia 的 UnmModule / NativeSourceFetcher 六源）。
 *
 * UI 选择器与 PlayerApi 共用这一份选项，避免两处漂移（同 UnblockSources 模式）。
 */
object LocalSources {

    data class Option(
        val value: String,
        val label: String,
        val description: String = "",
        val maxQuality: String = ""
    )

    /** 「直连音源」档位的默认源（第一个选项）。 */
    const val DEFAULT = "byfuns"

    /**
     * 「AUTO」：按 [DIRECT_KEYS] 顺序逐个尝试，命中即停（原有行为）。
     * 具体 key：只试该源，命中即用，未命中直接落官方兜底。
     */
    const val AUTO = "AUTO"

    /** 内置直连源按顺序逐个尝试，命中即停（照 Melodia 的六源探测）。 */
    val DIRECT_KEYS: List<String> = listOf("byfuns", "ddyr", "gdmusic", "oi", "qijieya", "msls")

    /** 直连源选择器（UI 选择器与 PlayerApi 共用这一份，避免两处漂移）。 */
    val OPTIONS: List<Option> = listOf(
        Option(AUTO, "自动选择", "按内置顺序逐个尝试，命中即用", "最高 Hi-Res"),
        Option("byfuns", "Byfuns", "第三方高品质直连解析", "最高 Hi-Res"),
        Option("ddyr", "Ddyr", "第三方高品质直连解析", "最高 Hi-Res"),
        Option("gdmusic", "Gdmusic", "GD 音乐台直链解析", "最高 无损"),
        Option("oi", "Oi", "OiAPI 网易云直链解析", "最高 极高 (320k)"),
        Option("qijieya", "Qijieya", "七街 Meting 直连解析", "最高 极高 (320k)"),
        Option("msls", "Msls", "聚合直连解析", "最高 极高 (320k)"),
    )

    fun fromKey(key: String): Option? = OPTIONS.find { it.value.equals(key, ignoreCase = true) }
}
