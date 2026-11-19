package com.jussicodes.music.lyric

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.datastore.preferences.core.edit
import com.jussicodes.music.constants.desktopLyricEnabledKey
import com.jussicodes.music.constants.desktopLyricXFractionKey
import com.jussicodes.music.constants.desktopLyricYFractionKey
import com.jussicodes.music.utils.dataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

object DesktopLyricManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun canDrawOverlays(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)
    }

    fun requestOverlayPermission(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    suspend fun setEnabled(
        context: Context,
        enabled: Boolean,
        requestPermissionIfNeeded: Boolean = false
    ): Boolean {
        val appContext = context.applicationContext
        if (enabled && !canDrawOverlays(appContext)) {
            // 记住「用户想开」这个意图：授权回来后 ON_RESUME / syncService 能自动恢复。
            // 之前这里会把开关静默写回 false，导致每次更新后都像被重置、要重新开一次。
            appContext.dataStore.edit { it[desktopLyricEnabledKey] = true }
            if (requestPermissionIfNeeded) {
                requestOverlayPermission(appContext)
            }
            stopService(appContext)
            return false
        }

        val prefs = appContext.dataStore.edit { it[desktopLyricEnabledKey] = enabled }
        if (enabled) {
            startService(appContext, DesktopLyricSettings.fromPreferences(prefs))
        } else {
            stopService(appContext)
        }
        return true
    }

    fun syncService(context: Context): Job {
        val appContext = context.applicationContext
        return scope.launch {
            val prefs = appContext.dataStore.data.first()
            syncService(appContext, DesktopLyricSettings.fromPreferences(prefs))
        }
    }

    fun syncService(context: Context, settings: DesktopLyricSettings) {
        val appContext = context.applicationContext
        if (settings.enabled && canDrawOverlays(appContext)) {
            startService(appContext, settings)
        } else {
            stopService(appContext)
        }
    }

    /**
     * 回到默认位置：Service 在跑就让它自己重算并回写比例（能同时刷新悬浮窗），
     * 没跑就直接把比例写回默认值，下次显示时生效。
     */
    suspend fun resetPosition(context: Context) {
        val appContext = context.applicationContext
        val settings = DesktopLyricSettings.fromPreferences(appContext.dataStore.data.first())
        val running = canDrawOverlays(appContext) && settings.enabled
        if (running) {
            appContext.startService(
                Intent(appContext, DesktopLyricService::class.java)
                    .setAction(DesktopLyricService.ACTION_RESET_POSITION)
                    .putExtra(DesktopLyricService.EXTRA_SETTINGS, settings.toBundle())
            )
            return
        }
        appContext.dataStore.edit { prefs ->
            prefs[desktopLyricXFractionKey] = DesktopLyricDefaultXFraction
            prefs[desktopLyricYFractionKey] = DesktopLyricDefaultYFraction
        }
    }

    private fun startService(context: Context, settings: DesktopLyricSettings) {
        // 后台（如更新后的 MY_PACKAGE_REPLACED）拉起 Service 可能被系统拒，别让广播崩掉。
        runCatching {
            context.startService(
                Intent(context, DesktopLyricService::class.java)
                    .putExtra(DesktopLyricService.EXTRA_SETTINGS, settings.toBundle())
            )
        }
    }

    private fun stopService(context: Context) {
        context.stopService(Intent(context, DesktopLyricService::class.java))
    }
}
