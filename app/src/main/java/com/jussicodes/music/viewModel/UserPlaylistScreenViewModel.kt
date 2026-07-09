package com.jussicodes.music.viewModel

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jussicodes.music.utils.dataStore
import com.rcmiku.ncmapi.api.account.AccountApi
import com.rcmiku.ncmapi.api.account.UserPlaylistType
import com.rcmiku.ncmapi.model.UserPlaylistResponse
import com.rcmiku.ncmapi.utils.json
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import javax.inject.Inject

@HiltViewModel
class UserPlaylistScreenViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context
) :
    ViewModel() {
    private val userId = savedStateHandle.get<Long>("userId")
    private val type = savedStateHandle.get<String>("type")
    val userPlaylistType: UserPlaylistType? =
        type?.let { UserPlaylistType.entries.find { it.type == type } }

    private val _playlist =
        MutableStateFlow<UserPlaylistResponse?>(null)
    val playlist: StateFlow<UserPlaylistResponse?> =
        _playlist.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        viewModelScope.launch {
            val currentUserId = userId ?: return@launch
            val currentType = userPlaylistType ?: return@launch
            loadCachedPlaylist(currentUserId, currentType)
            refreshPlaylist(currentUserId, currentType)
        }
    }

    private suspend fun loadCachedPlaylist(userId: Long, type: UserPlaylistType) {
        val cache = context.dataStore.data.first()[cacheKey(userId, type)]
        val cachedPlaylist = runCatching {
            cache?.takeIf { it.isNotBlank() }?.let { json.decodeFromString<UserPlaylistResponse>(it) }
        }.getOrNull()
        if (cachedPlaylist != null) {
            _playlist.value = cachedPlaylist
        }
    }

    private suspend fun refreshPlaylist(userId: Long, type: UserPlaylistType) {
        _isLoading.value = true
        AccountApi.userPlaylist(
            userId = userId,
            userPlaylistType = type
        ).getOrNull()?.let { response ->
            _playlist.value = response
            context.dataStore.edit { prefs ->
                prefs[cacheKey(userId, type)] = json.encodeToString(response)
            }
        }
        _isLoading.value = false
    }

    private fun cacheKey(userId: Long, type: UserPlaylistType) =
        stringPreferencesKey("userPlaylistCache_${userId}_${type.type}")
}
