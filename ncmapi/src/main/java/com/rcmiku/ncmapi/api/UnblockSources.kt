package com.rcmiku.ncmapi.api

/**
 * 解灰音源定义：UI 选择器与 PlayerApi 回退链共用的单一来源，避免两处漂移。
 *
 * 服务端存在两套引擎（升级时发生破坏性变更，名字互不通用）：
 *  - 新版 neteasecloudmusicapi-enhanced ≥4.40（unblockmusic-utils）：
 *    只认 modules/ 目录里的 source：gdmusic/bugpk/byfuns/ddyr/msls/oi/qijieya/unm
 *  - 旧版 ≤4.36（UNM server @unblockneteasemusic/server）：
 *    认 provider/ 里的 source，默认顺序 pyncmd,bodian,kuwo,kugou
 *
 * gdmusic 与 pyncmd 是同一上游（GD studio）但名字不同，选错一族时
 * 靠 PlayerApi 的回退链（两族并集）兜底出歌。
 */
object UnblockSources {
    /** 不指定 source，交给服务端默认行为（两版通用）。 */
    const val AUTO = "AUTO"

    data class Option(val value: String, val label: String)

    /** 新版服务端（unblockmusic-utils）可加载的 source。 */
    val NEW_OPTIONS = listOf(
        Option("gdmusic", "gdmusic（新版）"),
        Option("bugpk", "bugpk（新版）"),
        Option("byfuns", "byfuns（新版）"),
        Option("ddyr", "ddyr（新版）"),
        Option("msls", "msls（新版）"),
        Option("oi", "oi（新版）"),
        Option("qijieya", "qijieya（新版）"),
        Option("unm", "unm（新版）"),
    )

    /** 旧版服务端（UNM server）支持的 source（默认顺序，精选 4 个）。 */
    val OLD_OPTIONS = listOf(
        Option("pyncmd", "pyncmd（旧版）"),
        Option("bodian", "bodian（旧版）"),
        Option("kuwo", "kuwo（旧版）"),
        Option("kugou", "kugou（旧版）"),
    )

    /** UI 选择器列表：AUTO 优先，随后新版组、旧版组。 */
    val ALL_OPTIONS = listOf(Option(AUTO, "自动选择音源（通用）")) + NEW_OPTIONS + OLD_OPTIONS

    /** PlayerApi 回退链（AUTO 不参与遍历）。 */
    val FALLBACK_VALUES = (NEW_OPTIONS + OLD_OPTIONS).map { it.value }
}
