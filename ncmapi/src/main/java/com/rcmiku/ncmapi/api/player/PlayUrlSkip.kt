package com.rcmiku.ncmapi.api.player

/**
 * 「被播放器时长校验判定为错源」的直链集合的匹配规则。
 *
 * 只按原样比较 url 不够用：LX 脚本 / 直连云给出的直链大多带一次性 token 或时间戳 query，
 * 同一根坏链每次解析出来的 url 字符串都不一样，精确比较等于没拉黑（重试仍在原地打转）。
 * 所以匹配按「去 query/fragment 的指纹」比较：`scheme://host/path` 相同即视为同一条音源。
 *
 * 取舍：同源不同 token 会被一起跳过（这正是想要的）；不同音源碰巧共用同一 host+path
 * 骨架时会多跳一个候选，最多是少试一个源，代价可控。
 */
object PlayUrlSkip {

    /** 去 query/fragment 的指纹；不是 http(s) 链接返回 null。 */
    fun fingerprint(url: String): String? {
        val trimmed = url.trim()
        if (!trimmed.startsWith("http://", ignoreCase = true) &&
            !trimmed.startsWith("https://", ignoreCase = true)
        ) {
            return null
        }
        return trimmed.substringBefore('#').substringBefore('?').ifBlank { null }
    }

    /** 写入拉黑集合时用的两种形态：原串 + 指纹。 */
    fun forms(url: String): List<String> = listOfNotNull(url, fingerprint(url)).distinct()

    /** 候选直链是否已经被播放器侧判定为错源（先比原串，再比指纹）。 */
    fun blocks(skipUrls: Set<String>, candidate: String?): Boolean {
        if (candidate.isNullOrBlank() || skipUrls.isEmpty()) return false
        if (skipUrls.contains(candidate)) return true
        val fingerprint = fingerprint(candidate) ?: return false
        if (skipUrls.contains(fingerprint)) return true
        // 集合里可能只存了带 token 的原串（旧记录），再按指纹兜一遍。
        return skipUrls.any { fingerprint(it) == fingerprint }
    }
}
