package com.rcmiku.ncmapi.utils

object CookieProvider {
    private var cookieMap: Map<String, String> = emptyMap()
    var cookie: String = ""
        private set

    fun init(cookieMap: Map<String, String>) {
        this.cookieMap = cookieMap + (CookieKeys.OS to "pc")
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
