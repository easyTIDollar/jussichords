package com.jussicodes.music.download

import com.jussicodes.music.extensions.isTrialClip
import com.rcmiku.ncmapi.api.apiGet
import com.rcmiku.ncmapi.api.player.PlayerApi
import com.rcmiku.ncmapi.api.player.SongLevel
import com.rcmiku.ncmapi.model.Song
import com.rcmiku.ncmapi.model.SongUrl
import com.rcmiku.ncmapi.utils.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

enum class ProbeItemStatus {
    PENDING,
    PROBING,
    AVAILABLE,
    DOWNGRADED,
    ENCRYPTED_SHELL,
    UNAVAILABLE,
    NOT_IN_LIBRARY
}

data class SongMeta(
    val name: String,
    val artists: String,
    val durationMs: Long,
    val availableLevels: Set<SongLevel> = emptySet()
)

data class AudioQualityProbeItem(
    val level: SongLevel,
    val label: String,
    val description: String = "",
    val status: ProbeItemStatus = ProbeItemStatus.PENDING,
    val actualLevel: SongLevel? = null,
    val fileSize: Long? = null,
    val qualityMatched: Boolean = true,
    val resolvedUrl: String? = null,
    val errorReason: String? = null
)

object AudioQualityProber {

    private val probeClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    val PROBE_LEVELS = listOf(
        SongLevel.JYMASTER to ("超清母带" to "Master 顶尖母带音质"),
        SongLevel.DOLBY to ("杜比全景声" to "Dolby Atmos 沉浸空间音频"),
        SongLevel.HIRES to ("Hi-Res" to "高解析无损 24bit / ≥48kHz"),
        SongLevel.LOSSLESS to ("无损" to "CD级音质 16bit / 44.1kHz FLAC"),
        SongLevel.EXHIGH to ("极高" to "高保真 320 kbps MP3"),
        SongLevel.STANDARD to ("标准" to "标准音质 128 kbps MP3")
    )

