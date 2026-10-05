package com.jussicodes.music.ui.components

import android.widget.Toast
import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
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
import androidx.compose.ui.semantics.Role
import com.jussicodes.music.LocalPlayerController
import com.jussicodes.music.LocalPlayerState
import com.jussicodes.music.R
import com.jussicodes.music.constants.MediaSessionConstants
import com.jussicodes.music.constants.localSourceKey
import com.jussicodes.music.playback.ListenTogetherSession
import com.jussicodes.music.constants.audioEffectEightDEnabledKey
import com.jussicodes.music.constants.audioEffectIntensityKey
import com.jussicodes.music.constants.audioEffectModeKey
import com.jussicodes.music.constants.audioEffectReverbEnabledKey
import com.jussicodes.music.constants.audioEffectSpeedKey
import com.jussicodes.music.playback.audio.AudioEffectMode
import com.jussicodes.music.ui.icons.Dns
import com.jussicodes.music.ui.icons.AudioLines
import com.jussicodes.music.ui.icons.ListenTogether
import com.jussicodes.music.ui.icons.Repeat
import com.jussicodes.music.ui.icons.RepeatOne
import com.jussicodes.music.ui.icons.Shuffle
import com.jussicodes.music.ui.icons.Timelapse
import com.jussicodes.music.ui.icons.Timer
import com.jussicodes.music.utils.rememberEnumPreference
import com.jussicodes.music.utils.rememberPreference
import com.jussicodes.music.ui.components.localSourceOptions
import com.rcmiku.ncmapi.model.Song
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import kotlin.time.DurationUnit
import kotlin.time.toDuration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerMenuBottomSheet(
    currentSong: Song? = null,
    openBottomSheet: Boolean,
    onDismiss: () -> Unit,
) {
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var timePicker by rememberSaveable { mutableStateOf(false) }
    val playerState = LocalPlayerState.current
    val mediaController = LocalPlayerController.current.controller
    val isSleepTimerSet = playerState?.isSleepTimerSet == true
    val shuffleMode = playerState?.shuffleModeEnabled == true
    val repeatMode = playerState?.repeatMode ?: 0
    var audioEffectMode by rememberEnumPreference(audioEffectModeKey, AudioEffectMode.OFF)
    var eightDEnabledPreference by rememberPreference(audioEffectEightDEnabledKey, audioEffectMode == AudioEffectMode.EIGHT_D)
    var reverbEnabledPreference by rememberPreference(audioEffectReverbEnabledKey, audioEffectMode == AudioEffectMode.REVERB)
    var audioEffectIntensity by rememberPreference(audioEffectIntensityKey, 0.5f)
    var audioEffectSpeed by rememberPreference(audioEffectSpeedKey, 0.5f)
    var showAudioEffectPage by rememberSaveable { mutableStateOf(false) }
    val eightDEnabled = eightDEnabledPreference || audioEffectMode == AudioEffectMode.EIGHT_D
    val reverbEnabled = reverbEnabledPreference || audioEffectMode == AudioEffectMode.REVERB
    val repeatIcon = when (repeatMode) {
        1 -> RepeatOne
        else -> Repeat
    }
    val audioEffectModeText = when {
        eightDEnabled && reverbEnabled -> "双耳空间 + 混响"
        eightDEnabled -> "双耳空间"
        reverbEnabled -> "混响"
        else -> "关闭"
    }
    val repeatModeText = when (repeatMode) {
        1 -> stringResource(R.string.playback_mode_repeat_one)
        2 -> stringResource(R.string.playback_mode_repeat_all)
        else -> stringResource(R.string.playback_mode_sequential)
    }
    val remainingTimeText = playerState?.remainingTime?.toDuration(DurationUnit.SECONDS)?.toComponents { hours, minutes, seconds, _ ->
        if (hours > 0) {
            "%02dh:%02dm:%02ds".format(hours, minutes, seconds)
        } else {
            "%02dm:%02ds".format(minutes, seconds)
        }
    }
    val context = LocalContext.current
    var cancelSleepTimer by rememberSaveable { mutableStateOf(false) }
    var openSongListBottomSheet by rememberSaveable { mutableStateOf(false) }
    var openShareSheet by rememberSaveable { mutableStateOf(false) }
    var openListenTogetherSheet by rememberSaveable { mutableStateOf(false) }

    // 一起听会话状态（用于菜单卡右侧显示进房状态）
    val listenTogetherState by ListenTogetherSession.state.collectAsState()

    // Global local-source picker state (no per-song override: a pick applies to
    // the whole app until the user changes it again).
    var localSource by rememberPreference(localSourceKey, "AUTO")
    var showSourcePicker by rememberSaveable { mutableStateOf(false) }
    val sourceLabel = localSourceOptions.firstOrNull { it.value == localSource }?.label ?: localSource

    LaunchedEffect(openBottomSheet) {
        if (openBottomSheet) {
            bottomSheetState.show()
        } else {
            bottomSheetState.hide()
        }
    }

    if (openBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = bottomSheetState,
        ) {
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
                LazyColumn(
                    Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Column(Modifier.fillMaxWidth()) {
                            MenuSectionLabel("播放")
                            Spacer(Modifier.height(8.dp))
                            MenuGroupContainer {
                                MenuRow(
                                    icon = ListenTogether,
                                    title = "一起听",
                                    value = listenTogetherState.room?.let { "${it.members.size} 人" } ?: "未进房",
                                    onClick = {
                                        openListenTogetherSheet = true
                                        onDismiss()
                                    }
                                )
                                MenuDivider()
                                MenuRow(
                                    icon = Shuffle,
                                    title = stringResource(R.string.shuffle_mode),
                                    value = stringResource(if (shuffleMode) R.string.shuffle_on else R.string.shuffle_off),
                                    iconAlpha = if (shuffleMode) 1f else 0.4f,
                                    onClick = {
                                        mediaController?.sendCustomCommand(
                                            MediaSessionConstants.CommandToggleShuffle,
                                            Bundle.EMPTY
                                        )
                                    }
                                )
                                MenuDivider()
                                MenuRow(
                                    icon = repeatIcon,
                                    title = stringResource(R.string.playback_mode),
                                    value = repeatModeText,
                                    iconAlpha = if (repeatMode == 0) 0.4f else 1f,
                                    onClick = {
                                        mediaController?.repeatMode = when (repeatMode) {
                                            0 -> 2
                                            1 -> 0
                                            2 -> 1
                                            else -> 0
                                        }
                                    }
                                )
                                MenuDivider()
                                MenuRow(
                                    icon = if (isSleepTimerSet) Timelapse else Timer,
                                    title = stringResource(if (isSleepTimerSet) R.string.remaining_time else R.string.sleep_timer),
                                    value = if (isSleepTimerSet) remainingTimeText else null,
                                    iconAlpha = if (isSleepTimerSet) 1f else 0.4f,
                                    onClick = {
                                        if (isSleepTimerSet) {
                                            cancelSleepTimer = true
                                        } else {
                                            timePicker = true
                                        }
                                    }
                                )
                            }
                        }
                    }

                    item {
                        Column(Modifier.fillMaxWidth()) {
                            MenuSectionLabel("音频")
                            Spacer(Modifier.height(8.dp))
                            MenuGroupContainer {
                                MenuRow(
                                    icon = AudioLines,
                                    title = "音效",
                                    value = audioEffectModeText,
                                    iconAlpha = if (eightDEnabled || reverbEnabled) 1f else 0.4f,
                                    onClick = { showAudioEffectPage = true }
                                )
                                MenuDivider()
                                MenuRow(
                                    icon = Dns,
                                    title = "本地音源",
                                    value = sourceLabel,
                                    iconAlpha = 1f,
                                    onClick = {
                                        showSourcePicker = true
                                        onDismiss()
                                    }
                                )
                            }
                        }
                    }

                    item {
                        Column(Modifier.fillMaxWidth()) {
                            MenuSectionLabel("操作")
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                MenuActionButton(
                                    text = stringResource(R.string.add_to_songList),
                                    onClick = {
                                        openSongListBottomSheet = true
                                        onDismiss()
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                MenuActionButton(
                                    text = stringResource(R.string.share),
                                    onClick = {
                                        openShareSheet = true
                                        onDismiss()
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }

            if (timePicker) {
                TimePickerDialog(
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
        }
    }

    if (showSourcePicker) {
        LocalSourcePickerSheet(
            currentSource = localSource,
            onDismiss = { showSourcePicker = false },
            onApply = { selected ->
                localSource = selected
                showSourcePicker = false
                Toast.makeText(
                    context,
                    "本地音源：${localSourceOptions.firstOrNull { it.value == selected }?.label ?: selected}",
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
        onDismiss = { openListenTogetherSheet = false }
    )
}

/**
 * Picker for the global local source (关闭 / 智能切换 / 单个源). Selecting a
 * value persists it to [localSourceKey]; the whole app follows it until the
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = bottomSheetState,
    ) {
        Column(
            modifier = Modifier
                .selectableGroup()
                .padding(vertical = 12.dp)
        ) {
            Text(
                text = "选择本地音源",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            localSourceOptions.forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .selectable(
                            selected = option.value == currentSource,
                            onClick = { onApply(option.value) },
                            role = Role.RadioButton,
                        )
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = option.value == currentSource,
                        onClick = null
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = option.label,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun AudioEffectPage(
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

