package com.jussicodes.music.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rcmiku.ncmapi.api.account.AccountApi
import com.rcmiku.ncmapi.api.msg.MsgApi
import com.rcmiku.ncmapi.model.MsgComment
import com.rcmiku.ncmapi.model.MsgForward
import com.rcmiku.ncmapi.model.MsgNotice
import com.rcmiku.ncmapi.model.MsgPrivateMessage
import com.jussicodes.music.data.MsgContactCache
import com.jussicodes.music.data.MsgRecentContact
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
 * 提速：去掉「全屏 isLoading」，各区块（私信联系人 / 评论 / @我 / 通知 / 小秘书私信）
 * 并行且独立 loading——私信 tab 是默认首位，只等 /msg/recentcontact 一个请求，
 * 不再被最慢的区块拖住首屏；未就绪的区块在各自列表上方转圈，就绪即显示。
 * 最近联系（/msg/recentcontact）：按最近私信时间排序的联系人快照（约 43 人封顶），客户端仅保留 userType 0/207。
 */
@HiltViewModel
class MessagesScreenViewModel @Inject constructor() : ViewModel() {

    /** 登录账号 uid；AccountApi 不可用/未登录时恒为 0。 */
    private val _myUid = MutableStateFlow(0L)
    val myUid: StateFlow<Long> = _myUid.asStateFlow()

    // —— 私信联系人（默认 tab，数据源 /msg/recentcontact）——
    private val _contacts = MutableStateFlow(emptyList<MsgRecentContact>())
    val contacts: StateFlow<List<MsgRecentContact>> = _contacts.asStateFlow()
    private val _contactsLoading = MutableStateFlow(true)
    val contactsLoading: StateFlow<Boolean> = _contactsLoading.asStateFlow()

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

            // 私信联系人：独立请求，默认 tab 只等它
            launch {
                _contactsLoading.value = true
                apiGet<MsgRecentContactsResponse>("/msg/recentcontact")
                    .onSuccess {
                        val filtered = it.follow.filter { c -> c.userType in CONTACT_ALLOWED_USER_TYPES }
                        _contacts.value = filtered
                        MsgContactCache.publish(filtered, uid)
                    }
                _contactsLoading.value = false
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

    private companion object {
        /** 私信联系人列表仅保留普通用户（0）与 207（官方枚举未公开，实测数据驱动）。 */
        val CONTACT_ALLOWED_USER_TYPES = setOf(0, 207)
    }
}
