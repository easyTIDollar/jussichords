package com.jussicodes.music.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.jussicodes.music.constants.activeAccountIdKey
import com.jussicodes.music.constants.ncmCookieKey
import com.jussicodes.music.constants.savedAccountsKey
import com.jussicodes.music.utils.dataStore
import com.rcmiku.ncmapi.utils.CookieProvider
import com.rcmiku.ncmapi.utils.json
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

@Serializable
data class SavedAccount(
    val userId: Long = 0L,
    val nickname: String = "",
    val avatarUrl: String = "",
    val cookie: Map<String, String> = emptyMap(),
    val addedAt: Long = 0L,
)

object AccountStore {
    fun accounts(context: Context): Flow<List<SavedAccount>> =
        context.applicationContext.dataStore.data
            .map { it.savedAccounts() }
            .distinctUntilChanged()

    fun activeAccount(context: Context): Flow<SavedAccount?> =
        context.applicationContext.dataStore.data.map { preferences ->
            if (preferences[ncmCookieKey].isNullOrBlank()) {
                null
            } else {
                preferences.savedAccounts().firstOrNull {
                    it.userId == preferences[activeAccountIdKey]
                }
            }
        }.distinctUntilChanged()

    suspend fun upsert(context: Context, account: SavedAccount) {
        context.applicationContext.dataStore.edit { preferences ->
            preferences[savedAccountsKey] = json.encodeToString(
                preferences.savedAccounts().upserted(account)
            )
        }
    }

    suspend fun remove(context: Context, userId: Long) {
        context.applicationContext.dataStore.edit { preferences ->
            preferences[savedAccountsKey] = json.encodeToString(
                preferences.savedAccounts().filterNot { it.userId == userId }
            )
            if (preferences[activeAccountIdKey] == userId) {
                preferences.remove(activeAccountIdKey)
                preferences[ncmCookieKey] = ""
            }
        }
    }

    suspend fun setActive(context: Context, account: SavedAccount) {
        context.applicationContext.dataStore.edit { preferences ->
            preferences[activeAccountIdKey] = account.userId
            preferences[ncmCookieKey] = json.encodeToString(account.cookie)
        }
        CookieProvider.init(account.cookie)
    }

    suspend fun ensureMigrated(
        context: Context,
        cookie: Map<String, String>,
        userId: Long,
        nickname: String,
        avatarUrl: String,
    ) {
        if (cookie.isEmpty() || userId == 0L) return
        context.applicationContext.dataStore.edit { preferences ->
            // 请求资料期间可能已切号或退出，只迁移仍在使用的凭证。
            val currentCookie = runCatching {
                json.decodeFromString<Map<String, String>>(preferences[ncmCookieKey].orEmpty())
            }.getOrNull()
            if (currentCookie != cookie) return@edit

            val accounts = preferences.savedAccounts()
            val saved = accounts.firstOrNull { it.userId == userId }
            if (saved?.cookie != cookie) {
                preferences[savedAccountsKey] = json.encodeToString(
                    accounts.upserted(
                        SavedAccount(
                            userId = userId,
                            nickname = nickname,
                            avatarUrl = avatarUrl,
                            cookie = cookie,
                            addedAt = saved?.addedAt ?: System.currentTimeMillis(),
                        )
                    )
                )
            }
            preferences[activeAccountIdKey] = userId
        }
    }

    private fun Preferences.savedAccounts(): List<SavedAccount> = runCatching {
        json.decodeFromString<List<SavedAccount>>(this[savedAccountsKey].orEmpty())
    }.getOrDefault(emptyList())

    private fun List<SavedAccount>.upserted(account: SavedAccount): List<SavedAccount> =
        if (any { it.userId == account.userId }) {
            map { if (it.userId == account.userId) account else it }
        } else {
            this + account
        }
}
