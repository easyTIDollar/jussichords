package com.jussicodes.music.lyric

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.datastore.preferences.core.edit
import com.jussicodes.music.constants.desktopLyricEnabledKey
import com.jussicodes.music.utils.dataStore
import com.jussicodes.music.utils.get

object DesktopLyricManager {

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
            appContext.dataStore.edit { it[desktopLyricEnabledKey] = false }
            if (requestPermissionIfNeeded) {
                requestOverlayPermission(appContext)
            }
            stopService(appContext)
            return false
        }

        appContext.dataStore.edit { it[desktopLyricEnabledKey] = enabled }
        if (enabled) {
            startService(appContext)
        } else {
            stopService(appContext)
        }
        return true
    }

    fun syncService(context: Context) {
        val appContext = context.applicationContext
        val enabled = appContext.dataStore.get(desktopLyricEnabledKey, false)
        if (enabled && canDrawOverlays(appContext)) {
            startService(appContext)
        } else {
            stopService(appContext)
        }
    }

    private fun startService(context: Context) {
        context.startService(Intent(context, DesktopLyricService::class.java))
    }

    private fun stopService(context: Context) {
        context.stopService(Intent(context, DesktopLyricService::class.java))
    }
}
