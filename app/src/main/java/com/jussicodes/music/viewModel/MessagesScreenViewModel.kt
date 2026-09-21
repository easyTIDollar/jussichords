package com.jussicodes.music.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rcmiku.ncmapi.api.account.AccountApi
import com.rcmiku.ncmapi.api.msg.MsgApi
import com.rcmiku.ncmapi.model.MsgComment
import com.rcmiku.ncmapi.model.MsgForward
import com.rcmiku.ncmapi.model.MsgNotice
import com.rcmiku.ncmapi.model.MsgPrivateMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 消息中心页。先取登录 uid，再并行拉四个区块（评论 / @我 / 通知 / 私信）。
 * 评论端点要求 uid 与登录账号一致，未登录（uid=0）时不请求；私信小秘书（9003）无需登录。
 */
@HiltViewModel
class MessagesScreenViewModel @Inject constructor() : ViewModel() {

    private val _myUid = MutableStateFlow(0L)
    val myUid: StateFlow<Long> = _myUid.asStateFlow()

    private val _comments = MutableStateFlow(emptyList<MsgComment>())
    val comments: StateFlow<List<MsgComment>> = _comments.asStateFlow()

    private val _forwards = MutableStateFlow(emptyList<MsgForward>())
    val forwards: StateFlow<List<MsgForward>> = _forwards.asStateFlow()

    private val _notices = MutableStateFlow(emptyList<MsgNotice>())
    val notices: StateFlow<List<MsgNotice>> = _notices.asStateFlow()

    private val _privateMsgs = MutableStateFlow(emptyList<MsgPrivateMessage>())
    val privateMsgs: StateFlow<List<MsgPrivateMessage>> = _privateMsgs.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        refresh()
    }

    /** 重新拉取全部四个区块（进页面 & 下拉刷新共用）。 */
    fun refresh() {
        _isLoading.value = true
        viewModelScope.launch {
            val uid = AccountApi.account().getOrNull()?.account?.profile?.userId ?: 0L
            _myUid.value = uid
            val jobs = listOf(
                async { MsgApi.forwards().onSuccess { _forwards.value = it.forwards } },
                async { MsgApi.notices().onSuccess { _notices.value = it.notices } },
                async { MsgApi.privateHistory().onSuccess { _privateMsgs.value = it.msgs } }
            ) + (if (uid > 0) listOf(
                async { MsgApi.comments(uid = uid).onSuccess { _comments.value = it.comments } }
            ) else emptyList())
            jobs.awaitAll()
            _isLoading.value = false
        }
    }
}
