package com.jussicodes.music.playback

import androidx.media3.common.MediaItem
import com.jussicodes.music.constants.MediaSessionConstants

/**
 * NCM 播放历史来源上下文（对齐 MeiloX PlaybackHistorySource 的降级语义）：
 * source/sourceId 不可靠时回落 track/songId（NCM 可归因到歌曲本身的唯一安全值）。
 *
 * jussichords 的 MediaItem extras（MediaSessionConstants）里带了 source_id / source_name /
 * source_type，比 MeiloX（永远 track）更丰富，能报出真实的歌单/专辑/歌手来源。
 */
data class PlaybackHistorySourceContext(
    val songId: Long,
    val sourceId: Long,
    val source: String
)

/** 从 MediaItem 解析出可靠的 (songId, sourceId, source)；mediaId 不是正数歌曲 id 时返回 null。 */
fun MediaItem.playbackHistorySourceContext(): PlaybackHistorySourceContext? {
    val songId = mediaId.toLongOrNull()?.takeIf { it > 0L } ?: return null
    val extras = mediaMetadata?.extras
    val sourceId = extras?.getLong(MediaSessionConstants.EXTRA_SOURCE_ID, 0L) ?: 0L
    val sourceName = extras?.getString(MediaSessionConstants.EXTRA_SOURCE_NAME).orEmpty()
    val sourceType = extras?.getString(MediaSessionConstants.EXTRA_SOURCE_TYPE).orEmpty()

    return when {
        sourceId > 0L && (sourceType == "album" || sourceName == "专辑") ->
            PlaybackHistorySourceContext(songId, sourceId, "album")
        sourceId > 0L && (sourceType == "artist" || sourceName == "歌手") ->
            PlaybackHistorySourceContext(songId, sourceId, "artist")
        // 歌单 / 每日推荐 / 列表页（NCM 里都属于 list 来源）
        sourceId > 0L && (sourceType == "list" || sourceType == "playlist" ||
            sourceName == "list" || sourceName == "歌单" || sourceName == "每日推荐" ||
            sourceName.isEmpty()) ->
            PlaybackHistorySourceContext(songId, sourceId, "list")
        // 电台 / 云盘 / 其他：来源 id 不是 NCM 认可的 list/album/artist 资源，
        // 按 MeiloX 语义回落到 track + songId。
        else -> PlaybackHistorySourceContext(songId, songId, "track")
    }
}
