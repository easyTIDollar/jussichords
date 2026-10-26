package com.jussicodes.music.extensions

import android.net.Uri
import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.jussicodes.music.constants.MediaSessionConstants
import com.rcmiku.ncmapi.api.player.PlayerApi
import com.rcmiku.ncmapi.api.player.SongLevel
import com.jussicodes.music.utils.CoverImageSize
import com.jussicodes.music.utils.toCoverImageUrl
import com.rcmiku.ncmapi.model.CloudSong
import com.rcmiku.ncmapi.model.Radio
import com.rcmiku.ncmapi.model.Song
import com.rcmiku.ncmapi.model.SongUrl
import com.rcmiku.ncmapi.utils.json
import kotlinx.coroutines.delay

private fun Song.encodeUri(): String {
    val base = id.toString()
    val pl = privilege?.pl ?: 0
    return "$base?fee=$fee&pl=$pl"
}

fun Song.toMediaItem(
    sourceId: Long = 0L,
    sourceName: String = "list",
    sourceType: String? = null,
    navId: Long = 0L
) =
    MediaItem.Builder()
        .setUri(this.encodeUri())
        .setMediaId(this.id.toString())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setArtist(this.ar.joinToString("/") { it.name })
                .setTitle(this.name)
                .setArtworkUri(this.al.picUrl.toCoverImageUrl(CoverImageSize.DETAIL)?.toUri())
                .setExtras(Bundle().apply {
                    putString(
                        "song",
                        json.encodeToString(this@toMediaItem)
                    )
                    putLong(MediaSessionConstants.EXTRA_SOURCE_ID, sourceId)
                    putString(MediaSessionConstants.EXTRA_SOURCE_NAME, sourceName)
                    sourceType?.let { putString(MediaSessionConstants.EXTRA_SOURCE_TYPE, it) }
                    if (navId != 0L) putLong(MediaSessionConstants.EXTRA_NAV_ID, navId)
                    putLong(MediaSessionConstants.EXTRA_DURATION_MS, dt)
                })
                .build()
        )
        .build()


fun List<Song>.toMediaItemList(
    sourceId: Long = 0L,
    sourceName: String = "list",
    sourceType: String? = null,
    navId: Long = 0L
) =
    this.map { song ->
        val extras = Bundle().apply {
            putString("song", json.encodeToString(song))
            putLong(MediaSessionConstants.EXTRA_SOURCE_ID, sourceId)
            putString(MediaSessionConstants.EXTRA_SOURCE_NAME, sourceName)
            sourceType?.let { putString(MediaSessionConstants.EXTRA_SOURCE_TYPE, it) }
            if (navId != 0L) putLong(MediaSessionConstants.EXTRA_NAV_ID, navId)
            putLong(MediaSessionConstants.EXTRA_DURATION_MS, song.dt)
        }
        MediaItem.Builder()
            .setUri(song.encodeUri())
            .setMediaId(song.id.toString())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setArtist(song.ar.joinToString("/") { artist -> artist.name })
                    .setTitle(song.name)
                    .setArtworkUri(song.al.picUrl.toCoverImageUrl(CoverImageSize.DETAIL)?.toUri())
                    .setExtras(extras)
                    .build()
            )
            .build()
    }


fun List<CloudSong>.toCloudSongMediaItemList(
    uid: Long,
    sourceName: String = "cloud",
    sourceType: String? = null,
    navId: Long = 0L
) =
    this.map { cloudSong ->
        MediaItem.Builder()
            .setUri("${cloudSong.simpleSong.id}_$uid")
            .setMediaId(cloudSong.simpleSong.id.toString())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setArtist(cloudSong.artist)
                    .setTitle(cloudSong.simpleSong.name)
                    .setArtworkUri(
                        cloudSong.simpleSong.al?.picUrl.toCoverImageUrl(CoverImageSize.DETAIL)?.toUri()
                    )
                    .setExtras(Bundle().apply {
                        putString(MediaSessionConstants.EXTRA_SOURCE_NAME, sourceName)
                        sourceType?.let { putString(MediaSessionConstants.EXTRA_SOURCE_TYPE, it) }
                        if (navId != 0L) putLong(MediaSessionConstants.EXTRA_NAV_ID, navId)
                        putLong(MediaSessionConstants.EXTRA_DURATION_MS, cloudSong.simpleSong.dt)
                    })
                    .build()
            )
            .build()
    }

