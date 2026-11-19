package com.jussicodes.music.data

import android.content.Context
import com.jussicodes.music.utils.AppVisibilityTracker
import com.rcmiku.ncmapi.api.account.AccountApi
import com.rcmiku.ncmapi.api.apiGet
import com.rcmiku.ncmapi.utils.CookieProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 前台私信未读巡检：App 在前台且已登录时每 [POLL_INTERVAL_MS] 拉一次会话列表，
 * 把结果喂给 [MsgSessionCache]（消息页角标与列表预览随之更新）。
 *
 * 为什么只在亮屏前台跑：后台轮询耗电、也没有观感收益；切回前台会立刻补查一次。
 * 未读清零仍走 [MsgSessionCache.markRead] 的本地已读集合 —— 服务端顶层 newMsgCount
 * 传进 [MsgSessionCache.upsert] 后会被本地已读集合扣掉，不会把读过的又点亮。
 */
object MsgUnreadMonitor {

    private const val POLL_INTERVAL_MS = 60_000L

    /** 首轮错峰：冷启动那几秒 CPU/网络最紧张，等首屏稳定后再开始巡检。 */
    private const val FIRST_POLL_DELAY_MS = 8_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var started = false

    private var pollJob: Job? = null

    /** Application.onCreate 调一次。 */
    fun start(context: Context) {
        if (started) return
        started = true
        MsgSessionCache.init(context)
        scope.launch {
            var wasForeground = false
            AppVisibilityTracker.isAppInForeground.collect { foreground ->
                if (foreground == wasForeground) return@collect
                wasForeground = foreground
                pollJob?.cancel()
                pollJob = if (foreground) scope.launch { pollLoop() } else null
            }
        }
    }

    private suspend fun pollLoop() {
        delay(FIRST_POLL_DELAY_MS)
        while (true) {
            try {
                pollOnce()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // 网络抖动之类的失败不打断巡检，下一轮再试
            }
            delay(POLL_INTERVAL_MS)
        }
    }

    private suspend fun pollOnce() {
        if (!CookieProvider.isLoggedIn()) return
        val myUid = MsgSessionCache.myUid.takeIf { it > 0L }
            ?: withTimeoutOrNull(5_000) {
                AccountApi.account().getOrNull()?.account?.profile?.userId
            }?.also { MsgSessionCache.myUid = it }
            ?: return
        val resp = withTimeoutOrNull(8_000) {
            apiGet<MsgPrivateSessionsResponse>(
                "/msg/private",
                mapOf("limit" to 30, "offset" to 0)
            ).getOrNull()
        } ?: return
        val rcMap = withTimeoutOrNull(5_000) {
            apiGet<MsgRecentContactsResponse>("/msg/recentcontact").getOrNull()
        }?.follow.orEmpty().associateBy { it.userId }
        if (rcMap.isNotEmpty()) MsgSessionCache.setRecentContacts(rcMap)
        val onlineUids = rcMap.values.filter { it.onlined }.mapTo(HashSet()) { it.userId }
        val items = resp.msgs.mapNotNull { session ->
            val other = session.otherUser(myUid) ?: return@mapNotNull null
            if (other.userId == myUid) return@mapNotNull null
            MsgSessionCache.toItem(
                s = session,
                myUid = myUid,
                online = onlineUids.contains(other.userId),
                rc = rcMap[other.userId]
            )
        }
        if (items.isNotEmpty()) MsgSessionCache.upsert(items, resp.newMsgCount)
    }
}
