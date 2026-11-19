package com.jussicodes.music.lx

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jussicodes.music.utils.dataStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private val Context.lxDataStore: DataStore<Preferences> by preferencesDataStore(name = "lx")

/** 仅管理 LX 专用键；迁移与读写共用锁，失败后本次进程兼容旧文件，下次启动重试。 */
internal object LxPreferences {
    val pluginsKey = stringPreferencesKey("lxPlugins")
    val seedKey = stringPreferencesKey("lxSeedState")
    private val migratedKey = booleanPreferencesKey("lx_migrated_v1")
    private val keys = listOf(pluginsKey, seedKey)
    private val lock = Mutex()
    private var attempted = false
    private var migrated = false
    private val legacyPreferred = mutableSetOf<Preferences.Key<String>>()

    suspend fun read(context: Context, key: Preferences.Key<String>): String? =
        withContext(Dispatchers.IO) {
            lock.withLock {
                migrate(context)
                if (migrated) return@withLock context.lxDataStore.data.first()[key]
                if (key in legacyPreferred) {
                    // 上次复制或同步失败时，新副本可能过期；优先使用仍在旧文件的原值。
                    try {
                        context.dataStore.data.first()[key]?.let { return@withLock it }
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                    }
                }
                // 新文件不存在该键或暂时不可读时，迁移未完成的用户仍可使用旧数据。
                val fresh = try {
                    context.lxDataStore.data.first()
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    return@withLock context.dataStore.data.first()[key] ?: throw error
                }
                fresh[key] ?: context.dataStore.data.first()[key]
            }
        }

    suspend fun write(context: Context, key: Preferences.Key<String>, value: String) {
        withContext(Dispatchers.IO) {
            lock.withLock {
                migrate(context)
                if (migrated) {
                    context.lxDataStore.edit { it[key] = value }
                } else {
                    // 未迁完先保证旧文件持久化，再同步新文件；下次迁移仍以旧值为准。
                    context.dataStore.edit { it[key] = value }
                    legacyPreferred.add(key)
                    try {
                        context.lxDataStore.edit { it[key] = value }
                        legacyPreferred.remove(key)
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        Log.w("LxPreferences", "新文件写入失败，继续使用已落盘的旧数据", error)
                    }
                }
            }
        }
    }

    /** 调用方已持锁；收尾不可取消，避免删旧值后因协程取消而跳过标记或补偿。 */
    private suspend fun migrate(context: Context) {
        if (attempted) return
        withContext(NonCancellable) {
            legacyPreferred.addAll(keys)
            var oldValues: Map<Preferences.Key<String>, String?>? = null
            var removalAttempted = false
            try {
                val fresh = context.lxDataStore.data.first()
                if (fresh[migratedKey] == true) {
                    migrated = true
                    legacyPreferred.clear()
                } else {
                    val legacy = context.dataStore.data.first()
                    val values = keys.associateWith { legacy[it] }
                    oldValues = values
                    // 旧值优先；旧值缺失时保留新值，兼容上次删旧后进程退出的情况。
                    context.lxDataStore.edit { target ->
                        values.forEach { (key, value) ->
                            if (value != null) target[key] = value
                        }
                    }
                    val verified = context.lxDataStore.data.first()
                    check(keys.all { verified[it] == (values[it] ?: fresh[it]) }) {
                        "LX 数据迁移读回校验失败"
                    }
                    legacyPreferred.clear()
                    removalAttempted = true
                    context.dataStore.edit { source ->
                        check(keys.all { source[it] == values[it] }) { "LX 旧数据在迁移期间发生变化" }
                        keys.forEach { source.remove(it) }
                    }
                    context.lxDataStore.edit { it[migratedKey] = true }
                    migrated = true
                }
            } catch (error: Exception) {
                legacyPreferred.addAll(keys)
                // 标记失败时恢复刚删除的旧值；已校验的新副本始终保留。
                if (removalAttempted) {
                    try {
                        context.dataStore.edit { source ->
                            oldValues?.forEach { (key, value) ->
                                if (value != null && source[key] == null) source[key] = value
                            }
                        }
                    } catch (restoreError: Exception) {
                        Log.e("LxPreferences", "旧数据恢复失败，保留已校验的新副本供读取和下次迁移", restoreError)
                    }
                }
                Log.w("LxPreferences", "迁移未完成，启用兼容读写并在下次启动重试", error)
            }
            attempted = true
        }
    }
}
