package com.jussicodes.music.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rcmiku.ncmapi.api.account.AccountApi
import com.jussicodes.music.data.MsgRecentContact
import com.jussicodes.music.data.MsgSessionCache
import com.jussicodes.music.data.MsgPrivateSession
import com.jussicodes.music.data.MsgPrivateSessionsResponse
import com.jussicodes.music.data.MsgRecentContactsResponse
import com.rcmiku.ncmapi.api.apiGet
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 消息中心页私信 tab。只拉 /msg/private（会话列表）+ /msg/recentcontact（在线/VIP/互关补丁）。
 * 列表数据在 MsgSessionCache（单一数据源），这里只留分页状态位。
 */
@HiltViewModel
class MessagesScreenViewModel @Inject constructor() : ViewModel() {

    private val _myUid = MutableStateFlow(0L)
    val myUid: StateFlow<Long> = _myUid.asStateFlow()

    // 私信会话分页状态
    private val _sessionsLoading = MutableStateFlow(true)
    val sessionsLoading: StateFlow<Boolean> = _sessionsLoading.asStateFlow()
    private val _sessionsHasMore = MutableStateFlow(false)
    val sessionsHasMore: StateFlow<Boolean> = _sessionsHasMore.asStateFlow()
    /** 分页追加进行中标记（与首屏 _sessionsLoading 分开，追加时不重触发整表转圈）。 */
    private val _sessionsMoreLoading = MutableStateFlow(false)
    val sessionsMoreLoading: StateFlow<Boolean> = _sessionsMoreLoading.asStateFlow()
    /** 最近一次分页拉取失败（无缝加载：不显示尾部转圈，只在失败时给一行"点击重试"）。 */
    private val _sessionsMoreFailed = MutableStateFlow(false)
    val sessionsMoreFailed: StateFlow<Boolean> = _sessionsMoreFailed.asStateFlow()
    @Volatile
    private var moreLoading = false
    // NCM 视角的原始返回条数（未过滤前）。分页 offset 必须用它，
    // 否则客户端过滤掉音乐人/商家/系统号后列表变短，下一页 offset 错位、漏拉或重拉。
    private var rawFetched = 0
    // 最近一次 /msg/recentcontact 快照（userId → 联系人），补 VIP/互关/在线，best-effort。
    private var rcMap: Map<Long, MsgRecentContact> = emptyMap()

    init {
        refresh()
    }

    /**
     * 进页面 / 下拉刷新：清掉本地缓存（会话快照 + 手动排序），重新拉私信会话；返回 Job 供下拉指示器等待完成。 */
    fun refresh(clearCache: Boolean = false): Job {
        return viewModelScope.launch {
            val uid = AccountApi.account().getOrNull()?.account?.profile?.userId ?: 0L
            _myUid.value = uid

            _sessionsLoading.value = true
            _sessionsHasMore.value = false
            rawFetched = 0
            // 下拉刷新 = 全量重拉 + 清缓存（旧会话/手动序失效，重新从首页拉）
            if (clearCache) MsgSessionCache.clearForRefresh()
            val p = async {
                apiGet<MsgPrivateSessionsResponse>(
                    "/msg/private",
                    mapOf("limit" to PAGE_SIZE, "offset" to 0)
                ).getOrNull()
            }
            val r = async {
                withTimeoutOrNull(5_000) {
                    apiGet<MsgRecentContactsResponse>("/msg/recentcontact").getOrNull()
                }
            }
            val resp = p.await()
            rcMap = r.await()?.follow?.associateBy { it.userId } ?: emptyMap()
            MsgSessionCache.setRecentContacts(rcMap)
            if (resp != null) {
                rawFetched = resp.msgs.size
                val items = mergeSessions(resp.msgs, uid)
                _sessionsHasMore.value = resp.more
                MsgSessionCache.upsert(items, resp.newMsgCount)
            }
            _sessionsLoading.value = false
        }
    }

    /** 会话列表分页加载（more=true 时）。offset = NCM 视角原始返回条数 rawFetched；追加走缓存单一数据源。 */
    fun loadMoreSessions() {
        if (!_sessionsHasMore.value || _sessionsLoading.value || moreLoading) return
        moreLoading = true
        _sessionsMoreLoading.value = true
        _sessionsMoreFailed.value = false
        viewModelScope.launch {
            val uid = _myUid.value
            val offset = rawFetched
            val resp = apiGet<MsgPrivateSessionsResponse>(
                "/msg/private",
                mapOf("limit" to PAGE_SIZE, "offset" to offset)
            ).getOrNull()
            _sessionsMoreFailed.value = resp == null
            if (resp != null) {
                rawFetched += resp.msgs.size
                val appended = resp.msgs.mapNotNull { s -> mergeOne(s, uid) }
                MsgSessionCache.upsert(appended)
                _sessionsHasMore.value = resp.more
                // 本页 0 条（服务端到底）：停掉避免"滑到底部一直重试"
                if (resp.msgs.isEmpty()) _sessionsHasMore.value = false
            }
            moreLoading = false
            _sessionsMoreLoading.value = false
        }
    }

    /**
     * 单条会话 → Item（排除自己、解析预览、并入 recentcontact 快照的
     * 在线/VIP/互关）。纯函数，不触碰缓存状态；rcMap 由 refresh 维护。
     */
    private fun mergeOne(s: MsgPrivateSession, uid: Long): MsgSessionCache.Item? {
        val other = s.otherUser(uid) ?: return null
        val rc = rcMap[other.userId]
        return MsgSessionCache.toItem(s, uid, rc?.onlined == true, rc)
    }

    /** 会话列表 → 缓存 Item 列表（首屏用）。 */
    private fun mergeSessions(msgs: List<MsgPrivateSession>, uid: Long): List<MsgSessionCache.Item> =
        msgs.mapNotNull { mergeOne(it, uid) }

    private companion object {
        const val PAGE_SIZE = 30
    }
}
