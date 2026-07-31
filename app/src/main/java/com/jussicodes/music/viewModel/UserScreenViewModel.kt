package com.jussicodes.music.viewModel

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rcmiku.ncmapi.api.account.AccountApi
import com.jussicodes.music.utils.AvatarUploadLimiter
import com.rcmiku.ncmapi.api.artist.ArtistApi
import com.rcmiku.ncmapi.model.Playlist
import com.rcmiku.ncmapi.model.UserDetailResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import dagger.hilt.android.qualifiers.ApplicationContext

@HiltViewModel
class UserScreenViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val userId = savedStateHandle.get<Long>("userId") ?: 0L

    private val _userDetail = MutableStateFlow<UserDetailResponse?>(null)
    val userDetail: StateFlow<UserDetailResponse?> = _userDetail.asStateFlow()

    private val _userPlaylists = MutableStateFlow<List<Playlist>>(emptyList())
    val userPlaylists: StateFlow<List<Playlist>> = _userPlaylists.asStateFlow()

    private val _isFollowed = MutableStateFlow(false)
    val isFollowed: StateFlow<Boolean> = _isFollowed.asStateFlow()

    private val _isFollowUpdating = MutableStateFlow(false)
    val isFollowUpdating: StateFlow<Boolean> = _isFollowUpdating.asStateFlow()

    private val _isSelf = MutableStateFlow(false)
    val isSelf: StateFlow<Boolean> = _isSelf.asStateFlow()

    private val _isAvatarUploading = MutableStateFlow(false)
    val isAvatarUploading: StateFlow<Boolean> = _isAvatarUploading.asStateFlow()
    private val _avatarCacheVersion = MutableStateFlow(0L)
    val avatarCacheVersion: StateFlow<Long> = _avatarCacheVersion.asStateFlow()

    init {
        viewModelScope.launch {
            if (userId > 0) {
                val currentUserId = AccountApi.accountInfo().getOrNull()?.profile?.userId ?: 0L
                _isSelf.value = currentUserId == userId
                val detail = ArtistApi.userDetail(userId).getOrNull()
                _userDetail.value = detail
                _isFollowed.value = detail?.profile?.followed == true
                _userPlaylists.value = AccountApi.userPlaylists(userId)
                    .getOrNull()?.data?.playlist.orEmpty()
            }
        }
    }

    fun toggleFollow() {
        if (userId <= 0 || _isSelf.value || _isFollowUpdating.value) return
        val nextFollowed = !_isFollowed.value
        viewModelScope.launch {
            _isFollowUpdating.value = true
            _isFollowed.value = nextFollowed
            _userDetail.value = _userDetail.value?.let { detail ->
                val currentCount = detail.profile.followedsCount
                detail.copy(
                    profile = detail.profile.copy(
                        followed = nextFollowed,
                        followedsCount = (currentCount + if (nextFollowed) 1 else -1).coerceAtLeast(0)
                    )
                )
            }
            AccountApi.followUser(userId, nextFollowed)
            _isFollowUpdating.value = false
        }
    }

    fun uploadAvatar(file: File, onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        if (!_isSelf.value || _isAvatarUploading.value) return
        if (AvatarUploadLimiter.remaining(context) <= 0) {
            onResult(false, "本周头像上传次数已用完（每周最多 5 次）")
            return
        }
        viewModelScope.launch {
            _isAvatarUploading.value = true
            val result = AccountApi.uploadAvatar(file)
            val response = result.getOrNull()
            val success = response?.isSuccess == true
            if (success) {
                AvatarUploadLimiter.recordSuccess(context)
                _avatarCacheVersion.value = System.currentTimeMillis()
                val refreshed = ArtistApi.userDetail(userId).getOrNull()
                    ?: AccountApi.accountInfo().getOrNull()?.let { account ->
                        UserDetailResponse(profile = account.profile)
                    }
                refreshed?.let { _userDetail.value = it }
            }
            _isAvatarUploading.value = false
            onResult(success, result.exceptionOrNull()?.message ?: response?.errorMessage)
        }
    }
}