    fun formatFileSize(bytes: Long): String {
        return when {
            bytes <= 0 -> "大小未知"
            bytes >= 1024 * 1024 * 1024 -> String.format("%.2f GB", bytes / (1024f * 1024f * 1024f))
            bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes / (1024f * 1024f))
            bytes >= 1024 -> String.format("%.0f KB", bytes / 1024f)
            else -> "$bytes B"
        }
    }

    /**
     * 取歌曲元信息（歌名、歌手、时长、以及曲库声明支持的音质集合）
     */
    suspend fun fetchSongMeta(songId: Long): SongMeta? = withContext(Dispatchers.IO) {
        runCatching {
            apiGet<JsonElement>("/song/detail/batch", mapOf("c" to "[{\"id\":$songId}]"))
                .getOrNull()
                ?.let { el -> ((el as? JsonObject)?.get("songs") as? JsonArray).orEmpty().firstOrNull() }
                ?.let { runCatching { json.decodeFromString<Song>(it.toString()) }.getOrNull() }
                ?.let { song ->
                    val artists = song.ar.joinToString("/") { it.name }
                    SongMeta(
                        name = song.name,
                        artists = artists,
                        durationMs = song.dt,
                        availableLevels = determineAvailableLevels(song)
                    )
                }
        }.getOrNull()
    }

    /**
     * 根据网易云歌曲元数据（sq/hr/jm/db/h/m/l 及 privilege.maxBrLevel）前置推导客观收录的音质档位
     * 避免对歌曲根本不存在的高阶档位发起无效网络请求并误判降档
     */
    fun determineAvailableLevels(song: Song?): Set<SongLevel> {
        if (song == null) return SongLevel.entries.toSet()
        val set = mutableSetOf<SongLevel>()
        set.add(SongLevel.STANDARD)
        if (song.h != null || (song.privilege?.maxbr ?: 0) >= 320000) {
            set.add(SongLevel.EXHIGH)
        }
        if (song.m != null || (song.privilege?.maxbr ?: 0) >= 192000) {
            set.add(SongLevel.HIGHER)
        }
        val maxLevel = song.privilege?.maxBrLevel?.lowercase()
        val hasSq = song.sq != null || (song.privilege?.maxbr ?: 0) >= 999000 ||
                maxLevel in listOf("lossless", "hires", "jymaster", "dolby")
        if (hasSq) {
            set.add(SongLevel.LOSSLESS)
        }
        val hasHr = song.hr != null || maxLevel in listOf("hires", "jymaster", "dolby")
        if (hasHr) {
            set.add(SongLevel.HIRES)
        }
        val hasJm = song.jm != null || maxLevel == "jymaster"
        if (hasJm) {
            set.add(SongLevel.JYMASTER)
        }
        val hasDb = song.db != null || maxLevel == "dolby"
        if (hasDb) {
            set.add(SongLevel.DOLBY)
        }
        return set
    }

    /**
     * 发送轻量探测请求获取文件大小与 Content-Type
     * 优先 HEAD，若服务商拦截或 405 则退避为 GET Range: bytes=0-0
     */
    private fun probeHttp(url: String): Pair<Long, String> {
        val headReq = Request.Builder()
            .url(url)
            .head()
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .build()
        runCatching {
            probeClient.newCall(headReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val len = resp.header("Content-Length")?.toLongOrNull() ?: -1L
                    val type = resp.header("Content-Type").orEmpty()
                    if (len > 0) return Pair(len, type)
                }
            }
        }

        val getReq = Request.Builder()
            .url(url)
            .header("Range", "bytes=0-0")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .build()
        return runCatching {
            probeClient.newCall(getReq).execute().use { resp ->
                val contentRange = resp.header("Content-Range").orEmpty()
                val totalLen = contentRange.substringAfterLast("/", "").toLongOrNull()
                    ?: resp.header("Content-Length")?.toLongOrNull()
                    ?: -1L
                val type = resp.header("Content-Type").orEmpty()
                Pair(totalLen, type)
            }
        }.getOrDefault(Pair(-1L, ""))
    }

    /**
     * 探测单个档位：
     * 1. 真实调用解析链取直链；
     * 2. 拦截试听与加密壳（.mgg/.mflac/.vpr）；
     * 3. 发 HEAD 读文件大小；
     * 4. 比对请求档与实际档，抓包偷降档行为。
     */
    suspend fun probeLevel(
        songId: Long,
        level: SongLevel,
        durationMs: Long
    ): AudioQualityProbeItem = withContext(Dispatchers.IO) {
        val (label, desc) = PROBE_LEVELS.firstOrNull { it.first == level }?.second ?: (level.value to "")

        var resolvedUrl: String? = null
        var resp: SongUrl? = null

        val officialResp = runCatching {
            PlayerApi.songPlayUrlV1(songId.toString(), level).getOrNull()?.data?.firstOrNull()
        }.getOrNull()
        val officialU = officialResp?.url?.takeIf { it.isNotBlank() }
        val officialIsTrial = officialResp?.isTrialClip() == true || officialU?.contains("musicrep-ts", ignoreCase = true) == true
        val officialLevelStr = officialResp?.level?.lowercase()
        val officialMatchesLevel = officialLevelStr != null && officialLevelStr == level.value

        if (officialU != null && !officialIsTrial && (level == SongLevel.STANDARD || officialMatchesLevel)) {
            resolvedUrl = officialU
            resp = officialResp
        } else {
            val unblockResp = runCatching {
                PlayerApi.songPlayUrlV1("$songId?fee=1", level).getOrNull()?.data?.firstOrNull()
            }.getOrNull()
            val unblockU = unblockResp?.url?.takeIf { it.isNotBlank() }
            val unblockIsTrial = unblockResp?.isTrialClip() == true || unblockU?.contains("musicrep-ts", ignoreCase = true) == true
            if (unblockU != null && !unblockIsTrial) {
                resolvedUrl = unblockU
                resp = unblockResp
            } else if (officialU != null && !officialIsTrial) {
                resolvedUrl = officialU
                resp = officialResp
            }
        }

        if (resolvedUrl.isNullOrBlank()) {
            return@withContext AudioQualityProbeItem(
                level = level,
                label = label,
                description = desc,
                status = ProbeItemStatus.UNAVAILABLE,
                qualityMatched = false,
                errorReason = "未提供直链"
            )
        }

        val cleanUrl = resolvedUrl.substringBefore("?").lowercase()
        if (cleanUrl.endsWith(".mgg") || cleanUrl.endsWith(".mflac") || cleanUrl.endsWith(".vpr")) {
            return@withContext AudioQualityProbeItem(
                level = level,
                label = label,
                description = "检测到私有加密音频壳",
                status = ProbeItemStatus.ENCRYPTED_SHELL,
                qualityMatched = false,
                errorReason = "加密格式"
            )
        }

        val (fileSize, contentType) = probeHttp(resolvedUrl)

        var actualLevel = level
        var qualityMatched = true
        val returnedLevelStr = resp?.level?.lowercase()
        val returnedLevel = SongLevel.entries.firstOrNull { it.value.equals(returnedLevelStr, ignoreCase = true) }

        if (returnedLevel != null) {
            // 优先采信网易云服务端返回的真实协商档位
            if (returnedLevel != level) {
                actualLevel = returnedLevel
                qualityMatched = false
            } else {
                actualLevel = level
                qualityMatched = true
            }
        } else {
            // 第三方解灰源 / LX 脚本（未返回服务端 level 字段），按扩展名与基础码率做异常兜底
            val isMp3Url = cleanUrl.endsWith(".mp3")
            val approxBitrate = if (fileSize > 0 && durationMs > 0) {
                (fileSize * 8) / (durationMs / 1000)
            } else {
                -1L
            }

            when (level) {
                SongLevel.JYMASTER, SongLevel.DOLBY, SongLevel.HIRES, SongLevel.LOSSLESS -> {
                    // 请求无损以上档位，却返回了普通 MP3 或码率极低
                    if (isMp3Url || (approxBitrate in 1..350_000)) {
                        qualityMatched = false
                        actualLevel = if (approxBitrate in 1..192_000) SongLevel.STANDARD else SongLevel.EXHIGH
                    }
                }
                SongLevel.EXHIGH -> {
                    if (approxBitrate in 1..192_000) {
                        qualityMatched = false
                        actualLevel = SongLevel.STANDARD
                    }
                }
                else -> {
                    qualityMatched = true
                }
            }
        }

        val status = if (qualityMatched) ProbeItemStatus.AVAILABLE else ProbeItemStatus.DOWNGRADED

        AudioQualityProbeItem(
            level = level,
            label = label,
            description = desc,
            status = status,
            actualLevel = actualLevel,
            fileSize = if (fileSize > 0) fileSize else null,
            qualityMatched = qualityMatched,
            resolvedUrl = resolvedUrl
        )
    }

    /**
     * 限制最大 3 个 Worker 并发探测所有档位，逐步回调 UI 局部刷新
     * availableLevels 非空时仅对曲库实际收录的档位发起真探测，其余直接标记未收录
     */
    suspend fun probeAll(
        songId: Long,
        durationMs: Long,
        availableLevels: Set<SongLevel>? = null,
        onItemUpdate: (AudioQualityProbeItem) -> Unit
    ) = coroutineScope {
        val semaphore = Semaphore(3)
        PROBE_LEVELS.map { (lvl, pair) ->
            async {
                if (availableLevels != null && lvl !in availableLevels) {
                    onItemUpdate(
                        AudioQualityProbeItem(
                            level = lvl,
                            label = pair.first,
                            description = pair.second,
                            status = ProbeItemStatus.NOT_IN_LIBRARY,
                            qualityMatched = true,
                            errorReason = "曲库未收录"
                        )
                    )
                    return@async
                }

                semaphore.withPermit {
                    onItemUpdate(
                        AudioQualityProbeItem(
                            level = lvl,
                            label = pair.first,
                            description = pair.second,
                            status = ProbeItemStatus.PROBING
                        )
                    )
                    val result = probeLevel(songId, lvl, durationMs)
                    onItemUpdate(result)
                }
            }
        }.awaitAll()
    }
}
