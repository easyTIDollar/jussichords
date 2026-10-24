package com.jussicodes.music.utils

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.jussicodes.music.BuildConfig
import com.jussicodes.music.constants.updateCacheJsonKey
import com.jussicodes.music.constants.updateLastCheckAtKey
import com.jussicodes.music.constants.updateLastErrorKey
import com.rcmiku.ncmapi.utils.json
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

/**
 * 检查更新的节流 / 缓存 / 失败原因落盘。
 *
 * 为什么需要它：原来的实现是「每次冷启动都直接打 GitHub API」，两个问题叠加：
 *  1. GitHub 未认证 API 限额是 60 次/小时/IP（同一出口 IP 的用户互相挤占），
 *     命中 403 后更新检查静默失败，用户完全不知道自己被限流了；
 *  2. 检查是阻塞在启动流程里的，最坏两个源各 15s 超时共 30s，会把启动卡住。
 *
 * 这里把「检查结果」变成有状态的一等公民：
 *  - 成功过 → 6 小时内不再联网（窗口内直接用缓存的 UpdateInfo 弹窗，零网络请求）；
 *  - 失败过 → 30 分钟内不再重试（但保留错误原因，设置页可见，不静默吞掉）；
 *  - 缓存里保存「上次查到的更新」，后台任务命中后 App 一打开就能立刻弹窗。
 *
 * 手动点「检查更新」用 [force] = true，无视节流窗口。
 */
object UpdateCheckCoordinator {

    /** 成功后的免打扰窗口：6 小时。 */
    private const val SUCCESS_WINDOW_MS = 6L * 60 * 60 * 1000

    /** 失败后的重试窗口：30 分钟（失败也要退避，否则每次启动都重试打 API）。 */
    private const val FAILURE_WINDOW_MS = 30L * 60 * 1000

    /**
     * 检查结果。
     *
     * @param updateInfo 查到的更新（null = 已是最新 / 本次失败）。
     * @param fromCache  true = 本次没联网，用的是缓存（节流窗口内）。
     * @param error 本次失败原因（null = 没有失败）。
     */
    data class Outcome(
        val updateInfo: UpdateInfo?,
        val fromCache: Boolean = false,
        val error: String? = null,
    ) {
        /** 是否真的拿到了「有更新」。 */
        val hasUpdate: Boolean get() = updateInfo != null
    }

    /**
     * 执行一次（可能被节流的）检查更新。
     *
     * @param force true = 无视节流窗口强制联网（设置页手动检查 / 后台任务）。
     */
    suspend fun check(context: Context, force: Boolean = false): Outcome {
        val prefs = context.dataStore.data.first()
        val lastAt = prefs[updateLastCheckAtKey] ?: 0L
        val lastError = prefs[updateLastErrorKey].orEmpty()
        val window = if (lastError.isBlank()) SUCCESS_WINDOW_MS else FAILURE_WINDOW_MS
        val fresh = lastAt > 0L && (System.currentTimeMillis() - lastAt) < window
        if (!force && fresh) {
            return Outcome(
                updateInfo = readCachedUpdate(prefs[updateCacheJsonKey]),
                fromCache = true,
                error = lastError.takeIf { it.isNotBlank() },
            )
        }

        return AppUpdateManager.checkUpdate().fold(
            onSuccess = { info ->
                persistSuccess(context, info)
                Outcome(updateInfo = info)
            },
            onFailure = { throwable ->
                val message = throwable.message ?: "检查更新失败"
                persistFailure(context, message)
                Outcome(updateInfo = null, error = message)
            }
        )
    }

    /** 读取上次缓存到的更新信息（缓存为空 = 上次是「已是最新」）。 */
    suspend fun cachedUpdate(context: Context): UpdateInfo? =
        readCachedUpdate(context.dataStore.data.first()[updateCacheJsonKey])

    private fun readCachedUpdate(raw: String?): UpdateInfo? {
        if (raw.isNullOrBlank()) return null
        val info = runCatching { json.decodeFromString<UpdateInfo>(raw) }.getOrNull() ?: return null
        // 缓存里的版本已经装上了（用户可能刚从更新里装完）：当成「已是最新」，
        // 否则每次开 App 都会再弹一次同一个版本的更新窗。
        if (info.versionName == BuildConfig.VERSION_NAME) return null
        return info
    }

    private suspend fun persistSuccess(context: Context, info: UpdateInfo?) {
        context.dataStore.edit { prefs ->
            prefs[updateLastCheckAtKey] = System.currentTimeMillis()
            prefs.remove(updateLastErrorKey)
            if (info == null) {
                prefs.remove(updateCacheJsonKey)
            } else {
                prefs[updateCacheJsonKey] = json.encodeToString(info)
            }
        }
    }

    private suspend fun persistFailure(context: Context, message: String) {
        context.dataStore.edit { prefs ->
            prefs[updateLastCheckAtKey] = System.currentTimeMillis()
            prefs[updateLastErrorKey] = message
            // 缓存里的旧更新信息保留：网络失败不应该让用户看不到已经发现的更新。
        }
    }
}
