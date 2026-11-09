package com.jussicodes.music.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.jussicodes.music.utils.CoverImageSize
import com.jussicodes.music.utils.coverMemoryKey
import com.jussicodes.music.utils.toCoverImageUrl

/**
 * 封面/头像统一请求入口。
 *
 * 三件事只在这里做，避免每个调用点各写一套：
 *  1. 按屏幕密度换算 `?param=NyN`（[CoverImageSize.pixels]），小图不会去拉原图；
 *  2. 请求尺寸写进 request，与 [coverMemoryKey] 的档位一致，内存条目不会跨档串图；
 *  3. 关掉单图 crossfade，命中缓存立刻出图；加载中/失败都铺一块同圆角的底色，
 *     不再出现「空白等图」和失败后的白块。
 *
 * 需要和预取（ExplorePreloader）命中同一条内存缓存时，两边都会调 [coverMemoryKey]。
 */
@Composable
fun CoverAsyncImage(
    url: Any?,
    size: CoverImageSize,
    modifier: Modifier = Modifier,
    shape: Shape? = null,
    contentScale: ContentScale = ContentScale.Crop,
    contentDescription: String? = null,
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val request = remember(url, size, density) {
        val px = size.pixels(density)
        ImageRequest.Builder(context)
            .data(url.toCoverImageUrl(size, density))
            .size(px, px)
            .memoryCacheKey(coverMemoryKey(url, size))
            .crossfade(false)
            .build()
    }
    val placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant)

    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        placeholder = placeholder,
        error = placeholder,
        contentScale = contentScale,
        modifier = (if (shape != null) modifier.clip(shape) else modifier),
    )
}

/** 固定 dp 尺寸的圆形/圆角头像，省得每个调用点重复写 size + clip。 */
@Composable
fun CoverAvatar(
    url: Any?,
    dp: Int,
    modifier: Modifier = Modifier,
    shape: Shape? = null,
    size: CoverImageSize = CoverImageSize.AVATAR,
) {
    val placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant)
    val ctx = LocalContext.current
    val density = LocalDensity.current.density
    val request = remember(url, size, density) {
        val px = size.pixels(density)
        ImageRequest.Builder(ctx)
            .data(url.toCoverImageUrl(size, density))
            .size(px, px)
            .memoryCacheKey(coverMemoryKey(url, size))
            .crossfade(false)
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = null,
        placeholder = placeholder,
        error = placeholder,
        contentScale = ContentScale.Crop,
        modifier = (if (shape != null) modifier.clip(shape) else modifier).size(dp.dp),
    )
}
