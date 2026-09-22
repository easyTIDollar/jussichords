package com.jussicodes.music.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rcmiku.ncmapi.api.account.AccountApi
import com.rcmiku.ncmapi.api.msg.MsgApi
import com.rcmiku.ncmapi.model.MsgComment
import com.rcmiku.ncmapi.model.MsgForward
import com.rcmiku.ncmapi.model.MsgNotice
import com.rcmiku.ncmapi.model.MsgPrivateMessage
import com.jussicodes.music.data.MsgSessionCache
import com.jussicodes.music.data.MsgPrivateSession
import com.jussicodes.music.data.MsgPrivateSessionsResponse
import com.jussicodes.music.data.MsgRecentContactsResponse
import com.rcmiku.ncmapi.api.apiGet
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 消息中心页。
 * 提速：去掉「全屏 isLoading」，各区块（私信会话 / 评论 / @我 / 通知 / 小秘书私信）
 * 并行且独立 loading——私信 tab 是默认首位，只等 /msg/private 一个请求，
 * 不再被最慢的区块拖住首屏；未就绪的区块在各自列表上方转圈，就绪即显示。
 *
 * 私信 tab（/msg/private）：会话列表（预览 + 未读 + 时间），按 lastMsgTime 倒序；
 * /msg/recentcontact 仅作「在线状态」补丁（onlined），不进列表。
 * 客户端仅保留 userType 0/207 的会话（音乐人/商家/系统号过滤）。
 */
@HiltViewModel
class MessagesScreenViewModel @Inject constructor() : ViewModel() {

    /** 登录账号 uid；AccountApi 不可用/未登录时恒为 0。 */
    private val _myUid = MutableStateFlow(0L)
    val myUid: StateFlow<Long> = _myUid.asStateFlow()

    // —— 私信会话（默认 tab，数据源 /msg/private + /msg/recentcontact 在线状态合并）——
    private val _sessions = MutableStateFlow(emptyList<MsgSessionCache.Item>())
    val sessions: StateFlow<List<MsgSessionCache.Item>> = _sessions.asStateFlow()
    private val _sessionsLoading = MutableStateFlow(true)
    val sessionsLoading: StateFlow<Boolean> = _sessionsLoading.asStateFlow()
    private val _sessionsHasMore = MutableStateFlow(false)
    val sessionsHasMore: StateFlow<Boolean> = _sessionsHasMore.asStateFlow()
    /** 分页追加进行中标记（与首屏 _sessionsLoading 分开，追加时不重触发整表转圈）。 */
    @Volatile
    private var moreLoading = false
    // NCM 视角的原始返回条数（未过滤前）。分页 offset 必须用它，
    // 否则客户端过滤掉音乐人/系统号后列表变短，下一页 offset 错位、漏拉或重拉。
    private var rawFetched = 0

    private val _comments = MutableStateFlow(emptyList<MsgComment>())
    val comments: StateFlow<List<MsgComment>> = _comments.asStateFlow()
    private val _commentsLoading = MutableStateFlow(true)
    val commentsLoading: StateFlow<Boolean> = _commentsLoading.asStateFlow()

    private val _forwards = MutableStateFlow(emptyList<MsgForward>())
    val forwards: StateFlow<List<MsgForward>> = _forwards.asStateFlow()
    private val _forwardsLoading = MutableStateFlow(true)
    val forwardsLoading: StateFlow<Boolean> = _forwardsLoading.asStateFlow()

    private val _notices = MutableStateFlow(emptyList<MsgNotice>())
    val notices: StateFlow<List<MsgNotice>> = _notices.asStateFlow()
    private val _noticesLoading = MutableStateFlow(true)
    val noticesLoading: StateFlow<Boolean> = _noticesLoading.asStateFlow()

    private val _privateMsgs = MutableStateFlow(emptyList<MsgPrivateMessage>())
    val privateMsgs: StateFlow<List<MsgPrivateMessage>> = _privateMsgs.asStateFlow()
    private val _privateMsgsLoading = MutableStateFlow(true)
    val privateMsgsLoading: StateFlow<Boolean> = _privateMsgsLoading.asStateFlow()

