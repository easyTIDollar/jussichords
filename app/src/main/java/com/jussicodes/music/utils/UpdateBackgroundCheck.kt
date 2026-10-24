package com.jussicodes.music.utils

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.jussicodes.music.BuildConfig
import com.jussicodes.music.MainActivity
import com.jussicodes.music.constants.ignoredUpdateVersionKey
import com.jussicodes.music.constants.updateBackgroundCheckEnabledKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 后台静默检查更新（JobScheduler 周期任务）。
 *
 * 为什么用系统的 JobScheduler 而不是 WorkManager：WorkManager 是新依赖（要联网拉包、
 * 改版本目录），而 JobScheduler 是系统自带能力，minSdk 26 直接可用，且自带
 * 「需要网络」「开机后重排」这类约束，正好够用。
 *
 * 行为：周期唤醒 → 走 [UpdateCheckCoordinator] 强制检查一次 → 有新版本且用户没忽略过
 * 就发一条通知（点开进 App）。检查结果同时写进 DataStore 缓存，所以下次打开 App
 * 能零网络地直接弹更新窗。
 */
class UpdateBackgroundCheckJobService : JobService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartJob(params: JobParameters): Boolean {
        scope.launch {
            try {
                val enabled = dataStore.data.first()[updateBackgroundCheckEnabledKey] ?: false
                if (!enabled) return@launch
                val outcome = UpdateCheckCoordinator.check(applicationContext, force = true)
                val info = outcome.updateInfo ?: return@launch
                if (info.versionName == BuildConfig.VERSION_NAME) return@launch
                val ignored = dataStore.data.first()[ignoredUpdateVersionKey].orEmpty()
                if (ignored == info.versionName) return@launch
                notifyUpdateAvailable(applicationContext, info)
            } finally {
                jobFinished(params, false)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = false

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "app_update_check"
        private const val NOTIFICATION_ID = 10087

        private fun notifyUpdateAvailable(context: Context, info: UpdateInfo) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = context.getSystemService(NotificationManager::class.java)
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "更新提醒",
                        NotificationManager.IMPORTANCE_DEFAULT
                    ).apply { description = "发现新版本时提醒" }
                )
            }
            val intent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val pending = PendingIntent.getActivity(
                context,
                3,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("发现新版本 ${info.versionName}")
                .setContentText(info.releaseName.ifBlank { "点击查看更新内容" })
                .setAutoCancel(true)
                .setContentIntent(pending)
                .build()
            // Android 13+ 没给通知权限时 notify 是空操作，这里兜住异常别让 Job 崩掉。
            runCatching {
                NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            }
        }
    }
}

/**
 * 后台检查更新的调度开关。
 *
 * 开关打开 → 排一个 6 小时的周期任务（要求网络）；关闭 → 取消任务。
 * 设置项改动后调用 [sync] 立即生效，不用等下次启动。
 */
object UpdateBackgroundCheckScheduler {

    private const val JOB_ID = 4101

    /** 周期：6 小时（JobScheduler 周期任务最短 15 分钟，但没必要那么频繁打 API）。 */
    private const val INTERVAL_MS = 6L * 60 * 60 * 1000

    /** 按当前偏好同步调度状态（读 DataStore，需要挂起）。 */
    suspend fun sync(context: Context) {
        val enabled = context.dataStore.data.first()[updateBackgroundCheckEnabledKey] ?: false
        apply(context, enabled)
    }

    /**
     * 按给定状态排/取消周期任务。
     *
     * 单独提供这个入口，是因为设置页的开关刚写入 DataStore 时（写入是异步的），
     * 立刻回读 DataStore 可能读到旧值 —— 开关直接用内存里的新值调用本函数更稳。
     */
    fun apply(context: Context, enabled: Boolean) {
        if (enabled) schedule(context) else cancel(context)
    }

    fun schedule(context: Context) {
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
        val job = JobInfo.Builder(
            JOB_ID,
            ComponentName(context, UpdateBackgroundCheckJobService::class.java)
        )
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPeriodic(INTERVAL_MS)
            .setPersisted(true)
            .build()
        scheduler.schedule(job)
    }

    fun cancel(context: Context) {
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
        scheduler.cancel(JOB_ID)
    }
}
