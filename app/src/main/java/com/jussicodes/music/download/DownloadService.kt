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
import com.jussicodes.music.R
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

        val initialNotification = buildNotification("准备下载中...", 0, 0, false)
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
                    // 没有正在执行的任务了，稍微延迟等待看是否还有新入队，若无则停止服务
                    delay(2000)
                    val recheck = DownloadStore.getAllTasks().any {
                        it.status == DownloadStatus.DOWNLOADING ||
                        it.status == DownloadStatus.RESOLVING ||
                        it.status == DownloadStatus.PENDING
                    }
                    if (!recheck) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                } else {
                    val currentDownloading = activeTasks.firstOrNull { it.status == DownloadStatus.DOWNLOADING }
                        ?: activeTasks.firstOrNull { it.status == DownloadStatus.RESOLVING }
                        ?: activeTasks.first()

                    val title = when (currentDownloading.status) {
                        DownloadStatus.DOWNLOADING -> "正在下载: ${currentDownloading.title} (${currentDownloading.speedText})"
                        DownloadStatus.RESOLVING -> "正在解析: ${currentDownloading.title}"
                        else -> "排队中 (${activeTasks.size} 首)"
                    }
                    val percent = (currentDownloading.progress * 100).toInt().coerceIn(0, 100)
                    val content = "${currentDownloading.artist} · 队列剩余 ${activeTasks.size} 首"

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
        progress: Int,
        max: Int,
        indeterminate: Boolean,
        contentText: String = ""
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
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(contentText)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(max, progress, indeterminate)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "歌曲下载",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "显示歌曲下载进度"
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
