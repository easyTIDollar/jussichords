package com.rcmiku.ncmapi.utils

object CookieProvider {
    private var cookieMap: Map<String, String> = emptyMap()
    var cookie: String = ""
        private set

    fun init(cookieMap: Map<String, String>) {
        this.cookieMap = buildMap {
            putAll(cookieMap)
            put(CookieKeys.OS, "android")
            put(CookieKeys.APP_VER, "9.4.32.251222163637")
            put("channel", "xiaomi")
            put("versioncode", "6006066")
            put("resolution", "2268x1080")
        }
        this.cookie = this.cookieMap.entries.joinToString("; ") { (k, v) -> "$k=$v" }
    }

    fun clear() {
        cookieMap = emptyMap()
        cookie = ""
    }

    fun getCookieMap(): Map<String, String> = cookieMap

    fun isLoggedIn(): Boolean = cookieMap.isNotEmpty() && cookieMap.containsKey("MUSIC_U")

    fun hasCsrf(): Boolean = cookieMap.containsKey("__csrf")
}
