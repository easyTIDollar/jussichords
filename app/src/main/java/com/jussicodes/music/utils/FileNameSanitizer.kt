package com.jussicodes.music.utils

internal fun sanitizeFileName(name: String): String {
    return name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
}
