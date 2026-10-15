package com.jussicodes.music.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.jussicodes.music.constants.GridThumbnailHeight
import com.jussicodes.music.constants.ListThumbnailSize
import com.jussicodes.music.constants.ThumbnailCornerRadius
import com.jussicodes.music.constants.detailCoverSize
import com.jussicodes.music.utils.CoverImageSize
import com.jussicodes.music.utils.toCoverImageUrl

@Composable
fun ListThumbnailImage(url: Any?, modifier: Modifier = Modifier) =
    AsyncImage(
        model = url.toCoverImageUrl(CoverImageSize.LIST),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .clip(RoundedCornerShape(ThumbnailCornerRadius))
            .size(ListThumbnailSize)
    )

@Composable
fun GridThumbnailImage(url: Any?, modifier: Modifier = Modifier) =
    AsyncImage(
        model = url.toCoverImageUrl(CoverImageSize.GRID),
        contentDescription = null,
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
        model = url.toCoverImageUrl(CoverImageSize.DETAIL),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .clip(RoundedCornerShape(ThumbnailCornerRadius))
            .size(detailCoverSize())
    )

/** 电台页圆形大图，尺寸同 [PlaylistThumbnailImage]（走响应式）。 */
@Composable
fun RadioThumbnailImage(url: Any?, modifier: Modifier = Modifier) =
    AsyncImage(
        model = url.toCoverImageUrl(CoverImageSize.DETAIL),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .clip(CircleShape)
            .size(detailCoverSize())
    )