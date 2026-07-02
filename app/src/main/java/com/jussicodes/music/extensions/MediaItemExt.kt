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
import com.rcmiku.ncmapi.utils.json

private fun Song.encodeUri(): String {
    val base = id.toString()
    val pl = privilege?.pl ?: 0
    return "$base?fee=$fee&pl=$pl"
}

fun Song.toMediaItem(sourceId: Long = 0L, sourceName: String = "list") =
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
                    putLong(MediaSessionConstants.EXTRA_DURATION_MS, dt)
                })
                .build()
        )
        .build()


fun List<Song>.toMediaItemList(sourceId: Long = 0L, sourceName: String = "list") =
    this.map { song ->
        val extras = Bundle().apply {
            putString("song", json.encodeToString(song))
            putLong(MediaSessionConstants.EXTRA_SOURCE_ID, sourceId)
            putString(MediaSessionConstants.EXTRA_SOURCE_NAME, sourceName)
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


fun List<CloudSong>.toCloudSongMediaItemList(uid: Long) =
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
                        putString(MediaSessionConstants.EXTRA_SOURCE_NAME, "cloud")
                        putLong(MediaSessionConstants.EXTRA_DURATION_MS, cloudSong.simpleSong.dt)
                    })
                    .build()
            )
            .build()
    }

fun List<Radio>.toRadioMediaItemList() =
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
                        putString(MediaSessionConstants.EXTRA_SOURCE_NAME, "radio")
                        putLong(MediaSessionConstants.EXTRA_DURATION_MS, radio.mainSong.duration)
                    })
                    .build()
            )
            .build()
    }

suspend fun updateMediaItemUri(uri: Uri, songLevel: SongLevel): Uri? {
    if (uri.scheme == "http" || uri.scheme == "https") {
        return uri
    }

    val path = uri.path.orEmpty().removePrefix("/")
    val query = uri.query
    val songId = if (query != null) "$path?$query" else path
    return PlayerApi.songPlayUrlV1(songId, songLevel = songLevel)
        .getOrNull()?.data?.firstOrNull()?.url?.toUri()
}
