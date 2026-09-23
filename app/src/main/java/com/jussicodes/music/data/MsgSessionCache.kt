package com.jussicodes.music.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.jussicodes.music.utils.dataStore
import com.rcmiku.ncmapi.api.account.AccountApi
import com.rcmiku.ncmapi.api.apiGet
import com.rcmiku.ncmapi.utils.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Collections
import java.util.HashSet
import java.util.Locale
import java.util.TimeZone

/**
 * 私信会话列表的应用级缓存：/msg/private 为骨架（会话+预览+未读），
 * /msg/recentcontact 补皮肤（在线 / VIP / 互关），按 userId 合并。
 *
 * 列表不再按 userType 过滤——所有会话（普通/官方/系统号/商家）全量放出，
 * 过滤只发生在分享菜单（本地按 [allowedUserTypes] 筛，避免给系统号发私信）。
 *
 * 持久化（DataStore，重启生效）：
 * - 全量快照（JSON）：冷启动先铺列表，0 网络；随后 [ensureLoaded] 静默并行刷新；
 * - 已删除会话 uid 集合（本地隐藏，NCM 无私信会话删除 API）；
 * - 手动拖拽排序（uid 数组；空 = 按最后消息时间倒序）。
 *
 * 写入路径：[publish]（消息页 ViewModel 全量刷新回写）、[append]（分页/预热追加）、
 * [markRead]（进聊天页本地清未读——NCM 无已读标记 API，见实测 /msg/private/read 404）、
 * [deleteSession] / [setManualOrder]（用户操作）。
 */
object MsgSessionCache {

    /** 可私信的对象：普通用户(0) 与 207；分享菜单本地过滤用，私信列表不过滤。 */
    val allowedUserTypes = setOf(0, 207)

