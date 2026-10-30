package com.jussicodes.music.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.jussicodes.music.MainActivity
import com.jussicodes.music.data.download.DownloadStatus
import com.jussicodes.music.data.download.DownloadStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class DownloadService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var observerJob: Job? = null
    private lateinit var notificationManager: NotificationManager

    companion object {
        private const val CHANNEL_ID = "jussichords_download_channel"
        private const val NOTIFICATION_ID = 20261

        fun start(context: Context) {
            val intent = Intent(context, DownloadService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()

        val initialNotification = buildNotification(
            title = "歌曲下载准备中…",
            contentText = "正在初始化下载队列",
            progress = 0,
            max = 100,
            indeterminate = true
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                initialNotification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, initialNotification)
        }

        startObserving()
    }

    private fun startObserving() {
        observerJob?.cancel()
        observerJob = serviceScope.launch {
            DownloadStore.tasks.collect { tasks ->
                val activeTasks = tasks.filter {
                    it.status == DownloadStatus.DOWNLOADING ||
                    it.status == DownloadStatus.RESOLVING ||
                    it.status == DownloadStatus.PENDING
                }

                if (activeTasks.isEmpty()) {
                    // 没有正在执行的任务了，稍微延迟等待看是否还有新入队
                    delay(1500)
                    val recheck = DownloadStore.getAllTasks().any {
                        it.status == DownloadStatus.DOWNLOADING ||
                        it.status == DownloadStatus.RESOLVING ||
                        it.status == DownloadStatus.PENDING
                    }
                    if (!recheck) {
                        showCompletedNotification()
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                } else {
                    val currentDownloading = activeTasks.firstOrNull { it.status == DownloadStatus.DOWNLOADING }
                        ?: activeTasks.firstOrNull { it.status == DownloadStatus.RESOLVING }
                        ?: activeTasks.first()

                    val percent = (currentDownloading.progress * 100).toInt().coerceIn(0, 100)
                    val title = when (currentDownloading.status) {
                        DownloadStatus.DOWNLOADING -> "正在下载 (${activeTasks.size} 首): ${currentDownloading.title}"
                        DownloadStatus.RESOLVING -> "正在解析直链 (${activeTasks.size} 首): ${currentDownloading.title}"
                        else -> "等待下载 (${activeTasks.size} 首): ${currentDownloading.title}"
                    }
                    val speed = if (currentDownloading.speedText.isNotBlank()) " · ${currentDownloading.speedText}" else ""
                    val content = "${currentDownloading.artist} · $percent%$speed"

                    val notification = buildNotification(
                        title = title,
                        contentText = content,
                        progress = percent,
                        max = 100,
                        indeterminate = currentDownloading.status == DownloadStatus.RESOLVING
                    )
                    notificationManager.notify(NOTIFICATION_ID, notification)
                }
            }
        }
    }

    private fun buildNotification(
        title: String,
        contentText: String,
        progress: Int,
        max: Int,
        indeterminate: Boolean
    ): android.app.Notification {
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(contentText)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(max, progress, indeterminate)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun showCompletedNotification() {
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val completedNotification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("歌曲下载完成")
            .setContentText("所选歌曲已成功保存到本地存储")
            .setAutoCancel(true)
            .setOngoing(false)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        notificationManager.notify(NOTIFICATION_ID + 1, completedNotification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "歌曲下载",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "显示歌曲下载进度与状态"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
