package com.jussicodes.music.playback

import java.io.IOException

/** 候选源解析完毕仍无可播地址，与网络故障分开处理。 */
internal class NoPlayableSourceException(val songId: String) :
    IOException("这首暂无可播源：$songId")

/** 播放器可能多层包装数据源异常；同时防止异常原因链成环。 */
internal fun Throwable.findNoPlayableSource(): NoPlayableSourceException? {
    var current: Throwable? = this
    val visited = mutableSetOf<Throwable>()
    while (current != null && visited.add(current)) {
        if (current is NoPlayableSourceException) return current
        current = current.cause
    }
    return null
}