    init {
        refresh()
    }

    /** 重新拉取全部区块（进页面 & 下拉刷新共用）。各区块独立 loading，互不阻塞首屏。 */
    fun refresh() {
        viewModelScope.launch {
            val uid = AccountApi.account().getOrNull()?.account?.profile?.userId ?: 0L
            _myUid.value = uid

            // 私信会话：/msg/private 为主，/msg/recentcontact 仅补在线状态
            launch {
                _sessionsLoading.value = true
                _sessionsHasMore.value = false
                rawFetched = 0
                val resp = apiGet<MsgPrivateSessionsResponse>(
                    "/msg/private",
                    mapOf("limit" to PAGE_SIZE, "offset" to 0)
                ).getOrNull()
                if (resp != null) {
                    rawFetched = resp.msgs.size
                    val online = apiGet<MsgRecentContactsResponse>("/msg/recentcontact")
                        .getOrNull()?.follow?.filter { it.onlined }?.mapTo(HashSet()) { it.userId }
                        ?: emptySet()
                    val items = mergeSessions(resp.msgs, uid, online)
                    _sessions.value = items
                    _sessionsHasMore.value = resp.more
                    MsgSessionCache.publish(items, resp.newMsgCount)
                } else {
                    _sessions.value = emptyList()
                }
                _sessionsLoading.value = false
            }
            launch {
                _forwardsLoading.value = true
                MsgApi.forwards().onSuccess { _forwards.value = it.forwards }
                _forwardsLoading.value = false
            }
            launch {
                _noticesLoading.value = true
                MsgApi.notices().onSuccess { _notices.value = it.notices }
                _noticesLoading.value = false
            }
            launch {
                _privateMsgsLoading.value = true
                MsgApi.privateHistory().onSuccess { _privateMsgs.value = it.msgs }
                _privateMsgsLoading.value = false
            }
            if (uid > 0) {
                launch {
                    _commentsLoading.value = true
                    MsgApi.comments(uid = uid).onSuccess { _comments.value = it.comments }
                    _commentsLoading.value = false
                }
            } else {
                // 未登录：评论端点要求自身 uid，直接给空态、不请求
                _comments.value = emptyList()
                _commentsLoading.value = false
            }
        }
    }

    /** 会话列表分页加载（more=true 时）。offset = NCM 视角原始返回条数 rawFetched。 */
    fun loadMoreSessions() {
        val current = _sessions.value
        if (!_sessionsHasMore.value || _sessionsLoading.value || moreLoading) return
        moreLoading = true
        viewModelScope.launch {
            val uid = _myUid.value
            val offset = rawFetched
            val resp = apiGet<MsgPrivateSessionsResponse>(
                "/msg/private",
                mapOf("limit" to PAGE_SIZE, "offset" to offset)
            ).getOrNull()
            if (resp != null) {
                rawFetched += resp.msgs.size
                val appended = resp.msgs.mapNotNull { s -> MsgSessionCache.toItem(s, uid, false) }
                _sessions.value = current + appended
                _sessionsHasMore.value = resp.more
                MsgSessionCache.publish(_sessions.value, resp.newMsgCount)
            }
            moreLoading = false
        }
    }

    /**
     * 会话 → 缓存 Item（过滤 userType 0/207、排除自己、解析预览、并入在线状态）。
     * 纯函数，不触碰缓存状态。
     */
    private fun mergeSessions(
        msgs: List<MsgPrivateSession>,
        uid: Long,
        onlineUids: Set<Long>
    ): List<MsgSessionCache.Item> =
        msgs.mapNotNull { s ->
            val other = s.otherUser(uid)
            MsgSessionCache.toItem(s, uid, other?.let { onlineUids.contains(it.userId) } ?: false)
        }

    private companion object {
        const val PAGE_SIZE = 30
    }
}
