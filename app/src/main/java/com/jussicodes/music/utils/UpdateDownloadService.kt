package com.jussicodes.music.utils

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.jussicodes.music.MainActivity
import com.jussicodes.music.R
import com.jussicodes.music.ui.components.formatFileSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File

class UpdateDownloadService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val notificationManager by lazy { NotificationManagerCompat.from(this) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_INSTALL_APK) {
            val apkPath = intent.getStringExtra(EXTRA_APK_PATH)
            if (!apkPath.isNullOrBlank()) {
                AppUpdateManager.installApk(this, File(apkPath))
            }
            stopSelf(startId)
            return START_NOT_STICKY
        }

        val payload = intent?.getStringExtra(EXTRA_UPDATE_INFO)
        val updateInfo = payload?.let { Json.decodeFromString<UpdateInfo>(it) } ?: run {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildProgressNotification(updateInfo, 0L, updateInfo.apkSize))
        UpdateDownloadStateStore.downloading(
            updateInfo,
            DownloadProgress(0L, updateInfo.apkSize, 0f)
        )

        serviceScope.launch {
            val result = runCatching {
                AppUpdateManager.downloadApk(applicationContext, updateInfo) { progress ->
                    UpdateDownloadStateStore.downloading(updateInfo, progress)
                    notificationManager.notify(
                        NOTIFICATION_ID,
                        buildProgressNotification(
                            updateInfo,
                            progress.downloadedBytes,
                            progress.totalBytes,
                        )
                    )
                }
            }

            result.onSuccess { apkFile ->
                UpdateDownloadStateStore.completed(updateInfo, apkFile.absolutePath)
                notificationManager.notify(
                    NOTIFICATION_ID,
                    buildCompletedNotification(updateInfo, apkFile)
                )
            }.onFailure { throwable ->
                val message = throwable.message ?: "Download failed"
                UpdateDownloadStateStore.failed(updateInfo, message)
                notificationManager.notify(
                    NOTIFICATION_ID,
                    buildFailedNotification(updateInfo, message)
                )
            }

            stopForeground(false)
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun buildProgressNotification(
        updateInfo: UpdateInfo,
        downloadedBytes: Long,
        totalBytes: Long,
    ): Notification {
        val contentText = if (totalBytes > 0L) {
            "${formatFileSize(downloadedBytes)} / ${formatFileSize(totalBytes)}"
        } else {
            "${formatFileSize(downloadedBytes)} / --"
        }
        val progressMax = totalBytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val progressValue = downloadedBytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("?????? ${updateInfo.versionName}")
            .setContentText(contentText)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setContentIntent(buildLaunchAppPendingIntent())
            .setProgress(progressMax, progressValue, totalBytes <= 0L)
            .build()
    }

    private fun buildCompletedNotification(updateInfo: UpdateInfo, apkFile: File): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("?????? ${updateInfo.versionName}")
            .setContentText("点击安装更新")
            .setAutoCancel(true)
            .setContentIntent(buildInstallPendingIntent(apkFile))
            .build()
    }

    private fun buildFailedNotification(updateInfo: UpdateInfo, message: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("?????? ${updateInfo.versionName}")
            .setContentText(message)
            .setAutoCancel(true)
            .setContentIntent(buildLaunchAppPendingIntent())
            .build()
    }

    private fun buildInstallPendingIntent(apkFile: File): PendingIntent {
        val intent = Intent(this, UpdateDownloadService::class.java).apply {
            action = ACTION_INSTALL_APK
            putExtra(EXTRA_APK_PATH, apkFile.absolutePath)
        }
        return PendingIntent.getService(
            this,
            2,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildLaunchAppPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return PendingIntent.getActivity(
            this,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "应用更新",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "显示应用更新下载与安装通知"
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "app_update_download"
        private const val NOTIFICATION_ID = 10086
        private const val EXTRA_UPDATE_INFO = "extra_update_info"
        private const val EXTRA_APK_PATH = "extra_apk_path"
        private const val ACTION_START_DOWNLOAD = "com.jussicodes.music.action.START_UPDATE_DOWNLOAD"
        private const val ACTION_INSTALL_APK = "com.jussicodes.music.action.INSTALL_DOWNLOADED_APK"

        fun start(context: Context, updateInfo: UpdateInfo) {
            UpdateDownloadStateStore.reset()
            val intent = Intent(context, UpdateDownloadService::class.java).apply {
                action = ACTION_START_DOWNLOAD
                putExtra(EXTRA_UPDATE_INFO, Json.encodeToString(UpdateInfo.serializer(), updateInfo))
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
