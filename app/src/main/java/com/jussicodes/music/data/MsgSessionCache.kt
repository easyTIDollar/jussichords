package com.jussicodes.music.data

import com.rcmiku.ncmapi.api.account.AccountApi
import com.rcmiku.ncmapi.api.apiGet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.HashSet
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * 私信会话列表的应用级缓存：/msg/private 为骨架（会话+预览+未读），
 * /msg/recentcontact 补皮肤（在线 / VIP / 互关），按 userId 合并。
 *
 * 写入路径：[publish]（消息页 ViewModel 拉取成功后回写）、
 * [markRead]（进聊天页本地清未读——NCM 无已读标记 API，见实测 /msg/private/read 404）。
 * 读取路径：[ensureLoaded]（App 启动预热 / 分享菜单打开时若缓存为空则并行懒加载一次，失败不重试）。
 */
object MsgSessionCache {

    /** 可私信的对象：普通用户(0) 与 207；音乐人/商家/系统号一律过滤。 */
    val allowedUserTypes = setOf(0, 207)

    private val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }

    /** 合并后的会话行：对方资料 + 预览 + 时间 + 未读 + 在线 + VIP/互关。 */
    data class Item(
        val user: MsgSessionUser,
        val preview: String,
        val lastMsgTime: Long,
        val newMsgCount: Int,
        val onlined: Boolean,
        val vipType: Int = 0,
        val mutual: Boolean = false
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

    /** 全账号未读总数（/msg/private 顶层 newMsgCount，markRead 后本地递减），>0 时消息入口显红点。 */
    private val _unread = MutableStateFlow(0)
    val unread: StateFlow<Int> = _unread.asStateFlow()

    /** 本地已读：进聊天页清零的会话 uid（纯本地观感，NCM 无已读 API；刷新以服务端值为准）。 */
    private val readUids = Collections.synchronizedSet(HashSet<Long>())

    @Volatile
    private var loading = false
    @Volatile
    private var fetchFailed = false

    /**
     * 纯转换：单条会话 → 缓存 Item（过滤 userType 0/207、排除自己、解析预览）。
     * VIP/互关优先取会话自带字段，缺失时回退 [rc]（recentcontact 快照）；不触碰缓存状态。
     */
    fun toItem(s: MsgPrivateSession, myUid: Long, online: Boolean, rc: MsgRecentContact? = null): Item? {
        val other = s.otherUser(myUid) ?: return null
        if (other.userType !in allowedUserTypes) return null
        if (other.userId == myUid) return null
        val vipType = if (other.vipType != 0) other.vipType else (rc?.vipType ?: 0)
        val mutual = other.mutual || rc?.mutual == true
        return Item(
            user = other,
            preview = parsePreview(s.user.lastMsg),
            lastMsgTime = s.user.lastMsgTime,
            newMsgCount = s.user.newMsgCount,
            onlined = online,
            vipType = vipType,
            mutual = mutual
        )
    }

    /** 消息页 ViewModel 拉取成功后回写（已转成 Item、已过滤；本地已读清零同步套用，刷新后以服务端值为准）。 */
    fun publish(items: List<Item>, totalUnread: Int = 0) {
        val (cleaned, removed) = synchronized(readUids) {
            val removedTotal = items.sumOf {
                if (it.user.userId in readUids) it.newMsgCount else 0
            }
            val cleanedList = items.map {
                if (it.user.userId in readUids) it.copy(newMsgCount = 0) else it
            }
            cleanedList to removedTotal
        }
        _items.value = cleaned.distinctBy { it.user.userId }
        _unread.value = (totalUnread - removed).coerceAtLeast(0)
        fetchFailed = false
    }

    /** 分页追加（offset>0）：把新一页 Item 拼到已有列表，按 uid 去重。 */
    fun append(items: List<Item>) {
        val merged = (_items.value ?: emptyList()) + items
        _items.value = merged.distinctBy { it.user.userId }
        fetchFailed = false
    }

    /**
     * 分享菜单打开 / App 启动预热时调用：缓存已有则直接读；否则**并行**拉
     * 账号 uid（5s）+ /msg/private（主数据，10s）+ /msg/recentcontact（VIP/互关/在线 best-effort，5s），
     * 总时延 = 三路最慢者（旧实现是三段串行，最坏 20s——分享菜单首次打开"转很久"的根因）；失败不重试。
     */
    fun ensureLoaded(scope: CoroutineScope) {
        if (_items.value != null || loading || fetchFailed) return
        loading = true
        scope.launch {
            val a = async {
                withTimeoutOrNull(5_000) {
                    AccountApi.account().getOrNull()?.account?.profile?.userId
                } ?: 0L
            }
            val p = async {
                withTimeoutOrNull(10_000) {
                    apiGet<MsgPrivateSessionsResponse>(
                        "/msg/private",
                        mapOf("limit" to 30, "offset" to 0)
                    ).getOrNull()
                }
            }
            val r = async {
                withTimeoutOrNull(5_000) {
                    apiGet<MsgRecentContactsResponse>("/msg/recentcontact").getOrNull()
                }
            }
            val meUid = a.await()
            val resp = p.await()
            val rcList = r.await()?.follow.orEmpty()
            val rcMap = rcList.associateBy { it.userId }
            val onlineUids = rcList.filter { it.onlined }.mapTo(HashSet()) { it.userId }
            if (resp != null && _items.value == null) {
                // _items 已被消息页 ViewModel 的分页逻辑填充（refresh/loadMore 先跑完）时
                // 放弃本次预热发布，避免"首屏一页"覆盖掉 ViewModel 已追加的多页数据造成重复
                val items = resp.msgs.mapNotNull { s ->
                    val other = s.otherUser(meUid) ?: return@mapNotNull null
                    if (other.userType !in allowedUserTypes) return@mapNotNull null
                    if (other.userId == meUid) return@mapNotNull null
                    toItem(s, meUid, onlineUids.contains(other.userId), rcMap[other.userId])
                }
                publish(items, resp.newMsgCount)
            } else if (resp == null && _items.value == null) {
                // 只有缓存还没被消息页 ViewModel 填充过时才写空态，避免冲掉已分页出的数据
                _items.value = emptyList()
                fetchFailed = true
            }
            loading = false
        }
    }

    /** 进入聊天页：本地清掉该会话未读（NCM 无私信已读标记 API；刷新恢复服务端值，纯本地观感）。 */
    fun markRead(userId: Long) {
        if (userId <= 0) return
        val current = _items.value ?: return
        val target = current.firstOrNull { it.user.userId == userId } ?: return
        if (target.newMsgCount <= 0) return
        readUids.add(userId)
        _items.value = current.map { if (it.user.userId == userId) it.copy(newMsgCount = 0) else it }
        _unread.value = (_unread.value - target.newMsgCount).coerceAtLeast(0)
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
