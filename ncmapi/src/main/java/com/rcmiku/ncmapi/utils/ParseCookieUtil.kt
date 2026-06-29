package com.rcmiku.ncmapi.utils

fun parseCookieString(cookie: String): Map<String, String> {
    return cookie.split(";")
        .mapNotNull { part ->
            val trimmed = part.trim()
            val eqIndex = trimmed.indexOf('=')
            if (eqIndex > 0) {
                val key = trimmed.substring(0, eqIndex).trim()
                val value = trimmed.substring(eqIndex + 1).trim()
                if (key.isNotEmpty()) key to value else null
            } else null
        }
        .toMap()
}
