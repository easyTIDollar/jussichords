package com.jussicodes.music.data

import com.rcmiku.ncmapi.api.account.AccountApi
import com.rcmiku.ncmapi.api.apiGet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.text.SimpleDateFormat

/**
 * 私信会话列表的应用级缓存：/msg/private 为骨架（会话+预览+未读），
 * /msg/recentcontact 补皮肤（在线状态），按 userId 合并。
 *
 * 写入路径：[publish]（消息页 ViewModel 拉取成功后回写）。
 * 读取路径：[ensureLoaded]（分享菜单打开时若缓存为空则懒加载一次，带超时、失败不重试）。
 */
object MsgSessionCache {

    /** 可私信的对象：普通用户(0) 与 207；音乐人/商家/系统号一律过滤。 */
    val allowedUserTypes = setOf(0, 207)

    private val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }

    /** 合并后的会话行：对方资料 + 预览 + 时间 + 未读 + 在线。 */
    data class Item(
        val user: MsgSessionUser,
        val preview: String,
        val lastMsgTime: Long,
        val newMsgCount: Int,
        val onlined: Boolean
    ) {
        /** 今天→HH:mm，今年→MM-dd，跨年→yyyy-MM-dd。用 Calendar 避免 java.time 依赖 desugaring。 */
        val displayTime: String
            get() {
                if (lastMsgTime <= 0) return ""
                val tz = TimeZone.getDefault()
                val cal = Calendar.getInstance(tz).apply { timeInMillis = lastMsgTime }
                val now = Calendar.getInstance(tz)
                val year = cal.get(Calendar.YEAR)
                val month = cal.get(Calendar.MONTH) + 1
                val dayOfMonth = cal.get(Calendar.DAY_OF_MONTH)
                val nowYear = now.get(Calendar.YEAR)
                val nowMonth = now.get(Calendar.MONTH) + 1
                val nowDay = now.get(Calendar.DAY_OF_MONTH)
                val sf = SimpleDateFormat("HH:mm", Locale.getDefault())
                return when {
                    year == nowYear && month == nowMonth && dayOfMonth == nowDay ->
                        sf.format(cal.time)
                    year == nowYear ->
                        "%02d-%02d".format(month, dayOfMonth)
                    else ->
                        "%d-%02d-%02d".format(year, month, dayOfMonth)
                }
            }
    }

    /** null = 未加载；空列表 = 已加载但无可发会话。 */
    private val _items = MutableStateFlow<List<Item>?>(null)
    val items: StateFlow<List<Item>?> = _items.asStateFlow()

    /** 全账号未读总数（/msg/private 顶层 newMsgCount），>0 时消息入口显红点。 */
    private val _unread = MutableStateFlow(0)
    val unread: StateFlow<Int> = _unread.asStateFlow()

    @Volatile private var loading = false
    @Volatile private var fetchFailed = false

    /**
     * 纯转换：单条会话 → 缓存 Item（过滤 userType 0/207、排除自己、解析预览）。
     * 不触碰缓存状态；供 ViewModel 分页追加时复用。
     */
    fun toItem(s: MsgPrivateSession, myUid: Long, online: Boolean): Item? {
        val other = s.otherUser(myUid) ?: return null
        if (other.userType !in allowedUserTypes) return null
        if (other.userId == myUid) return null
        return Item(
            user = other,
            preview = parsePreview(s.user.lastMsg),
            lastMsgTime = s.user.lastMsgTime,
            newMsgCount = s.user.newMsgCount,
            onlined = online
        )
    }

    /** 消息页 ViewModel 拉取成功后回写（已转成 Item、已过滤；onlineUids 在 toItem 时已并入 onlined）。 */
    fun publish(items: List<Item>, totalUnread: Int = 0) {
        _items.value = items.distinctBy { it.user.userId }
        _unread.value = totalUnread
        fetchFailed = false
    }

    /** 分享菜单进入时调用：缓存已有则直接读，否则懒加载一次（带超时，失败不重试）。 */
    fun ensureLoaded(scope: CoroutineScope) {
        if (_items.value != null || loading || fetchFailed) return
        loading = true
        scope.launch {
            val meUid = withTimeoutOrNull(5_000) {
                AccountApi.account().getOrNull()?.account?.profile?.userId
            } ?: 0L
            val resp = withTimeoutOrNull(10_000) {
                apiGet<MsgPrivateSessionsResponse>("/msg/private", mapOf("limit" to 30))
            }?.getOrNull()
            if (resp != null) {
                // 在线状态 best-effort：超时/失败不影响主数据
                val online = withTimeoutOrNull(5_000) {
                    apiGet<MsgRecentContactsResponse>("/msg/recentcontact").getOrNull()
                }?.follow?.filter { it.onlined }?.mapTo(HashSet()) { it.userId } ?: emptySet()
                val items = resp.msgs.mapNotNull { s ->
                    val other = s.otherUser(meUid)
                    toItem(s, meUid, other?.let { online.contains(it.userId) } ?: false)
                }
                publish(items, resp.newMsgCount)
            } else {
                _items.value = emptyList()
                fetchFailed = true
            }
            loading = false
        }
    }

    /**
     * lastMsg（两层 JSON 字符串）→ 预览文案。
     * 内层 type：1=卡片（取 title）、5=声音、12=活动、6=纯文本、23=系统/推送长文案；
     * 解析失败兜底"新消息"，保证不崩。
     */
    fun parsePreview(lastMsg: String): String {
        if (lastMsg.isBlank()) return "新消息"
        return try {
            val outer = lenientJson.parseToJsonElement(lastMsg).jsonObject
            val innerRaw = outer["msg"]?.jsonPrimitive?.content ?: return "新消息"
            val inner = lenientJson.parseToJsonElement(innerRaw).jsonObject
            val type = inner["type"]?.jsonPrimitive?.intOrNull ?: 0
            val title = inner["title"]?.jsonPrimitive?.content.orEmpty()
            val text = inner["msg"]?.jsonPrimitive?.content.orEmpty()
            val raw = when (type) {
                6 -> text.takeIf { it.isNotBlank() } ?: "新消息"
                1, 5, 12 -> title.takeIf { it.isNotBlank() } ?: "分享卡片"
                else -> text.takeIf { it.isNotBlank() } ?: "新消息"
            }
            if (raw.length > 40) raw.take(40) + "…" else raw
        } catch (_: Exception) {
            "新消息"
        }
    }
}
