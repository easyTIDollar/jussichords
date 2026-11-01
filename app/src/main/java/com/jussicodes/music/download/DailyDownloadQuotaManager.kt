package com.jussicodes.music.download

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 每日下载配额管理器：
 * 限制每个用户每天最多下载 60 首歌，每天 0 点自动重置额度。
 * 保护音源生态，避免滥用。
 */
object DailyDownloadQuotaManager {

    const val DAILY_LIMIT = 60
    private const val PREF_NAME = "daily_download_quota_pref"
    private const val KEY_LAST_DATE = "key_last_date"
    private const val KEY_USED_COUNT = "key_used_count"

    data class QuotaInfo(
        val used: Int,
        val limit: Int = DAILY_LIMIT
    ) {
        val remaining: Int get() = (limit - used).coerceAtLeast(0)
        val isExhausted: Boolean get() = remaining <= 0
        val progress: Float get() = (used.toFloat() / limit.toFloat()).coerceIn(0f, 1f)
    }

    private val _quotaState = MutableStateFlow(QuotaInfo(used = 0))
    val quotaState: StateFlow<QuotaInfo> = _quotaState.asStateFlow()

    private val lock = Any()

    private fun getTodayDateString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(Date())
    }

    /**
     * 检查并刷新当天的配额状态
     */
    fun checkAndRefresh(context: Context): QuotaInfo {
        synchronized(lock) {
            val pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val today = getTodayDateString()
            val lastDate = pref.getString(KEY_LAST_DATE, "") ?: ""
            val used = if (lastDate == today) {
                pref.getInt(KEY_USED_COUNT, 0)
            } else {
                // 跨天或首次：自动重置为 0
                pref.edit()
                    .putString(KEY_LAST_DATE, today)
                    .putInt(KEY_USED_COUNT, 0)
                    .apply()
                0
            }
            val info = QuotaInfo(used = used)
            _quotaState.value = info
            return info
        }
    }

    /**
     * 获取当前配额快照
     */
    fun getQuota(context: Context): QuotaInfo = checkAndRefresh(context)

    /**
     * 尝试请求下载数量，返回实际允许下载的数量。
     * 若剩余配额不足，返回允许的剩余量（可为 0）。
     */
    fun tryConsume(context: Context, requestCount: Int): Int {
        if (requestCount <= 0) return 0
        synchronized(lock) {
            val current = checkAndRefresh(context)
            if (current.isExhausted) {
                showExhaustedToast(context)
                return 0
            }

            val allowed = requestCount.coerceAtMost(current.remaining)
            val newUsed = current.used + allowed
            val today = getTodayDateString()

            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_LAST_DATE, today)
                .putInt(KEY_USED_COUNT, newUsed)
                .apply()

            val updated = QuotaInfo(used = newUsed)
            _quotaState.value = updated

            if (allowed < requestCount) {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(
                        context,
                        "今日剩余下载额度仅剩 ${current.remaining} 首，已为你下载前 $allowed 首",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            return allowed
        }
    }

    private fun showExhaustedToast(context: Context) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(
                context,
                "今日下载配额已达上限（$DAILY_LIMIT/$DAILY_LIMIT 首），每天 0 点刷新额度",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