fun List<Radio>.toRadioMediaItemList(
    sourceName: String = "radio",
    sourceType: String? = null,
    navId: Long = 0L
) =
    this.map { radio ->
        MediaItem.Builder()
            .setUri(radio.mainSong.id.toString())
            .setMediaId(radio.mainSong.id.toString())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setArtist(radio.mainSong.artists.joinToString { it.name })
                    .setTitle(radio.mainSong.name)
                    .setArtworkUri(radio.coverUrl.toCoverImageUrl(CoverImageSize.DETAIL)?.toUri())
                    .setExtras(Bundle().apply {
                        putLong(MediaSessionConstants.EXTRA_SOURCE_ID, radio.id)
                        putString(MediaSessionConstants.EXTRA_SOURCE_NAME, sourceName)
                        sourceType?.let { putString(MediaSessionConstants.EXTRA_SOURCE_TYPE, it) }
                        if (navId != 0L) putLong(MediaSessionConstants.EXTRA_NAV_ID, navId)
                        putLong(MediaSessionConstants.EXTRA_DURATION_MS, radio.mainSong.duration)
                    })
                    .build()
            )
            .build()
    }

/**
 * 试听片段判定。只有两个可靠判据：
 * - `musicrep-ts`：NCM 试听片段的 CDN 标识；
 * - `time in 1..60000`：NCM 试听占位固定回 30040ms。
 * `freeTrial*` 字段对可播歌曲也常回非 null 的空对象，不能当判据（见 PlayerApi.isPlayableFullUrl 注释）。
 */
private fun SongUrl.isTrialClip(): Boolean =
    url?.contains("musicrep-ts", ignoreCase = true) == true || time in 1L..60_000L

/**
 * Resolve a MediaItem URI (a raw `id?fee=&pl=` string) to a playable URL.
 *
 * There is no per-song source override: the app-wide local/unblock source
 * settings (AudioSourceSettings / UNBLOCK_SOURCE) decide the channel, so we
 * always resolve with a null per-song source.
 *
 * 拿到试听片段（LX/直连偶发未命中时 PlayerApi 会回落官方直连，非会员拿到的是 30 秒
 * 试听链）不能直接交给播放器——那会表现为「听着听着只能试听」。这里先短退避重解析一次
 * （沙盒已热、脚本有 URL 缓存，第二次通常就命中），两次都只有试听才接受，避免彻底没声。
 */
suspend fun updateMediaItemUri(uri: Uri, songLevel: SongLevel): Uri? {
    if (uri.scheme == "http" || uri.scheme == "https") {
        return uri
    }

    val path = uri.path.orEmpty().removePrefix("/")
    val query = uri.query
    val songId = if (query != null) "$path?$query" else path

    val first = PlayerApi.songPlayUrlV1(songId, songLevel).getOrNull()?.data?.firstOrNull()
    val firstUrl = first?.url?.takeIf { it.isNotBlank() }
    if (firstUrl != null && first?.isTrialClip() != true) return firstUrl.toUri()

    android.util.Log.d(
        "PlayerApi",
        "play url blank/trial, retry resolve songId=$songId firstUrl=${firstUrl != null} trial=${first?.isTrialClip()}"
    )
    delay(400)
    val second = PlayerApi.songPlayUrlV1(songId, songLevel).getOrNull()?.data?.firstOrNull()
    val secondUrl = second?.url?.takeIf { it.isNotBlank() }

    return when {
        secondUrl != null && second?.isTrialClip() != true -> secondUrl.toUri()
        // 两次都只拿到试听：先给试听，别让用户彻底听不到。
        firstUrl != null -> firstUrl.toUri()
        secondUrl != null -> secondUrl.toUri()
        else -> null
    }
}

