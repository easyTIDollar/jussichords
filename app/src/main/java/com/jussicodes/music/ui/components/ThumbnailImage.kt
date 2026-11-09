package com.jussicodes.music.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.jussicodes.music.constants.GridThumbnailHeight
import com.jussicodes.music.constants.ListThumbnailSize
import com.jussicodes.music.constants.ThumbnailCornerRadius
import com.jussicodes.music.constants.detailCoverSize
import com.jussicodes.music.utils.CoverImageSize
import com.jussicodes.music.utils.coverMemoryKey
import com.jussicodes.music.utils.toCoverImageUrl

/**
 * 封面统一入口。
 *
 * 两点和以前不同：
 *  1. 请求尺寸按屏幕密度换算（[CoverImageSize.pixels]），高密屏不再把 256px 的图拉伸到 384px 显示。
 *  2. 有占位色块、且关掉单图 crossfade —— 命中缓存时直接出图，不再"淡入 100ms"；
 *     未命中时先有一块和圆角一致的底色，观感上不再是"空白等图"。
 */
@Composable
private fun coverModel(url: Any?, size: CoverImageSize): Any? {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    return remember(url, size, density) {
        ImageRequest.Builder(context)
            .data(url.toCoverImageUrl(size, density))
            .size(size.pixels(density), size.pixels(density))
            // 与 ExplorePreloader 的预取共用同一把 key，预取的图才能直接被这里命中。
            .memoryCacheKey(coverMemoryKey(url, size))
            .crossfade(false)
            .build()
    }
}

@Composable
private fun coverPlaceholder() = ColorPainter(MaterialTheme.colorScheme.surfaceVariant)

@Composable
fun ListThumbnailImage(url: Any?, modifier: Modifier = Modifier) =
    AsyncImage(
        model = coverModel(url, CoverImageSize.LIST),
        contentDescription = null,
        placeholder = coverPlaceholder(),
        contentScale = ContentScale.Crop,
        modifier = modifier
            .clip(RoundedCornerShape(ThumbnailCornerRadius))
            .size(ListThumbnailSize)
    )

@Composable
fun GridThumbnailImage(url: Any?, modifier: Modifier = Modifier) =
    AsyncImage(
        model = coverModel(url, CoverImageSize.GRID),
        contentDescription = null,
        placeholder = coverPlaceholder(),
        contentScale = ContentScale.Crop,
        modifier = modifier
            .clip(RoundedCornerShape(ThumbnailCornerRadius))
            .size(GridThumbnailHeight)
    )

/**
 * 详情页大封面（歌单头图等）。
 *
 * 尺寸走 [detailCoverSize] 响应式：横屏手机矮屏（总高仅 411dp）压到 120dp，
 * 否则固定 200dp 会把首屏的歌曲列表整个挤到看不见。
 */
@Composable
fun PlaylistThumbnailImage(url: Any?, modifier: Modifier = Modifier) =
    AsyncImage(
        model = coverModel(url, CoverImageSize.DETAIL),
        contentDescription = null,
        placeholder = coverPlaceholder(),
        contentScale = ContentScale.Crop,
        modifier = modifier
            .clip(RoundedCornerShape(ThumbnailCornerRadius))
            .size(detailCoverSize())
    )

/** 电台页圆形大图，尺寸同 [PlaylistThumbnailImage]（走响应式）。 */
@Composable
fun RadioThumbnailImage(url: Any?, modifier: Modifier = Modifier) =
    AsyncImage(
        model = coverModel(url, CoverImageSize.DETAIL),
        contentDescription = null,
        placeholder = coverPlaceholder(),
        contentScale = ContentScale.Crop,
        modifier = modifier
            .clip(CircleShape)
            .size(detailCoverSize())
    )
