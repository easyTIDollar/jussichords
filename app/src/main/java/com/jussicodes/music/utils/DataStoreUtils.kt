package com.jussicodes.music.utils

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.properties.ReadOnlyProperty

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

private val snapshotScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
private val snapshots = ConcurrentHashMap<DataStore<Preferences>, StateFlow<Preferences?>>()

private fun DataStore<Preferences>.snapshot(): StateFlow<Preferences?> =
    snapshots.computeIfAbsent(this) { store ->
        store.data.retryWhen { error, _ ->
            if (error is IOException) {
                delay(1000)
                true
            } else {
                false
            }
        }.stateIn(snapshotScope, SharingStarted.Eagerly, null)
    }

/** 启动异步订阅；同步 getter 在首份快照到达前返回空值，由调用方使用默认设置。 */
fun DataStore<Preferences>.preloadPreferences() {
    snapshot()
}

operator fun <T> DataStore<Preferences>.get(key: Preferences.Key<T>): T? =
    snapshot().value?.get(key)

fun <T> DataStore<Preferences>.get(key: Preferences.Key<T>, defaultValue: T): T =
    get(key) ?: defaultValue

fun <T> preference(
    context: Context,
    key: Preferences.Key<T>,
    defaultValue: T,
) = ReadOnlyProperty<Any?, T> { _, _ -> context.dataStore[key] ?: defaultValue }

inline fun <reified T : Enum<T>> enumPreference(
    context: Context,
    key: Preferences.Key<String>,
    defaultValue: T,
) = ReadOnlyProperty<Any?, T> { _, _ -> context.dataStore[key].toEnum(defaultValue) }

@Composable
fun <T> rememberPreference(
    key: Preferences.Key<T>,
    defaultValue: T,
): MutableState<T> {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val state = remember {
        context.dataStore.data
            .map { it[key] ?: defaultValue }
            .distinctUntilChanged()
    }.collectAsState(defaultValue)

    return remember {
        object : MutableState<T> {
            override var value: T
                get() = state.value
                set(value) {
                    coroutineScope.launch {
                        context.dataStore.edit {
                            it[key] = value
                        }
                    }
                }

            override fun component1() = value
            override fun component2(): (T) -> Unit = { value = it }
        }
    }
}

@Composable
fun <T> rememberNullablePreference(
    key: Preferences.Key<T>,
): State<T?> {
    val context = LocalContext.current

    val flow = remember {
        context.dataStore.data
            .map { it[key] }
            .distinctUntilChanged()
    }
    return flow.collectAsState(initial = null)
}

@Composable
inline fun <reified T : Enum<T>> rememberEnumPreference(
    key: Preferences.Key<String>,
    defaultValue: T,
): MutableState<T> {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val state = remember {
        context.dataStore.data
            .map { it[key].toEnum(defaultValue = defaultValue) }
            .distinctUntilChanged()
    }.collectAsState(defaultValue)

    return remember {
        object : MutableState<T> {
            override var value: T
                get() = state.value
                set(value) {
                    coroutineScope.launch {
                        context.dataStore.edit {
                            it[key] = value.name
                        }
                    }
                }

            override fun component1() = value
            override fun component2(): (T) -> Unit = { value = it }
        }
    }
}


inline fun <reified T : Enum<T>> String?.toEnum(defaultValue: T): T =
    if (this == null) defaultValue
    else try {
        enumValueOf(this)
    } catch (e: IllegalArgumentException) {
        defaultValue
    }
