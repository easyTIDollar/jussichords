package com.jussicodes.music.data

import com.rcmiku.ncmapi.api.account.AccountApi
import com.rcmiku.ncmapi.api.apiGet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 最近私信联系人的应用级缓存，与消息页私信列表共用同一数据源（/msg/recentcontact），
 * 分享菜单打开时直接复用，不再每个分享入口各发一次请求。
 *
 * 写入路径：[publish]（消息页 ViewModel 拉取成功后回写）。
 * 读取路径：[ensureLoaded]（分享菜单若缓存已有则直接读；没有则懒加载一次）。
 * 拉取带 10s 超时保护且失败不重试，避免分享菜单"一直转圈"。
 */
object MsgContactCache {

    /** 仅保留普通用户(0) / 官方号(207)，与消息页同一标准。 */
    val allowedUserTypes = setOf(0, 207)

    /** null = 未加载；空列表 = 已加载但无可发对象。 */
    private val _contacts = MutableStateFlow<List<MsgRecentContact>?>(null)
    val contacts: StateFlow<List<MsgRecentContact>?> = _contacts.asStateFlow()

    @Volatile
    private var loading = false
    @Volatile
    private var fetchFailed = false

    /** 消息页 ViewModel 拉取成功后回写（已按 userType 过滤；myUid>0 时顺带排除自己）。 */
    fun publish(contacts: List<MsgRecentContact>, myUid: Long = 0) {
        _contacts.value = contacts
            .filter { it.userId != myUid }
            .distinctBy { it.userId }
        fetchFailed = false
    }

    /** 分享菜单进入时调用：缓存已有则直接读，否则懒加载一次（带超时，失败不重试）。 */
    fun ensureLoaded(scope: CoroutineScope) {
        if (_contacts.value != null || loading || fetchFailed) return
        loading = true
        scope.launch {
            val meUid = withTimeoutOrNull(5_000) {
                AccountApi.account().getOrNull()?.account?.profile?.userId
            } ?: 0L
            _contacts.value = withTimeoutOrNull(10_000) {
                apiGet<MsgRecentContactsResponse>("/msg/recentcontact")
            }?.fold(
                onSuccess = { list ->
                    list.follow
                        .filter { it.userType in allowedUserTypes }
                        .filter { it.userId != meUid }
                        .distinctBy { it.userId }
                },
                onFailure = { emptyList() }
            )
            if (_contacts.value.isNullOrEmpty()) fetchFailed = true
            loading = false
        }
    }
}
