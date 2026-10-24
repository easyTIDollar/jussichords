package com.jussicodes.music.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.jussicodes.music.LocalPlayerController
import com.jussicodes.music.LocalPlayerState
import com.jussicodes.music.R
import com.jussicodes.music.constants.audioSourceKey
import com.jussicodes.music.constants.directSourceKey
import com.jussicodes.music.playback.ListenTogetherSession
import com.jussicodes.music.constants.audioEffectEightDEnabledKey
import com.jussicodes.music.constants.audioEffectIntensityKey
import com.jussicodes.music.constants.audioEffectModeKey
import com.jussicodes.music.constants.audioEffectReverbEnabledKey
import com.jussicodes.music.constants.audioEffectSpeedKey
import com.jussicodes.music.playback.audio.AudioEffectMode
import androidx.compose.material.icons.outlined.Dns
import com.jussicodes.music.ui.icons.GraphicEq
import com.jussicodes.music.ui.icons.Headphones
import com.jussicodes.music.ui.icons.Timelapse
import com.jussicodes.music.ui.icons.Timer
import com.jussicodes.music.utils.rememberEnumPreference
import com.jussicodes.music.utils.rememberPreference
import com.jussicodes.music.ui.components.AudioSourceSelector
import com.jussicodes.music.ui.components.audioSourceOptions
import com.jussicodes.music.lx.AudioSourceSwitcher
import com.rcmiku.ncmapi.api.AudioSources
import com.rcmiku.ncmapi.api.LocalSources
import com.rcmiku.ncmapi.model.Song
import kotlin.time.DurationUnit
import kotlin.time.toDuration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerMenuBottomSheet(
    currentSong: Song? = null,
    openBottomSheet: Boolean,
    onDismiss: () -> Unit,
    // 一起听面板的开关由调用方（Player.kt）持有：歌名行爱心左侧的成员小组件点了要
    // 直接开一起听面板，而不是先开菜单再让用户点一次「一起听」。
    listenTogetherOpen: Boolean = false,
    onListenTogetherOpenChange: (Boolean) -> Unit = {},
    // 横屏（矮屏）用对话框承载菜单：底部弹层在 411dp 高度里几乎占满整屏，
    // 改成居中对话框 + 左右两栏，既不遮歌词也不需要长距离滚动。
    landscape: Boolean = false,
) {
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var timePicker by rememberSaveable { mutableStateOf(false) }
    val playerState = LocalPlayerState.current
    val mediaController = LocalPlayerController.current.controller
    val isSleepTimerSet = playerState?.isSleepTimerSet == true
    val shuffleMode = playerState?.shuffleModeEnabled == true
    val repeatMode = playerState?.repeatMode ?: 0
    // 随机 / 循环合并成一个「播放模式」，四档循环切换。
    val playbackMode = currentPlaybackMode(shuffleMode, repeatMode)
    var audioEffectMode by rememberEnumPreference(audioEffectModeKey, AudioEffectMode.OFF)
    var eightDEnabledPreference by rememberPreference(audioEffectEightDEnabledKey, audioEffectMode == AudioEffectMode.EIGHT_D)
    var reverbEnabledPreference by rememberPreference(audioEffectReverbEnabledKey, audioEffectMode == AudioEffectMode.REVERB)
    var audioEffectIntensity by rememberPreference(audioEffectIntensityKey, 0.5f)
    var audioEffectSpeed by rememberPreference(audioEffectSpeedKey, 0.5f)
    var showAudioEffectPage by rememberSaveable { mutableStateOf(false) }
    val eightDEnabled = eightDEnabledPreference || audioEffectMode == AudioEffectMode.EIGHT_D
    val reverbEnabled = reverbEnabledPreference || audioEffectMode == AudioEffectMode.REVERB
    val audioEffectModeText = when {
        eightDEnabled && reverbEnabled -> "双耳空间 + 混响"
        eightDEnabled -> "双耳空间"
        reverbEnabled -> "混响"
        else -> "关闭"
    }
    val playbackModeText = when (playbackMode) {
        PlaybackModeState.SEQUENTIAL -> stringResource(R.string.playback_mode_sequential)
        PlaybackModeState.REPEAT_ALL -> stringResource(R.string.playback_mode_repeat_all)
        PlaybackModeState.REPEAT_ONE -> stringResource(R.string.playback_mode_repeat_one)
        PlaybackModeState.SHUFFLE -> stringResource(R.string.shuffle_mode)
    }
    val remainingTimeText = playerState?.remainingTime?.toDuration(DurationUnit.SECONDS)?.toComponents { hours, minutes, seconds, _ ->
        if (hours > 0) {
            "%02dh:%02dm:%02ds".format(hours, minutes, seconds)
        } else {
            "%02dm:%02ds".format(minutes, seconds)
        }
    }
    // 随机与循环合并成一个「播放模式」：点一下在
    // 顺序 → 列表循环 → 单曲循环 → 随机 之间循环切换。
    // 随机与循环互斥：切到随机时关掉循环，切到循环时关掉随机。
    val cyclePlaybackMode: () -> Unit = {
        when (nextPlaybackMode(playbackMode)) {
            PlaybackModeState.SEQUENTIAL -> {
                mediaController?.shuffleModeEnabled = false
                mediaController?.repeatMode = androidx.media3.common.Player.REPEAT_MODE_OFF
            }

            PlaybackModeState.REPEAT_ALL -> {
                mediaController?.shuffleModeEnabled = false
                mediaController?.repeatMode = androidx.media3.common.Player.REPEAT_MODE_ALL
            }

            PlaybackModeState.REPEAT_ONE -> {
                mediaController?.shuffleModeEnabled = false
                mediaController?.repeatMode = androidx.media3.common.Player.REPEAT_MODE_ONE
            }

            PlaybackModeState.SHUFFLE -> {
                mediaController?.shuffleModeEnabled = true
                mediaController?.repeatMode = androidx.media3.common.Player.REPEAT_MODE_OFF
            }
        }
    }
    val context = LocalContext.current
    var cancelSleepTimer by rememberSaveable { mutableStateOf(false) }
    var openSongListBottomSheet by rememberSaveable { mutableStateOf(false) }
    var openShareSheet by rememberSaveable { mutableStateOf(false) }
    // 一起听面板开关：调用方（Player.kt 的成员小组件）也能开，故走外部受控 + 本地兜底。
    // 本地兜底保留是为了菜单里那一行「一起听」仍能独立打开（Player 未接管时）。
    var openListenTogetherSheet by rememberSaveable { mutableStateOf(listenTogetherOpen) }
    LaunchedEffect(listenTogetherOpen) {
        if (listenTogetherOpen) openListenTogetherSheet = true
    }
    val setListenTogetherOpen: (Boolean) -> Unit = { value ->
        openListenTogetherSheet = value
        onListenTogetherOpenChange(value)
    }

    // 一起听会话状态（用于菜单卡右侧显示进房状态）
    val listenTogetherState by ListenTogetherSession.state.collectAsState()

    // Global audio-source picker state (no per-song override: a pick applies to
    // the whole app until the user changes it again).
    var audioSource by rememberPreference(audioSourceKey, AudioSources.DEFAULT)
    var directSource by rememberPreference(directSourceKey, LocalSources.AUTO)
    var showSourcePicker by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(directSource) {
        com.rcmiku.ncmapi.api.player.PlayerApi.DirectSourceSettings.SOURCE = directSource
    }
    val sourceLabel = audioSourceOptions.firstOrNull { it.value == audioSource }?.label ?: audioSource

    LaunchedEffect(openBottomSheet) {
        if (openBottomSheet) {
            if (!landscape) bottomSheetState.show()
        } else {
            if (!landscape) bottomSheetState.hide()
        }
    }

    // 菜单主体：竖屏走底部弹层的单栏列表，横屏走对话框的双栏布局。
    val menuContent: @Composable () -> Unit = {
        if (showAudioEffectPage) {
            AudioEffectPage(
                eightDEnabled = eightDEnabled,
                onEightDEnabledChange = {
                    eightDEnabledPreference = it
                    audioEffectMode = AudioEffectMode.OFF
                },
                reverbEnabled = reverbEnabled,
                onReverbEnabledChange = {
                    reverbEnabledPreference = it
                    audioEffectMode = AudioEffectMode.OFF
                },
                intensity = audioEffectIntensity,
                onIntensityChange = { audioEffectIntensity = it },
                speed = audioEffectSpeed,
                onSpeedChange = { audioEffectSpeed = it },
                onBack = { showAudioEffectPage = false }
            )
        } else {
            if (landscape) {
                // 横屏：左栏「播放 + 音频」，右栏「操作」，一屏看全，不用上下滚。
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        MenuSectionLabel("播放")
                        PlaybackMenuSection(
                            listenTogetherValue = listenTogetherState.room?.let { "${it.members.size} 人" } ?: "未进房",
                            onListenTogetherClick = {
                                setListenTogetherOpen(true)
                                onDismiss()
                            },
                            playbackModeText = playbackModeText,
                            playbackModeIcon = playbackModeIcon(playbackMode),
                            playbackModeActive = playbackMode != PlaybackModeState.SEQUENTIAL,
                            onPlaybackModeClick = cyclePlaybackMode,
                            isSleepTimerSet = isSleepTimerSet,
                            remainingTimeText = remainingTimeText,
                            onSleepTimerClick = {
                                if (isSleepTimerSet) {
                                    cancelSleepTimer = true
                                } else {
                                    timePicker = true
                                }
                            }
                        )
                        MenuSectionLabel("音频")
                        AudioMenuSection(
                            audioEffectModeText = audioEffectModeText,
                            audioEffectActive = eightDEnabled || reverbEnabled,
                            onAudioEffectClick = { showAudioEffectPage = true },
                            sourceLabel = sourceLabel,
                            onSourceClick = {
                                showSourcePicker = true
                                onDismiss()
                            }
                        )
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        MenuSectionLabel("操作")
                        ActionMenuSection(
                            onAddToSongList = {
                                openSongListBottomSheet = true
                                onDismiss()
                            },
                            onShare = {
                                openShareSheet = true
                                onDismiss()
                            }
                        )
                    }
                }
            } else {
                LazyColumn(
                    Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Column(Modifier.fillMaxWidth()) {
                            MenuSectionLabel("播放")
                            Spacer(Modifier.height(8.dp))
                            PlaybackMenuSection(
                                listenTogetherValue = listenTogetherState.room?.let { "${it.members.size} 人" } ?: "未进房",
                                onListenTogetherClick = {
                                    setListenTogetherOpen(true)
                                    onDismiss()
                                },
                                playbackModeText = playbackModeText,
                                playbackModeIcon = playbackModeIcon(playbackMode),
                                playbackModeActive = playbackMode != PlaybackModeState.SEQUENTIAL,
                                onPlaybackModeClick = cyclePlaybackMode,
                                isSleepTimerSet = isSleepTimerSet,
                                remainingTimeText = remainingTimeText,
                                onSleepTimerClick = {
                                    if (isSleepTimerSet) {
                                        cancelSleepTimer = true
                                    } else {
                                        timePicker = true
                                    }
                                }
                            )
                        }
                    }

                    item {
                        Column(Modifier.fillMaxWidth()) {
                            MenuSectionLabel("音频")
                            Spacer(Modifier.height(8.dp))
                            AudioMenuSection(
                                audioEffectModeText = audioEffectModeText,
                                audioEffectActive = eightDEnabled || reverbEnabled,
                                onAudioEffectClick = { showAudioEffectPage = true },
                                sourceLabel = sourceLabel,
                                onSourceClick = {
                                    showSourcePicker = true
                                    onDismiss()
                                }
                            )
                        }
                    }

                    item {
                        Column(Modifier.fillMaxWidth()) {
                            MenuSectionLabel("操作")
                            Spacer(Modifier.height(8.dp))
                            ActionMenuSection(
                                onAddToSongList = {
                                    openSongListBottomSheet = true
                                    onDismiss()
                                },
                                onShare = {
                                    openShareSheet = true
                                    onDismiss()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (openBottomSheet) {
        if (landscape) {
            // 注意：同包的 [Dialog]（AlertDialog 封装）与 window.Dialog 同名，
            // 这里显式用全限定名，避免解析到同包那个。
            androidx.compose.ui.window.Dialog(
                onDismissRequest = onDismiss,
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                ScaledDialogContent {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .width(640.dp)
                            .heightIn(max = 400.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            menuContent()
                        }
                    }
                }
            }
        } else {
            ResponsiveModalBottomSheet(
                onDismissRequest = onDismiss,
                sheetState = bottomSheetState,
            ) {
                menuContent()
            }
        }
    }

    // 定时播放 / 取消定时 放在最外层：无论竖屏弹层还是横屏对话框，
    // 这两个弹窗都是独立窗口，嵌在弹层里会互相遮挡。
    if (timePicker) {
        SleepTimerDialog(
            onDismiss = {
                timePicker = false
            },
            onTimeSet = {
                playerState?.startTimer(it)
                timePicker = false
            }
        )
    }

    if (cancelSleepTimer) {
        Dialog(
            onConfirmation = {
                playerState?.cancelTimer()
                cancelSleepTimer = false
            },
            onDismissRequest = {
                cancelSleepTimer = false
            },
            dialogTitle = stringResource(R.string.sleep_timer_cancel),
        )
    }

    if (showSourcePicker) {
        LocalSourcePickerSheet(
            currentSource = audioSource,
            onDismiss = { showSourcePicker = false },
            onApply = { selected ->
                audioSource = selected
                showSourcePicker = false
                // 换源立即生效并重解当前歌曲：与设置页 reloadCurrentSong 同一套逻辑
                // （cacheKey 含音源代号，不会复用旧直链）。
                AudioSourceSwitcher.apply(selected, directSource)
                Toast.makeText(
                    context,
                    "音源：${audioSourceOptions.firstOrNull { it.value == selected }?.label ?: selected}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
    }

    SongListBottomSheet(song = currentSong, onDismiss = {
        openSongListBottomSheet = false
    }, openBottomSheet = openSongListBottomSheet)

    currentSong?.let { song ->
        ShareSheet(
            payload = SharePayload.SongShare(song),
            openBottomSheet = openShareSheet,
            onDismiss = { openShareSheet = false }
        )
    }

    ListenTogetherSheet(
        openBottomSheet = openListenTogetherSheet,
        onDismiss = { setListenTogetherOpen(false) }
    )
}

/**
 * 「播放」分组：一起听 / 播放模式（随机+循环合并）/ 定时播放。
 *
 * 竖屏是纵向一栏，横屏是左栏的一半 —— 两种形态复用同一份行定义。
 */
@Composable
private fun PlaybackMenuSection(
    listenTogetherValue: String,
    onListenTogetherClick: () -> Unit,
    playbackModeText: String,
    playbackModeIcon: ImageVector,
    playbackModeActive: Boolean,
    onPlaybackModeClick: () -> Unit,
    isSleepTimerSet: Boolean,
    remainingTimeText: String?,
    onSleepTimerClick: () -> Unit,
) {
    MenuGroupContainer {
        MenuRow(
            icon = Headphones,
            title = "一起听",
            value = listenTogetherValue,
            onClick = onListenTogetherClick
        )
        MenuDivider()
        // 随机与循环合并成一个「播放模式」行：点一下就在
        // 顺序 → 列表循环 → 单曲循环 → 随机 之间循环切换。
        MenuRow(
            icon = playbackModeIcon,
            title = stringResource(R.string.playback_mode),
            value = playbackModeText,
            iconAlpha = if (playbackModeActive) 1f else 0.4f,
            onClick = onPlaybackModeClick
        )
        MenuDivider()
        MenuRow(
            icon = if (isSleepTimerSet) Timelapse else Timer,
            title = stringResource(if (isSleepTimerSet) R.string.remaining_time else R.string.sleep_timer),
            value = if (isSleepTimerSet) remainingTimeText else null,
            iconAlpha = if (isSleepTimerSet) 1f else 0.4f,
            onClick = onSleepTimerClick
        )
    }
}

/** 「音频」分组：音效 / 切换音源。 */
@Composable
private fun AudioMenuSection(
    audioEffectModeText: String,
    audioEffectActive: Boolean,
    onAudioEffectClick: () -> Unit,
    sourceLabel: String,
    onSourceClick: () -> Unit,
) {
    MenuGroupContainer {
        MenuRow(
            icon = GraphicEq,
            title = "音效",
            value = audioEffectModeText,
            iconAlpha = if (audioEffectActive) 1f else 0.4f,
            onClick = onAudioEffectClick
        )
        MenuDivider()
        MenuRow(
            icon = Icons.Outlined.Dns,
            title = "切换音源",
            value = sourceLabel,
            iconAlpha = 1f,
            onClick = onSourceClick
        )
    }
}

/** 「操作」分组：添加到歌单 / 分享。 */
@Composable
private fun ActionMenuSection(
    onAddToSongList: () -> Unit,
    onShare: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MenuActionButton(
            text = stringResource(R.string.add_to_songList),
            onClick = onAddToSongList,
            modifier = Modifier.weight(1f)
        )
        MenuActionButton(
            text = stringResource(R.string.share),
            onClick = onShare,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Picker for the global local source (关闭 / 单个源). Selecting a
 * value persists it to [audioSourceKey]; the whole app follows it until the
 * user picks something else — no per-song override.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocalSourcePickerSheet(
    currentSource: String,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit,
) {
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    LaunchedEffect(Unit) { bottomSheetState.show() }

    ResponsiveModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = bottomSheetState,
        // 音源选择器是普通 Column，横屏面板高度封顶后需要滚动。
        landscapeScrollable = true,
    ) {
        Column(modifier = Modifier.padding(vertical = 12.dp)) {
            Text(
                text = "三种音源只生效一个",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            Text(
                text = "选择音源",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            // 与设置页同一套小卡片选择器（AudioSourceSelector），命名/排序同源
            AudioSourceSelector(
                currentSource = currentSource,
                onSourceSelected = { onApply(it) },
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}

/**
 * 音效设置页（双耳空间 / 混响 / 强度 / 空间旋转速度）。
 *
 * 播放页「更多」菜单与设置页共用这一份实现：两处都是 [GraphicEq] 均衡器图标，
 * 打开的是同一个页面、同一套偏好项。
 */
@Composable
fun AudioEffectPage(
    eightDEnabled: Boolean,
    onEightDEnabledChange: (Boolean) -> Unit,
    reverbEnabled: Boolean,
    onReverbEnabledChange: (Boolean) -> Unit,
    intensity: Float,
    onIntensityChange: (Float) -> Unit,
    speed: Float,
    onSpeedChange: (Float) -> Unit,
    onBack: () -> Unit,
) {
    LazyColumn(
        Modifier.padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        item {
            PlayerMenuActionCard(
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = 8.dp,
                    bottomEnd = 8.dp
                ),
                icon = Icons.AutoMirrored.Outlined.ArrowBack,
                title = "音效设置",
                value = audioEffectLabel(eightDEnabled, reverbEnabled),
                onClick = onBack
            )
        }

        item {
            AudioEffectOption(
                title = "双耳空间",
                selected = eightDEnabled,
                onClick = { onEightDEnabledChange(!eightDEnabled) }
            )
        }

        item {
            AudioEffectOption(
                title = "混响",
                selected = reverbEnabled,
                onClick = { onReverbEnabledChange(!reverbEnabled) }
            )
        }

        item {
            AudioEffectSlider(
                title = "音效强度",
                value = intensity,
                enabled = eightDEnabled || reverbEnabled,
                onValueChange = onIntensityChange
            )
        }

        item {
            AudioEffectSlider(
                title = "空间旋转速度",
                value = speed,
                enabled = eightDEnabled,
                onValueChange = onSpeedChange
            )
        }

        item {
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun AudioEffectOption(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = selected, onCheckedChange = null)
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@Composable
private fun AudioEffectSlider(
    title: String,
    value: Float,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .alpha(if (enabled) 1f else 0.45f)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${(value * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Slider(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                valueRange = 0f..1f
            )
        }
    }
}

private fun audioEffectLabel(eightDEnabled: Boolean, reverbEnabled: Boolean): String = when {
    eightDEnabled && reverbEnabled -> "双耳空间 + 混响"
    eightDEnabled -> "双耳空间"
    reverbEnabled -> "混响"
    else -> "关闭"
}

@Composable
private fun PlayerMenuActionCard(
    shape: RoundedCornerShape,
    icon: ImageVector,
    title: String,
    value: String? = null,
    iconAlpha: Float = 1f,
    onClick: () -> Unit,
) {
    Card(
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .alpha(iconAlpha)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.weight(1f))
            value?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(end = 12.dp)
                )
            }
        }
    }
}

/**
 * A small, uppercase-feel section label that heads a grouped container.
 * Lower visual weight than a card title so the grouping reads as structure,
 * not as another item.
 */
@Composable
private fun MenuSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 6.dp)
    )
}

/**
 * A rounded container that holds a set of sibling [MenuRow]s. The container
 * paints a single primaryContainer surface; each row is a transparent row and
 * the dividers between them are thin, low-contrast lines. This collapses the
 * old "one big card per item" look into one compact, levelled block.
 */
@Composable
private fun MenuGroupContainer(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.primaryContainer,
                RoundedCornerShape(14.dp)
            )
            .padding(vertical = 4.dp)
    ) {
        content()
    }
}

/**
 * A uniform-height, low-decoration list row: icon + title on the left, the
 * (de-emphasized) status value pushed to the right. Sits inside a
 * [MenuGroupContainer]; the row itself carries no background so the container
 * surface is what gives it shape.
 */
@Composable
private fun MenuRow(
    icon: ImageVector,
    title: String,
    value: String?,
    iconAlpha: Float = 1f,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier
                .padding(end = 12.dp)
                .alpha(iconAlpha)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Spacer(Modifier.weight(1f))
        value?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * A hairline divider between two [MenuRow]s inside a [MenuGroupContainer].
 * Indented past the icon and kept faint so it reads as grouping, not as a
 * card border.
 */
@Composable
private fun MenuDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 52.dp, end = 14.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.08f))
    )
}

/**
 * A compact, de-emphasized action button used for the 操作 section
 * (添加到歌单 / 分享). Rendered side-by-side via RowScope weight at the
 * call site; visually lighter than the setting rows above it.
 */
@Composable
private fun MenuActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(44.dp)
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}
