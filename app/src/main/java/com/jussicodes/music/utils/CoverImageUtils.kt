package com.jussicodes.music.utils

import androidx.core.net.toUri

enum class CoverImageSize(val pixels: Int) {
    LIST(128),
    GRID(256),
    DETAIL(512),
    LARGE(1000),
    HERO(768),
}

fun Any?.toCoverImageUrl(size: CoverImageSize): Any? =
    when (this) {
        is String -> this.toCoverImageUrl(size)
        else -> this
    }

fun String?.toCoverImageUrl(size: CoverImageSize): String? {
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
    builder.appendQueryParameter("param", "${size.pixels}y${size.pixels}")
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
