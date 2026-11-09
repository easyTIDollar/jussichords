package com.jussicodes.music.utils

import android.content.Context
import androidx.core.net.toUri
import kotlin.math.roundToInt

/**
 * 进程级屏幕密度。
 *
 * [toCoverImageUrl] 的 density 以前默认 0f，调用方忘了传就退回写死的像素值
 * （LIST=128、GRID=256…），高密屏上等于把「按密度换算」白做了。现在由
 * Application 启动时灌一次真实密度，所有调用点默认就走正确档位：
 * 低密屏不再白下多余像素，高密屏也不会拿小图拉伸。
 */
object CoverDensity {
    @Volatile
    var value: Float = 0f
        private set

    fun init(context: Context) {
        value = context.resources.displayMetrics.density
    }
}

/**
 * 封面请求尺寸。
 *
 * 以 dp 描述视觉尺寸，由 [pixels] 按屏幕密度换算成 NCM `?param=NyN` 的边长。
 * 档位越细，同一张封面在磁盘上的副本越多，所以只保留实际用到的几档：
 * LIST 列表缩略图 48dp / AVATAR 头像 32dp / GRID 网格 128dp /
 * DETAIL 详情头图 200dp / LARGE 大图 390dp / HERO 歌手头图 300dp。
 *
 * [fallbackPixels] 只在拿不到密度（density <= 0）时使用。
 */
enum class CoverImageSize(val dp: Int, val fallbackPixels: Int) {
    LIST(48, 128),
    AVATAR(32, 96),
    MEDIUM(96, 256),
    GRID(128, 256),
    DETAIL(200, 512),
    LARGE(390, 1000),
    HERO(300, 768);

    /** 按屏幕密度换算后的请求像素边长。 */
    fun pixels(density: Float = CoverDensity.value): Int {
        if (density <= 0f) return fallbackPixels
        return (dp * density).roundToInt().coerceIn(64, 2048)
    }
}

fun Any?.toCoverImageUrl(
    size: CoverImageSize,
    density: Float = CoverDensity.value,
): Any? =
    when (this) {
        is String -> this.toCoverImageUrl(size, density)
        else -> this
    }

fun String?.toCoverImageUrl(
    size: CoverImageSize,
    density: Float = CoverDensity.value,
): String? {
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
 * 统一的 Coil 内存缓存 key。
 *
 * 用「去掉尺寸参数的图片地址 + 档位」而不是完整 URL：同一封面即使被不同调用点
 * 用不同 `?param=` 请求（磁盘上是不同条目），内存里仍然只有一条；档位参与 key，
 * 避免 48dp 缩略图的位图被拿去当 390dp 大图用（放大糊）。
 *
 * 预取（ExplorePreloader）和实际展示必须用同一个 key，否则预取只省了网络、
 * 首帧仍要重新解码 —— 这正是「封面还是慢半拍」的来源。
 */
fun coverMemoryKey(url: Any?, size: CoverImageSize): String? {
    val raw = when (url) {
        null -> return null
        is String -> url
        else -> url.toString()
    }
    if (raw.isBlank()) return null
    return "cover:${size.name}:${stripSizeParam(raw)}"
}

/** 只剔除 NCM 的 `?param=NyN`，其余 query（头像/歌单版本号）保留，换图后才能立即失效。 */
private fun stripSizeParam(raw: String): String {
    if (!raw.startsWith("http", ignoreCase = true)) return raw
    return runCatching {
        val uri = raw.toUri()
        val builder = uri.buildUpon().clearQuery()
        uri.queryParameterNames
            .filterNot { it == "param" }
            .forEach { name ->
                uri.getQueryParameters(name).forEach { value ->
                    builder.appendQueryParameter(name, value)
                }
            }
        builder.build().toString()
    }.getOrDefault(raw)
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
