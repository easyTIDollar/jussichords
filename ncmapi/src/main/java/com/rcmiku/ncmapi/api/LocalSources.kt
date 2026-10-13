package com.rcmiku.ncmapi.api

/**
 * 本地直连音源定义：客户端直接向第三方公开接口发起 GET 获取 NCM 歌曲直链，
 * 不经代理、不经 NCM 加密（照抄 Melodia 的 UnmModule / NativeSourceFetcher 六源）。
 *
 * UI 选择器与 PlayerApi 共用这一份选项，避免两处漂移（同 UnblockSources 模式）。
 */
object LocalSources {

    /** 内置直连源按顺序逐个尝试，命中即停（照 Melodia 的六源探测）。 */
    val DIRECT_KEYS: List<String> = listOf("byfuns", "ddyr", "gdmusic", "oi", "qijieya", "msls")
}
