package com.jussicodes.music.playback

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.jussicodes.music.constants.MediaSessionConstants
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/**
 * 播放队列落盘。
 *
 * 系统回收进程后媒体会话里队列是空的，这时点桌面组件 / 蓝牙耳机 / 系统媒体面板的播放键
 * 没有任何东西可播，只能把用户拽回 App 重新找歌。把「队列 + 第几首 + 播到哪」写下来，
 * MediaSession.Callback.onPlaybackResumption() 就能原样重建，按播放键直接续播。
 *
 * 线程约束：写盘在主线程被调用（播放器事件），所以文件写全部丢给单线程 IO；
 * 解析只在恢复时做一次，也在 IO 线程上，不挡主线程。
 */
class PlaybackQueueStore private constructor(context: Context) {

    private val app = context.applicationContext

    private val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val queueFile = File(app.filesDir, QUEUE_FILE)

    private val io = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "playback-queue-io").apply { isDaemon = true }
    }

    private val listeningIo = MoreExecutors.listeningDecorator(io)

    /** 上次交给写线程的队列签名：队列没变就不重复序列化/写盘。 */
    @Volatile
    private var lastSignature = 0

    /** 解析结果缓存，进程内复用。 */
    @Volatile
    private var cached: SavedQueue? = null

    fun save(items: List<MediaItem>, index: Int, positionMs: Long, playWhenReady: Boolean) {
        if (items.isEmpty()) return
        val safeIndex = index.coerceIn(0, items.size - 1)
        val position = positionMs.coerceAtLeast(0L)
        // 进度/下标是小字段，apply() 本身异步，主线程上开销可以忽略。
        prefs.edit()
            .putInt(KEY_INDEX, safeIndex)
            .putLong(KEY_POSITION, position)
            .putBoolean(KEY_PLAY, playWhenReady)
            .putInt(KEY_COUNT, items.size)
            .putLong(KEY_SAVED_AT, System.currentTimeMillis())
            .apply()
        val signature = signatureOf(items)
        if (signature == lastSignature) return
        lastSignature = signature
        // 队列体量可能到几百 KB，序列化 + 写盘全部离开主线程。
        listeningIo.submit(Callable {
            runCatching {
                val encoded = encodeQueue(items)
                val tmp = File(app.filesDir, "$QUEUE_FILE.tmp")
                tmp.writeText(encoded)
                if (!tmp.renameTo(queueFile)) {
                    queueFile.writeText(encoded)
                    tmp.delete()
                }
                cached = null
            }
        })
    }

    /** 有没有可恢复的队列：只查小字段，不解析文件。 */
    fun hasQueue(): Boolean = prefs.getInt(KEY_COUNT, 0) > 0 && queueFile.exists()

    /** 读回上次的队列（IO 线程，结果给 onPlaybackResumption 用）。 */
    fun readAsync(): ListenableFuture<SavedQueue?> = listeningIo.submit(Callable { read() })

    fun read(): SavedQueue? {
        cached?.let { return it }
        if (!queueFile.exists()) return null
        val items = runCatching { decodeQueue(queueFile.readText()) }.getOrNull() ?: return null
        if (items.isEmpty()) return null
        val index = prefs.getInt(KEY_INDEX, 0).coerceIn(0, items.size - 1)
        val result = SavedQueue(
            mediaItems = items,
            index = index,
            positionMs = prefs.getLong(KEY_POSITION, 0L).coerceAtLeast(0L),
            playWhenReady = prefs.getBoolean(KEY_PLAY, false),
        )
        cached = result
        lastSignature = signatureOf(items)
        return result
    }

    private fun signatureOf(items: List<MediaItem>): Int {
        var hash = items.size
        for (item in items) hash = hash * 31 + item.mediaId.hashCode()
        return hash
    }

    private fun encodeQueue(items: List<MediaItem>): String {
        val array = JSONArray()
        for (item in items) {
            val extras = item.mediaMetadata.extras
            val json = JSONObject()
            json.put("uri", item.localConfiguration?.uri?.toString() ?: item.mediaId)
            json.put("id", item.mediaId)
            item.mediaMetadata.title?.let { json.put("title", it.toString()) }
            item.mediaMetadata.artist?.let { json.put("artist", it.toString()) }
            item.mediaMetadata.artworkUri?.let { json.put("art", it.toString()) }
            if (extras != null) {
                extras.getString("song")?.let { json.put("song", it) }
                if (extras.containsKey(MediaSessionConstants.EXTRA_SOURCE_ID)) {
                    json.put("srcId", extras.getLong(MediaSessionConstants.EXTRA_SOURCE_ID))
                }
                extras.getString(MediaSessionConstants.EXTRA_SOURCE_NAME)
                    ?.let { json.put("srcName", it) }
                extras.getString(MediaSessionConstants.EXTRA_SOURCE_TYPE)
                    ?.let { json.put("srcType", it) }
                if (extras.containsKey(MediaSessionConstants.EXTRA_NAV_ID)) {
                    json.put("navId", extras.getLong(MediaSessionConstants.EXTRA_NAV_ID))
                }
                if (extras.containsKey(MediaSessionConstants.EXTRA_DURATION_MS)) {
                    json.put("dur", extras.getLong(MediaSessionConstants.EXTRA_DURATION_MS))
                }
            }
            array.put(json)
        }
        return array.toString()
    }

    private fun decodeQueue(raw: String): List<MediaItem> {
        val array = JSONArray(raw)
        val items = ArrayList<MediaItem>(array.length())
        for (i in 0 until array.length()) {
            val json = array.optJSONObject(i) ?: continue
            val uri = json.optString("uri").takeIf { it.isNotEmpty() } ?: continue
            val id = json.optString("id").takeIf { it.isNotEmpty() } ?: continue
            val extras = Bundle().apply {
                json.optString("song").takeIf { it.isNotEmpty() }?.let { putString("song", it) }
                if (json.has("srcId")) {
                    putLong(MediaSessionConstants.EXTRA_SOURCE_ID, json.optLong("srcId"))
                }
                json.optString("srcName").takeIf { it.isNotEmpty() }
                    ?.let { putString(MediaSessionConstants.EXTRA_SOURCE_NAME, it) }
                json.optString("srcType").takeIf { it.isNotEmpty() }
                    ?.let { putString(MediaSessionConstants.EXTRA_SOURCE_TYPE, it) }
                if (json.has("navId")) {
                    putLong(MediaSessionConstants.EXTRA_NAV_ID, json.optLong("navId"))
                }
                if (json.has("dur")) {
                    putLong(MediaSessionConstants.EXTRA_DURATION_MS, json.optLong("dur"))
                }
            }
            val metadata = MediaMetadata.Builder()
                .setTitle(json.optString("title").takeIf { it.isNotEmpty() })
                .setArtist(json.optString("artist").takeIf { it.isNotEmpty() })
                .setArtworkUri(json.optString("art").takeIf { it.isNotEmpty() }?.let(Uri::parse))
                .setExtras(extras)
                .build()
            items.add(
                MediaItem.Builder()
                    .setUri(uri)
                    .setMediaId(id)
                    .setMediaMetadata(metadata)
                    .build()
            )
        }
        return items
    }

    data class SavedQueue(
        val mediaItems: List<MediaItem>,
        val index: Int,
        val positionMs: Long,
        val playWhenReady: Boolean,
    )

    companion object {

        @Volatile
        private var instance: PlaybackQueueStore? = null

        fun get(context: Context): PlaybackQueueStore =
            instance ?: synchronized(this) {
                instance ?: PlaybackQueueStore(context).also { instance = it }
            }

        private const val PREFS_NAME = "playback_queue"
        private const val QUEUE_FILE = "playback_queue.json"
        private const val KEY_INDEX = "index"
        private const val KEY_POSITION = "position"
        private const val KEY_PLAY = "play_when_ready"
        private const val KEY_COUNT = "queue_count"
        private const val KEY_SAVED_AT = "saved_at"
    }
}
