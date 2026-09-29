package com.jussicodes.music.utils

import com.rcmiku.ncmapi.utils.parseCookieString

/**
 * Cookie 登录态存储归一化（对齐 MeiloX：只存 MUSIC_U，运行时参数由请求层合成/兜底）。
 *
 * 输入三种形态自动识别：
 * - 完整 cookie 串（含 `;`，WebView/设置页抓出来的那种）→ 解析后提取核心键
 * - 单个 `k=v`（如 `MUSIC_U=xxx`，含 `=` 不含 `;`）→ 按 cookie 项解析
 * - 裸 MUSIC_U 值（无 `;` 无 `=`）→ 直接作为 MUSIC_U
 *
 * 输出固定 3 键：MUSIC_U（必需，缺失返回 null）+ __csrf（缺失用 MeiloX 生产常量兜底）
 * + deviceId（缺失用 getDeviceID() 生成，保证设备指纹跨进程稳定）。
 * os/appver/versioncode 等其余字段不存，由 CookieProvider.init 运行时补齐。
 */
object NcmCookieNormalizer {
    /** MeiloX NeteaseInterceptor.CONST_CSRF，生产验证可用。 */
    private const val FALLBACK_CSRF = "40ab38f0a305fc4c7ff68e636bcf34aa"

    /** 字符串入口：完整 cookie 串 / 单个 k=v / 裸 MUSIC_U 值。 */
    fun normalizeForStorage(raw: String): Map<String, String>? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        // 含 `;` 或含 `=`（单个 k=v，如 "MUSIC_U=xxx"）都走 cookie 项解析；
        // 纯 token（无 ; 无 =）才当裸 MUSIC_U 值。
        val map = if (trimmed.contains(";") || trimmed.contains("=")) parseCookieString(trimmed)
        else mapOf("MUSIC_U" to trimmed)
        return normalizeForStorage(map)
    }

    /** Map 入口：提取核心 3 键。 */
    fun normalizeForStorage(cookieMap: Map<String, String>): Map<String, String>? {
        val musicU = cookieMap["MUSIC_U"]?.takeIf { it.isNotBlank() } ?: return null
        return buildMap {
            put("MUSIC_U", musicU)
            put("__csrf", cookieMap["__csrf"]?.takeIf { it.isNotBlank() } ?: FALLBACK_CSRF)
            put("deviceId", cookieMap["deviceId"]?.takeIf { it.isNotBlank() } ?: getDeviceID())
        }
    }
}
