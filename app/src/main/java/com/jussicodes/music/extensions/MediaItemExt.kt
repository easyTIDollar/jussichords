package com.jussicodes.music.extensions

import android.net.Uri
import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.jussicodes.music.constants.MediaSessionConstants
import com.jussicodes.music.playback.PlaybackPath
import com.jussicodes.music.playback.ResolveGuard
import com.rcmiku.ncmapi.api.player.PlayerApi
import com.rcmiku.ncmapi.api.player.SongLevel
import com.jussicodes.music.utils.CoverImageSize
import com.jussicodes.music.utils.toCoverImageUrl
import com.rcmiku.ncmapi.model.CloudSong
import com.rcmiku.ncmapi.model.Radio
import com.rcmiku.ncmapi.model.Song
import com.rcmiku.ncmapi.model.SongUrl
import com.rcmiku.ncmapi.utils.json

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
fun SongUrl.isTrialClip(): Boolean =
    url?.contains("musicrep-ts", ignoreCase = true) == true || time in 1L..60_000L

/**
 * MediaItem uri → 解析用的 songId（`id?fee=&pl=`）。
 * 同一个键也用作 [ResolveGuard] 的标识：播放器做时长校验、解析层读「要跳过的坏链」都用它，
 * 两边必须算出完全一致的字符串，否则拉黑不生效（fail-open）。
 */
fun Uri.toResolveKey(): String {
    if (scheme == "http" || scheme == "https") return toString()
    val path = path.orEmpty().removePrefix("/")
    val query = query
    return if (query != null) "$path?$query" else path
}

/**
 * Resolve a MediaItem URI (a raw `id?fee=&pl=` string) to a playable URL.
 *
 * There is no per-song source override: the app-wide local/unblock source
 * settings (AudioSourceSettings / UNBLOCK_SOURCE) decide the channel, so we
 * always resolve with a null per-song source.
 *
 * 拿到试听片段（LX/直连偶发未命中时 PlayerApi 会回落官方直连，非会员拿到的是 30 秒
 * 试听链）就直接交给播放器：再解析一遍要额外等一轮、再跑一整条插件链路，结果几乎一样，
 * 只会把起播拖慢。只有完全拿不到直链时才重试一次。
 *
 * 每次真正返回给播放器的直链都登记到 [ResolveGuard]：播放器拿到真实音频时长后核对，
 * 对不上会把它拉黑并触发重新解析（这是「解析成功但放不出声 / 播一半断」的兜底）。
 */
suspend fun updateMediaItemUri(uri: Uri, songLevel: SongLevel, context: android.content.Context? = null): Uri? {
    if (uri.scheme == "http" || uri.scheme == "https" || uri.scheme == "file" || uri.scheme == "content") {
        return uri
    }

    val songId = uri.toResolveKey()
    val idLong = songId.substringBefore("?").toLongOrNull()
    if (PlaybackPath.begin(songId, songLevel.value)) {
        PlaybackPath.route("网易云 · " + describeSource())
    }
    if (context != null && idLong != null) {
        val externalPlayable = com.jussicodes.music.data.external.ExternalPlaylistStore.getPlayableUri(context, idLong)
        if (externalPlayable != null) {
            android.util.Log.i("PlayerApi", "Hit external playlist audio for songId=$songId uri=$externalPlayable")
            PlaybackPath.stepFor(songId, "命中本地文件", "外部歌单音频", listOf("本地"), PlaybackPath.Status.OK)
            PlaybackPath.finishFor(songId, "本地文件 · $externalPlayable", PlaybackPath.Status.OK)
            return externalPlayable
        } else if (idLong < 0) {
            // 外部歌曲但物理文件已不存在或无权限，直接终止，不请求网易云
            android.util.Log.w("PlayerApi", "External audio file not found or inaccessible for songId=$songId")
            PlaybackPath.stepFor(songId, "本地文件缺失", "外部歌曲且文件不存在或无权限", listOf("本地"), PlaybackPath.Status.FAIL)
            PlaybackPath.finishFor(songId, "外部歌曲文件缺失", PlaybackPath.Status.FAIL)
            return null
        }
        val localPlayable = com.jussicodes.music.data.download.DownloadStore.getCompletedPlayableUri(context, idLong)
        if (localPlayable != null) {
            android.util.Log.i("PlayerApi", "Hit local downloaded audio for songId=$songId uri=$localPlayable")
            PlaybackPath.stepFor(songId, "命中本地文件", "已下载音频", listOf("本地"), PlaybackPath.Status.OK)
            PlaybackPath.finishFor(songId, "本地文件 · $localPlayable", PlaybackPath.Status.OK)
            return localPlayable
        }
    }

    // 上次被时长校验判定为错源的直链：解析层会跳过它们，于是重试会升级到下一个脚本/下一个直连源，
    // 而不是把同一根坏链再刷一遍。
    val skipUrls = ResolveGuard.skipUrls(songId)
    if (skipUrls.isNotEmpty()) {
        PlaybackPath.stepFor(songId, "跳过已拉黑直链", "上次被时长校验判定为错源：${skipUrls.size} 条", listOf("校验"), PlaybackPath.Status.INFO)
    }

    suspend fun resolveOnce(): SongUrl? {
        val t0 = System.currentTimeMillis()
        val r = PlayerApi.songPlayUrlV1(songId, songLevel, skipUrls = skipUrls)
            .getOrNull()?.data?.firstOrNull()
        val cost = System.currentTimeMillis() - t0
        // 成功不打点：命中哪条分支、哪个域名，PlayerApi 已经写过一次；这里只补它不会写的「空结果」。
        if (r?.url.isNullOrBlank()) {
            PlaybackPath.stepFor(
                songId,
                "解析未返回地址",
                "空结果 · ${cost}ms",
                listOf("网易云"),
                PlaybackPath.Status.FAIL,
                cost,
            )
        }
        return r
    }

    fun accept(url: String?, trial: Boolean): Uri? {
        if (url == null) return null
        ResolveGuard.recordResolved(songId, url, trial)
        PlaybackPath.resolvedFor(
            songId,
            (if (trial) "试听片段 · " else "") + com.rcmiku.ncmapi.api.PlayUrlTrace.hostOf(url),
        )
        return url.toUri()
    }

    val first = resolveOnce()
    val firstUrl = first?.url?.takeIf { it.isNotBlank() }
    // 拿到试听就直接播：再解析一遍要额外等一轮，结果几乎一样，只会把起播拖慢。
    if (firstUrl != null) return accept(firstUrl, trial = first?.isTrialClip() == true)

    // 完全拿不到直链才重试一次（网络抖动），不再额外 sleep。
    android.util.Log.d("PlayerApi", "play url blank, retry resolve songId=$songId")
    PlaybackPath.stepFor(songId, "空结果，重试一次", "网络抖动兜底", listOf("重试"), PlaybackPath.Status.RUNNING)
    val second = resolveOnce()
    val secondUrl = second?.url?.takeIf { it.isNotBlank() }
    if (secondUrl == null) {
        PlaybackPath.finishFor(songId, "两次解析都没有可播地址", PlaybackPath.Status.FAIL)
    }
    return accept(secondUrl, trial = second?.isTrialClip() == true)
}

/** 面板上的路由摘要：当前选的是哪个音源通道。 */
private fun describeSource(): String = when (com.rcmiku.ncmapi.api.AudioSourceSettings.SELECTED) {
    com.rcmiku.ncmapi.api.AudioSources.LX -> "LX 脚本音源"
    com.rcmiku.ncmapi.api.AudioSources.DIRECT -> "内置直连音源"
    else -> "代理解灰音源"
}

