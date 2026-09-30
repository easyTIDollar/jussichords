package com.rcmiku.ncmapi.api

import com.rcmiku.ncmapi.model.CompletedPlaybackSession

/**
 * eapi feedback/weblog 基础字段（对齐 MeiloX playbackHistoryBaseFields）：
 * - startlogtime 用开始时刻的 epoch 秒（原生 BI 协议），logtime 保留事件毫秒。
 * - source/sourceId 不可靠时回落 track/songId（唯一安全兜底，NCM 端可归因到歌曲本身）。
 */
private val PLAYBACK_SOURCES = setOf("list", "album", "artist", "track")

internal fun playbackHistoryBaseFields(
    songId: Long,
    sourceId: Long,
    source: String,
    startedAtMs: Long,
    loggedAtMs: Long
): Map<String, Any> {
    require(songId > 0L) { "songId must be positive" }
    val knownSource = source.takeIf { it in PLAYBACK_SOURCES }
    val hasReliableSource = sourceId > 0L && knownSource != null
    val safeSourceId = if (hasReliableSource) sourceId else songId
    val safeSource = knownSource.takeIf { hasReliableSource } ?: "track"
    return mapOf(
        "id" to songId.toString(),
        "type" to "song",
        "startlogtime" to startedAtMs / 1_000L,
        "logtime" to loggedAtMs,
        "sourceId" to safeSourceId.toString(),
        "source" to safeSource,
        "sourcetype" to safeSource,
        "mainsite" to "1",
        "mainsiteWeb" to "1",
        "content" to "id=$safeSourceId",
    )
}

internal fun playbackHistoryPlayFields(
    songId: Long,
    sourceId: Long,
    source: String,
    timeSeconds: Long,
    startedAtMs: Long,
    endedAtMs: Long,
    endReason: String
): Map<String, Any> =
    playbackHistoryBaseFields(songId, sourceId, source, startedAtMs, endedAtMs) +
        mapOf(
            "download" to 0,
            "end" to endReason,
            "time" to timeSeconds.coerceAtLeast(0L),
            "wifi" to 0,
        )

/** 便捷重载：直接消费一次完成的会话记录。 */
internal fun playbackHistoryPlayFields(session: CompletedPlaybackSession): Map<String, Any> =
    playbackHistoryPlayFields(
        songId = session.songId,
        sourceId = session.sourceId,
        source = session.source,
        timeSeconds = session.playedDurationMs.coerceAtLeast(0L) / 1_000L,
        startedAtMs = session.startedAtMs,
        endedAtMs = session.endedAtMs,
        endReason = session.endReason,
    )
