package com.rcmiku.ncmapi.utils

object CookieProvider {
    private var cookieMap: Map<String, String> = emptyMap()
    var cookie: String = ""
        private set

    fun init(cookieMap: Map<String, String>) {
        this.cookieMap = cookieMap
        this.cookie = cookieMap.entries.joinToString("; ") { (k, v) -> "$k=$v" }
    }

    fun getCookieMap(): Map<String, String> = cookieMap

    fun isLoggedIn(): Boolean = cookieMap.isNotEmpty() && cookieMap.containsKey("MUSIC_U")
}
