package com.rcmiku.ncmapi.utils

object UserAgentProvider {
    var userAgent: String = ""
        private set

    fun init(ua: String) {
        userAgent = ua
    }
}
