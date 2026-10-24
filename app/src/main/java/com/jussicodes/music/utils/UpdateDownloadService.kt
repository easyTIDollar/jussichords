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
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.jussicodes.music.MainActivity
import com.jussicodes.music.R
import com.jussicodes.music.ui.components.formatFileSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
                // 没装成功要把原因说出来（尤其是缺「安装未知应用」权限），
                // 否则用户点了通知里的「安装」却什么都没发生。
                AppUpdateManager.installApk(this, File(apkPath))
                    .onFailure { throwable ->
                        Toast.makeText(
                            this,
                            throwable.message ?: "无法打开安装器",
                            Toast.LENGTH_LONG
                        ).show()
                    }
            }
            stopSelf(startId)
            return START_NOT_STICKY
        }

        val payload = intent?.getStringExtra(EXTRA_UPDATE_INFO)
        val updateInfo = payload?.let { Json.decodeFromString<UpdateInfo>(it) } ?: run {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val sourceId = intent.getStringExtra(EXTRA_DOWNLOAD_SOURCE_ID)
            ?: downloadSources.first().id

        // 幂等：已有下载在跑时忽略新的启动请求。否则两次下载会同时写同一个
        // .part 文件，互相覆盖字节，产物损坏且报错信息完全误导。
        // 注意这里不能 stopSelf：stopSelf 会连带停掉正在跑的下载。
        if (activeJob?.isActive == true) {
            return START_NOT_STICKY
        }

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildProgressNotification(updateInfo, 0L, updateInfo.apkSize))
        UpdateDownloadStateStore.downloading(
            updateInfo,
            DownloadProgress(0L, updateInfo.apkSize, 0f)
        )

        // 进度节流：下载回调是每读一块（8KB）触发一次，5MB 的包会有 ~640 次回调。
        // 每次都 notify + 写 StateFlow 会造成通知刷屏和 Compose 频繁重组，
        // 所以只有「进度变了至少 1%」或「距上次刷新超过 500ms」才真正刷新 UI。
        var lastNotifiedPercent = -1
        var lastNotifyAt = 0L

        val job = serviceScope.launch {
            try {
                val apkFile = AppUpdateManager.downloadApk(
                    applicationContext,
                    updateInfo,
                    sourceId
                ) { progress ->
                    val now = System.currentTimeMillis()
                    val percent = if (progress.totalBytes > 0L) {
                        (progress.downloadedBytes * 100 / progress.totalBytes).toInt()
                    } else {
                        -1
                    }
                    val percentChanged = percent >= 0 && percent != lastNotifiedPercent
                    val timedOut = now - lastNotifyAt >= 500L
                    if (progress.done || percentChanged || timedOut) {
                        lastNotifiedPercent = percent
                        lastNotifyAt = now
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
                UpdateDownloadStateStore.completed(updateInfo, apkFile.absolutePath)
                notificationManager.notify(
                    NOTIFICATION_ID,
                    buildCompletedNotification(updateInfo, apkFile)
                )
            } catch (ce: kotlinx.coroutines.CancellationException) {
                // 取消是用户主动行为，不该弹「下载失败」；进度通知也一并撤掉。
                UpdateDownloadStateStore.cancelled(updateInfo)
                notificationManager.cancel(NOTIFICATION_ID)
                throw ce
            } catch (throwable: Throwable) {
                val message = throwable.message ?: "Download failed"
                UpdateDownloadStateStore.failed(updateInfo, message)
                notificationManager.notify(
                    NOTIFICATION_ID,
                    buildFailedNotification(updateInfo, message)
                )
            } finally {
                stopForeground(false)
                stopSelf(startId)
            }
        }
        activeJob = job

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        activeJob?.cancel()
        activeJob = null
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
            .setContentTitle("正在下载更新 ${updateInfo.versionName}")
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
            .setContentTitle("正在下载更新 ${updateInfo.versionName}")
            .setContentText("点击安装更新")
            .setAutoCancel(true)
            .setContentIntent(buildInstallPendingIntent(apkFile))
            .build()
    }

    private fun buildFailedNotification(updateInfo: UpdateInfo, message: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("正在下载更新 ${updateInfo.versionName}")
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
        private const val EXTRA_DOWNLOAD_SOURCE_ID = "extra_download_source_id"
        private const val EXTRA_APK_PATH = "extra_apk_path"
        private const val ACTION_START_DOWNLOAD = "com.jussicodes.music.action.START_UPDATE_DOWNLOAD"
        private const val ACTION_INSTALL_APK = "com.jussicodes.music.action.INSTALL_DOWNLOADED_APK"

        /** 当前正在跑的下载任务（进程内单例）。用于「取消下载」与重复启动的幂等保护。 */
        @Volatile
        private var activeJob: Job? = null

        /**
         * 取消当前下载。已下载的部分保留在 .part 里，下次点「下载」会继续断点续传。
         */
        fun cancelDownload() {
            activeJob?.cancel()
            activeJob = null
        }

        fun start(
            context: Context,
            updateInfo: UpdateInfo,
            sourceId: String = downloadSources.first().id,
        ) {
            UpdateDownloadStateStore.reset()
            val intent = Intent(context, UpdateDownloadService::class.java).apply {
                action = ACTION_START_DOWNLOAD
                putExtra(EXTRA_UPDATE_INFO, Json.encodeToString(UpdateInfo.serializer(), updateInfo))
                putExtra(EXTRA_DOWNLOAD_SOURCE_ID, sourceId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
