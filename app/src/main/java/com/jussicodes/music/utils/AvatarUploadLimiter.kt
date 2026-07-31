package com.jussicodes.music.utils

import android.content.Context
import java.util.Calendar

object AvatarUploadLimiter {
    const val WEEKLY_LIMIT = 5
    private const val PREFS_NAME = "avatar_upload_limit"
    private const val KEY_TIMESTAMPS = "successful_uploads"

    @Synchronized
    fun remaining(context: Context, now: Long = System.currentTimeMillis()): Int {
        return (WEEKLY_LIMIT - currentWeekUploads(context, now).size).coerceAtLeast(0)
    }

    @Synchronized
    fun recordSuccess(context: Context, now: Long = System.currentTimeMillis()) {
        val uploads = currentWeekUploads(context, now) + now
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TIMESTAMPS, uploads.joinToString(","))
            .apply()
    }

    private fun currentWeekUploads(context: Context, now: Long): List<Long> {
        val weekStart = Calendar.getInstance().apply {
            timeInMillis = now
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val uploads = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_TIMESTAMPS, null)
            .orEmpty()
            .split(',')
            .mapNotNull(String::toLongOrNull)
            .filter { it >= weekStart && it <= now }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_TIMESTAMPS, uploads.joinToString(",")).apply()
        return uploads
    }
}
