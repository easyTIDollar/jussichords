package com.jussicodes.music.utils

import androidx.core.net.toUri
import kotlin.math.roundToInt

/**
 * 封面请求尺寸。
 *
 * 以前这里是写死的像素值（LIST=128、GRID=256…），在 3x 屏上 128dp 的缩略图只有 256px
 * 可用 → 必然被放大插值，观感发糊；低密屏又白下载多余像素。现在统一用 dp 描述视觉尺寸，
 * 由 [pixels] 按当前屏幕密度换算成 NCM `?param=NyN` 的边长。
 *
 * [fallbackPixels] 只在拿不到密度（density <= 0）时使用，取值与改造前一致。
 */
enum class CoverImageSize(val dp: Int, val fallbackPixels: Int) {
    LIST(48, 128),
    GRID(128, 256),
    DETAIL(200, 512),
    LARGE(390, 1000),
    HERO(300, 768);

    /** 按屏幕密度换算后的请求像素边长。 */
    fun pixels(density: Float): Int {
        if (density <= 0f) return fallbackPixels
        return (dp * density).roundToInt().coerceIn(64, 2048)
    }
}

fun Any?.toCoverImageUrl(size: CoverImageSize, density: Float = 0f): Any? =
    when (this) {
        is String -> this.toCoverImageUrl(size, density)
        else -> this
    }

fun String?.toCoverImageUrl(size: CoverImageSize, density: Float = 0f): String? {
    if (this.isNullOrBlank()) return this

    val uri = this.toUri()
    val host = uri.host.orEmpty()
    if (!host.contains("music.126.net")) return this

    val builder = uri.buildUpon().clearQuery()
    uri.queryParameterNames
        .filterNot { it == "param" }
        .forEach { name ->
            uri.getQueryParameters(name).forEach { value ->
                builder.appendQueryParameter(name, value)
            }
        }
    val px = size.pixels(density)
    builder.appendQueryParameter("param", "${px}y${px}")
    return builder.build().toString()
}

/**
 * Gives an avatar a new Coil cache key after it has been replaced on the server.
 * The music CDN can serve an updated avatar from an unchanged path.
 */
fun String?.withAvatarCacheBuster(version: Long): String? {
    if (this.isNullOrBlank() || version <= 0) return this
    return this.toUri()
        .buildUpon()
        .appendQueryParameter("avatarVersion", version.toString())
        .build()
        .toString()
}

fun String?.withPlaylistCoverCacheBuster(version: Long): String? {
    if (this.isNullOrBlank() || version <= 0) return this
    return this.toUri()
        .buildUpon()
        .appendQueryParameter("playlistCoverVersion", version.toString())
        .build()
        .toString()
}
