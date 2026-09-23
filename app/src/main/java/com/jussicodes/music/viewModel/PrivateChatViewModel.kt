package com.jussicodes.music.viewModel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rcmiku.ncmapi.api.apiGet
import com.rcmiku.ncmapi.model.MsgPrivateHistoryResponse
import com.rcmiku.ncmapi.model.MsgPrivateMessage
import com.jussicodes.music.data.MsgSendTextResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 单人私信聊天页（从消息中心私信 tab 的联系人进入）。
 * 历史：/msg/private/history?uid={联系人}；发送：/send/text?user_ids={联系人}&msg=...
 * NCM 限制：不能发私信给自己；发送成功返回 code 200 后刷新历史（历史为倒序返回）。
 */
@HiltViewModel
class PrivateChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val contactUserId: Long = savedStateHandle.get<Long>("userId") ?: 0L
    val contactName: String = savedStateHandle.get<String>("nickname") ?: ""
    val contactAvatar: String = savedStateHandle.get<String>("avatarUrl") ?: ""

    private val _history = MutableStateFlow(emptyList<MsgPrivateMessage>())
    val history: StateFlow<List<MsgPrivateMessage>> = _history.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    private val _sendError = MutableStateFlow<String?>(null)
    val sendError: StateFlow<String?> = _sendError.asStateFlow()

    /** 每次历史刷新 +1（发送成功后），界面据此滚到底部。 */
    private val _refreshTick = MutableStateFlow(0)
    val refreshTick: StateFlow<Int> = _refreshTick.asStateFlow()

    init {
        loadHistory()
    }

    fun loadHistory() {
        if (contactUserId <= 0) {
            _isLoading.value = false
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            val result = apiGet<MsgPrivateHistoryResponse>(
                "/msg/private/history",
                mapOf("uid" to contactUserId, "limit" to 30)
            )
            result.onSuccess { _history.value = it.msgs }
            _refreshTick.value += 1
            _isLoading.value = false
        }
    }

    fun send(text: String) {
        val message = text.trim()
        if (message.isEmpty() || _isSending.value || contactUserId <= 0) return
        _isSending.value = true
        _sendError.value = null
        viewModelScope.launch {
            val result = apiGet<MsgSendTextResponse>(
                "/send/text",
                mapOf("user_ids" to contactUserId, "msg" to message)
            )
            result.onSuccess { body ->
                // NCM 用 body.code 传状态：301 = 未登录，非 200 一律视为失败
                if (body.code == 200) {
                    loadHistory()
                } else {
                    _sendError.value = if (body.code == 301) "登录已失效，请重新登录" else "发送失败 (code ${body.code})"
                }
            }.onFailure {
                _sendError.value = it.message ?: "发送失败"
            }
            _isSending.value = false
        }
    }
}
