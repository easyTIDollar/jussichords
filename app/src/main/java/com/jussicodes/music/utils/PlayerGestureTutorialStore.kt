package com.jussicodes.music.utils

import android.content.Context

/**
 * 播放器手势指南持久化管理。
 *
 * 使用 SharedPreferences 保证同步读取，避免 DataStore 异步 Flow 在 Compose 首帧
 * 回调 defaultValue 导致弹窗在重开软件时闪烁。
 */
object PlayerGestureTutorialStore {
    private const val PREFS_NAME = "player_gesture_prefs"
    private const val KEY_DISMISSED = "player_gesture_tutorial_dismissed"

    fun isDismissed(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_DISMISSED, false)
    }

    fun markDismissed(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DISMISSED, true)
            .apply()
    }
}
