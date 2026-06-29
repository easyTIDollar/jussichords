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
import com.jussicodes.music.constants.PlaylistThumbnailSize
import com.jussicodes.music.constants.ThumbnailCornerRadius
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

@Composable
fun PlaylistThumbnailImage(url: Any?, modifier: Modifier = Modifier) =
    AsyncImage(
        model = url.toCoverImageUrl(CoverImageSize.DETAIL),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .clip(RoundedCornerShape(ThumbnailCornerRadius))
            .size(PlaylistThumbnailSize)
    )

@Composable
fun RadioThumbnailImage(url: Any?, modifier: Modifier = Modifier) =
    AsyncImage(
        model = url.toCoverImageUrl(CoverImageSize.DETAIL),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .clip(CircleShape)
            .size(PlaylistThumbnailSize)
    )
