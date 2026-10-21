package com.jussicodes.music.animation

import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.ui.unit.IntSize
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

/**
 * 共享封面的统一身份与 bounds 配置。
 *
 * 背景：播放器此前用 `mediaMetadata.artist` 当共享 key，同一艺人的不同歌曲会撞 key，
 * 切歌时封面会"瞬移"而不是连续变形。这里统一改为媒体本身的稳定标识。
 */
object SharedArtwork {

    /**
     * 播放中封面的共享 key。
     *
     * 优先级：mediaId → 规范化 URI → 标题+艺人兜底。
     * 同一首歌在队列/详情/播放器之间应当得到同一个 key，否则共享元素会退化成淡入淡出。
     */
    fun playerArtworkKey(metadata: MediaMetadata): String {
        val fromId = metadata.mediaId?.takeIf { it.isNotBlank() }
        if (fromId != null) return "artwork-media-$fromId"

        val fromUri = metadata.uri?.toString()?.takeIf { it.isNotBlank() }
        if (fromUri != null) return "artwork-uri-${normalizeUri(fromUri)}"

        val fallback = listOfNotNull(
            metadata.title?.takeIf { it.isNotBlank() },
            metadata.artist?.takeIf { it.isNotBlank() },
        ).joinToString("-")
        return "artwork-fallback-$fallback"
    }

    /** 从队列项取 key（queue item 可能只有 mediaId / uri，没有 metadata）。 */
    fun playerArtworkKey(item: MediaItem): String {
        val fromId = item.mediaId.takeIf { it.isNotBlank() }
        if (fromId != null) return "artwork-media-$fromId"
        val fromUri = item.requestUri.toString().takeIf { it.isNotBlank() }
        if (fromUri != null) return "artwork-uri-${normalizeUri(fromUri)}"
        return "artwork-fallback-${item.mediaMetadata.title.orEmpty()}"
    }

    /**
     * 归一化 URI：去掉可能随播放进度/音质变化而变动的查询参数，
     * 只保留 stable 的身份部分，避免同一首歌在不同请求下得到不同 key。
     */
    private fun normalizeUri(raw: String): String = runCatching {
        val uri = Uri.parse(raw)
        val stable = uri.buildUpon().clearQuery().fragment(null).build().toString()
        stable.ifBlank { raw }
    }.getOrDefault(raw)
}

/**
 * 播放面板（mini ↔ full ↔ queue ↔ lyric）共用的 bounds 变换。
 *
 * copy from https://gist.github.com/JunkFood02/caf2af3cee41f847c0ad0bcf4d0cf9d8
 * Player 与 RoamScreen 此前各有一份等价实现，现统一到这里。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
val AlbumArtBoundsTransform: BoundsTransform = BoundsTransform { _, _ ->
    MotionSpec.sharedBounds()
}

/** mini 封面：宽度取目标尺寸，高度跟随动画值插值。 */
@OptIn(ExperimentalSharedTransitionApi::class)
val MiniArtworkPlaceHolder: (IntSize, IntSize) -> IntSize =
    { contentSize, animatedSize -> IntSize(contentSize.width, animatedSize.height) }

/** 面板容器：按目标尺寸整体形变（mini 展开成全屏）。 */
@OptIn(ExperimentalSharedTransitionApi::class)
val PanelBoundsPlaceHolder = SharedTransitionScope.PlaceHolderSize.animatedSize