    private val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }

    @Serializable
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

    private val KEY_SNAPSHOT = stringPreferencesKey("msgSessionsCache")
    private val KEY_DELETED = stringPreferencesKey("msgDeletedUids")
    private val KEY_ORDER = stringPreferencesKey("msgManualOrder")
    private val KEY_FILTER = stringPreferencesKey("msgFilterUserTypes")

    /** 服务端原始数据（全类型、未删、未排序）；null = 尚无磁盘快照且未拉取过。 */
    private val _allItems = MutableStateFlow<List<Item>?>(null)

    /** 展示列表 = 全量 − 已删除 + 手动序（无手动序则按时间倒序）。null 仅在 init 前。 */
    private val _items = MutableStateFlow<List<Item>?>(null)
    val items: StateFlow<List<Item>?> = _items.asStateFlow()

    private val _deletedUids = MutableStateFlow<Set<Long>>(emptySet())
    val deletedUids: StateFlow<Set<Long>> = _deletedUids.asStateFlow()

    /** 私信列表 userType 筛选：null = 显示全部（默认）；空集 = 全部隐藏。 */
    private val _filterUserTypes = MutableStateFlow<Set<Int>?>(null)
    val filterUserTypes: StateFlow<Set<Int>?> = _filterUserTypes.asStateFlow()

    /** 全账号未读总数（/msg/private 顶层 newMsgCount，markRead 后本地递减），>0 时消息入口显红点。 */
    private val _unread = MutableStateFlow(0)
    val unread: StateFlow<Int> = _unread.asStateFlow()

    /** 全量（未删、未筛）会话的 userType 分布：筛选菜单生成选项与计数用（含当前被筛掉的类型）。 */
    private val _typeCounts = MutableStateFlow<Map<Int, Int>>(emptyMap())
    val typeCounts: StateFlow<Map<Int, Int>> = _typeCounts.asStateFlow()

    /** 本地已读：进聊天页清零的会话 uid（纯本地观感，NCM 无已读 API；刷新以服务端值为准）。 */
    private val readUids = Collections.synchronizedSet(HashSet<Long>())

    @Volatile
    private var deletedSet: Set<Long> = emptySet()          // 仅主线程读写
    @Volatile
    private var manualOrder: List<Long> = emptyList()       // 仅主线程读写；空 = 时间倒序
    private val _manualOrderActive = MutableStateFlow(false)
    val manualOrderActive: StateFlow<Boolean> = _manualOrderActive.asStateFlow()

    @Volatile
    private var store: DataStore<Preferences>? = null
    @Volatile
    private var initialized = false
    @Volatile
    private var loading = false
    @Volatile
    private var liveFetched = false
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Application.onCreate 调一次：IO 线程读 DataStore 快照铺列表（冷启动 0 网络），
     * 顺带恢复删除集合与手动排序。若 live 数据（ensureLoaded/refresh）已先到位则不覆盖。
     */
    fun init(context: Context) {
        if (initialized) return
        initialized = true
        store = context.applicationContext.dataStore
        ioScope.launch {
            val ds = store ?: return@launch
            val prefs = ds.data.first()
            val raw = prefs[KEY_SNAPSHOT]?.let {
                runCatching { json.decodeFromString<List<Item>>(it) }.getOrNull()
            }.orEmpty()
            deletedSet = runCatching {
                json.decodeFromString<List<Long>>(prefs[KEY_DELETED] ?: "[]")
            }.getOrNull().orEmpty().toSet()
            _deletedUids.value = deletedSet
            manualOrder = runCatching {
                json.decodeFromString<List<Long>>(prefs[KEY_ORDER] ?: "[]")
            }.getOrNull().orEmpty()
            _manualOrderActive.value = manualOrder.isNotEmpty()
            // userType 筛选：JSON 数组；"null" / 缺省 = 显示全部（默认）
            _filterUserTypes.value = runCatching {
                val rawFilter = prefs[KEY_FILTER] ?: "null"
                if (rawFilter == "null") null
                else json.decodeFromString<List<Int>>(rawFilter).toSet()
            }.getOrNull()
            // 只有 live 数据还没到位时才用磁盘快照铺（快照可能过期，live 优先）
            if (_allItems.value == null && !liveFetched) {
                _allItems.value = raw
                // 快照里的未读总数：求和近似（冷启动观感），live 刷新后以服务端顶层值为准
                _unread.value = raw.sumOf { it.newMsgCount }
            }
            recomputeDisplay()
        }
    }

    /**
     * 纯转换：单条会话 → 缓存 Item（排除自己、解析预览）。**不过滤 userType**（列表全量展示）。
     * VIP/互关优先取会话自带字段，缺失时回退 [rc]（recentcontact 快照）；不触碰缓存状态。
     */
    fun toItem(s: MsgPrivateSession, myUid: Long, online: Boolean, rc: MsgRecentContact? = null): Item? {
        val other = s.otherUser(myUid) ?: return null
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

    /** 消息页 ViewModel 刷新/分页回写：**upsert 合并**（不截断已分页数据，重复 uid 以新数据为准）；
     * [totalUnread] 传值时按本地已读清零重算全局未读。 */
    fun upsert(items: List<Item>, totalUnread: Int? = null) {
        val cleaned = items.map {
            if (it.user.userId in readUids) it.copy(newMsgCount = 0) else it
        }
        // 新数据优先：同 uid 以本次拉取为准；旧数据里未出现的 uid 追加在后（不截断已分页数据）
        val newUids = cleaned.mapTo(HashSet()) { it.user.userId }
        _allItems.value = cleaned + (_allItems.value.orEmpty().filter { it.user.userId !in newUids })
        if (totalUnread != null) {
            val removed = _allItems.value!!.sumOf {
                if (it.user.userId in readUids) it.newMsgCount else 0
            }
            _unread.value = (totalUnread - removed).coerceAtLeast(0)
            liveFetched = true
        }
        recomputeDisplay()
        saveSnapshot()
    }

    /** 长按删除：本地隐藏该会话（NCM 无私信会话删除 API），持久化，重启保留。 */
    fun deleteSession(userId: Long) {
        if (userId <= 0) return
        if (userId in deletedSet) return
        val orderBefore = manualOrder
        deletedSet = deletedSet + userId
        _deletedUids.value = deletedSet
        manualOrder = orderBefore.filterNot { it == userId }
        _manualOrderActive.value = manualOrder.isNotEmpty()
        recomputeDisplay()
        saveDeleted()
        if (manualOrder.size != orderBefore.size) saveOrder()
    }

    /** 拖拽排序：传入当前完整展示序的 uid 数组；空列表 = 恢复时间倒序。持久化。 */
    fun setManualOrder(uids: List<Long>) {
        manualOrder = uids.distinct()
        _manualOrderActive.value = manualOrder.isNotEmpty()
        recomputeDisplay()
        saveOrder()
    }

    /** 恢复已删除的会话（顶栏"排序"按钮长按 / 会话恢复入口用）：从全量集合移除。 */
    fun restoreDeleted(userId: Long) {
        if (userId <= 0) return
        val before = manualOrder.size
        deletedSet = deletedSet - userId
        _deletedUids.value = deletedSet
        manualOrder = manualOrder.filterNot { it == userId }
        _manualOrderActive.value = manualOrder.isNotEmpty()
        recomputeDisplay()
        saveDeleted()
        if (manualOrder.size != before) saveOrder()
    }

    /** 设置 userType 筛选（null = 显示全部；空集 = 全部隐藏）。持久化。 */
    fun setFilterUserTypes(types: Set<Int>?) {
        if (types == null && _filterUserTypes.value == null) return
        _filterUserTypes.value = types
        recomputeDisplay()
        saveFilter()
    }

    /** 清除手动排序，回到时间倒序（持久化）。 */
    fun resetManualOrder() {
        if (manualOrder.isEmpty()) return
        manualOrder = emptyList()
        _manualOrderActive.value = false
        recomputeDisplay()
        saveOrder()
    }

    /** 拖拽排序（松手前，每次跨项调用一次）：只改内存 + 重算展示，不触发 IO。 */
    fun applyManualOrderLive(uids: List<Long>) {
        manualOrder = uids.distinct()
        _manualOrderActive.value = manualOrder.isNotEmpty()
        recomputeDisplay()
    }

    /** 拖拽松手：把当前 manualOrder 落盘（配合 [applyManualOrderLive]）。 */
    fun flushManualOrder() {
        if (manualOrder.isEmpty()) return
        saveOrder()
    }

    /** 拖拽排序（每次跨项触发）：在当前展示序里把 [fromUid] 移到 [toUid] 位置；只改内存，松手 [flushManualOrder] 落盘。 */
    fun moveItem(fromUid: Long, toUid: Long) {
        val current = _items.value ?: return
        val uids = current.map { it.user.userId }
        val f = uids.indexOf(fromUid)
        val t = uids.indexOf(toUid)
        if (f < 0 || t < 0 || f == t) return
        val list = uids.toMutableList()
        list.add(t, list.removeAt(f))
        applyManualOrderLive(list)
    }

    /** 一键恢复全部已删除会话（顶栏菜单用）：清空删除集合，持久化。 */
    fun restoreAllDeleted() {
        if (deletedSet.isEmpty()) return
        deletedSet = emptySet()
        _deletedUids.value = emptySet()
        recomputeDisplay()
        saveDeleted()
    }

    /** 进入聊天页：本地清掉该会话未读（NCM 无私信已读标记 API；刷新恢复服务端值，纯本地观感）。 */
    fun markRead(userId: Long) {
        if (userId <= 0) return
        val current = _allItems.value ?: return
        val target = current.firstOrNull { it.user.userId == userId } ?: return
        if (target.newMsgCount <= 0) return
        readUids.add(userId)
        _allItems.value = current.map { if (it.user.userId == userId) it.copy(newMsgCount = 0) else it }
        _unread.value = (_unread.value - target.newMsgCount).coerceAtLeast(0)
        recomputeDisplay()
        saveSnapshot()
    }

    /**
     * 分享菜单打开 / App 启动预热时调用：已有 live 数据则跳过；否则**并行**拉
     * 账号 uid（5s）+ /msg/private（主数据，8s）+ /msg/recentcontact（在线/VIP/互关，5s），
     * 总时延 = 三路最慢者；成功后 [append]（不截断 VM 已分页出的数据），失败保留磁盘快照。
     */
    fun ensureLoaded(scope: CoroutineScope) {
        if (liveFetched || loading) return
        loading = true
        scope.launch {
            val a = async {
                withTimeoutOrNull(5_000) {
                    AccountApi.account().getOrNull()?.account?.profile?.userId
                } ?: 0L
            }
            val p = async {
                withTimeoutOrNull(8_000) {
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
            if (resp != null) {
                val items = resp.msgs.mapNotNull { s ->
                    val other = s.otherUser(meUid) ?: return@mapNotNull null
                    if (other.userId == meUid) return@mapNotNull null
                    toItem(s, meUid, onlineUids.contains(other.userId), rcMap[other.userId])
                }
                upsert(items, resp.newMsgCount)
            }
            loading = false
        }
    }

    /** 展示列表 = 全量 − 删除 − userType 筛选 + 手动序（无手动序按最后消息时间倒序）。
     * 同时发布全量（未删、未筛）的 userType 分布，供筛选菜单生成选项。 */
    private fun recomputeDisplay() {
        val all = _allItems.value ?: return
        val notDeleted = all.filter { it.user.userId !in deletedSet }
        _typeCounts.value = notDeleted.groupingBy { it.user.userType }.eachCount()
        val filter = _filterUserTypes.value
        val visible = notDeleted.filter { filter == null || it.user.userType in filter }
        _items.value = if (manualOrder.isEmpty()) {
            visible.sortedByDescending { it.lastMsgTime }
        } else {
            val byUid = visible.associateBy { it.user.userId }
            val pinned = manualOrder.mapNotNull { byUid[it] }
            val pinnedSet = pinned.mapTo(HashSet()) { it.user.userId }
            val rest = visible.filter { it.user.userId !in pinnedSet }
                .sortedByDescending { it.lastMsgTime }
            pinned + rest
        }
    }

    private fun saveSnapshot() {
        val s = store ?: return
        val payload = json.encodeToString(_allItems.value ?: emptyList())
        ioScope.launch { s.edit { it[KEY_SNAPSHOT] = payload } }
    }

    private fun saveDeleted() {
        val s = store ?: return
        val payload = json.encodeToString(deletedSet.toList())
        ioScope.launch { s.edit { it[KEY_DELETED] = payload } }
    }

    private fun saveOrder() {
        val s = store ?: return
        val payload = json.encodeToString(manualOrder)
        ioScope.launch { s.edit { it[KEY_ORDER] = payload } }
    }

    private fun saveFilter() {
        val s = store ?: return
        val payload = _filterUserTypes.value?.let { json.encodeToString(it.toList()) } ?: "null"
        ioScope.launch { s.edit { it[KEY_FILTER] = payload } }
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
