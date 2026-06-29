package com.jussicodes.music.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rcmiku.ncmapi.api.account.AccountApi
import com.rcmiku.ncmapi.model.SearchUser
import com.rcmiku.ncmapi.model.UserFollowResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class UserFollowType {
    FOLLOWS,
    FOLLOWEDS
}

@HiltViewModel
class UserFollowScreenViewModel @Inject constructor() : ViewModel() {
    private val _follows = MutableStateFlow<UserFollowResponse?>(null)
    val follows: StateFlow<UserFollowResponse?> = _follows.asStateFlow()

    private val _users = MutableStateFlow<List<SearchUser>>(emptyList())
    val users: StateFlow<List<SearchUser>> = _users.asStateFlow()

    fun fetch(userId: Long, type: UserFollowType) {
        viewModelScope.launch {
            val result = when (type) {
                UserFollowType.FOLLOWS -> AccountApi.userFollows(userId)
                UserFollowType.FOLLOWEDS -> AccountApi.userFolloweds(userId)
            }.getOrNull()
            _follows.value = result
            _users.value = when (type) {
                UserFollowType.FOLLOWS -> result?.follows.orEmpty()
                UserFollowType.FOLLOWEDS -> result?.followeds.orEmpty()
            }
        }
    }

    fun clear() {
        _follows.value = null
        _users.value = emptyList()
    }
}
