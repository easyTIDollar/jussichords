package com.rcmiku.ncmapi.utils

import java.io.File

object FileProvider {
    var cacheDir: File? = null
        private set

    fun init(dir: File) {
        if (!dir.exists()) dir.mkdirs()
        cacheDir = dir
    }
}
