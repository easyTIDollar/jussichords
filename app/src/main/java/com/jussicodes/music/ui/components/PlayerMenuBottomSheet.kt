package com.jussicodes.music.ui.components

import android.content.Intent
import android.widget.Toast
import android.os.Bundle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.outlined.Share
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.jussicodes.music.constants.unblockSourceKey
import com.jussicodes.music.data.SongSourceCache
import com.jussicodes.music.extensions.withSongSource
import com.jussicodes.music.constants.audioEffectEightDEnabledKey
import com.jussicodes.music.constants.audioEffectIntensityKey
import com.jussicodes.music.constants.audioEffectModeKey
import com.jussicodes.music.constants.audioEffectReverbEnabledKey
import com.jussicodes.music.constants.audioEffectSpeedKey
import com.jussicodes.music.playback.audio.AudioEffectMode
import com.jussicodes.music.ui.icons.Dns
import com.jussicodes.music.ui.icons.AudioLines
import com.jussicodes.music.ui.icons.Repeat
import com.jussicodes.music.ui.icons.RepeatOne
import com.jussicodes.music.ui.icons.Shuffle
import com.jussicodes.music.ui.icons.SongListAdd
import com.jussicodes.music.ui.icons.Timelapse
import com.jussicodes.music.ui.icons.Timer
import com.jussicodes.music.utils.rememberEnumPreference
import com.jussicodes.music.utils.rememberPreference
import com.rcmiku.ncmapi.model.Song
import androidx.media3.common.C
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import kotlinx.coroutines.launch
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

    // Per-song unblock-source picker state.
    val scope = rememberCoroutineScope()
    var globalSource by rememberPreference(unblockSourceKey, "AUTO")
    var showSourcePicker by rememberSaveable { mutableStateOf(false) }
    var perSongSourceOverride by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(openBottomSheet, currentSong?.id) {
        if (openBottomSheet) {
            currentSong?.id?.let { perSongSourceOverride = SongSourceCache.getForSong(it) }
        } else {
            perSongSourceOverride = null
        }
    }

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
                            icon = if (isSleepTimerSet) Timelapse else Timer,
                            title = stringResource(if (isSleepTimerSet) R.string.remaining_time else R.string.sleep_timer),
                            value = if (isSleepTimerSet) remainingTimeText else null,
                            onClick = {
                                if (isSleepTimerSet) {
                                    cancelSleepTimer = true
                                } else {
                                    timePicker = true
                                }
                            }
                        )
                    }

                    item {
                        PlayerMenuActionCard(
                            shape = RoundedCornerShape(8.dp),
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
                    }

                    item {
                        PlayerMenuActionCard(
                            shape = RoundedCornerShape(8.dp),
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
                    }

                    item {
                        PlayerMenuActionCard(
                            shape = RoundedCornerShape(8.dp),
                            icon = AudioLines,
                            title = "音效",
                            value = audioEffectModeText,
                            iconAlpha = if (eightDEnabled || reverbEnabled) 1f else 0.4f,
                            onClick = { showAudioEffectPage = true }
                        )
                    }

                    item {
                        val effectiveSource = perSongSourceOverride
                            ?: globalSource.takeIf { it != "AUTO" }
                        val sourceLabel = when (effectiveSource) {
                            null -> "自动（全局）"
                            else -> effectiveSource
                        }
                        PlayerMenuActionCard(
                            shape = RoundedCornerShape(8.dp),
                            icon = Dns,
                            title = "音源",
                            value = sourceLabel,
                            iconAlpha = 1f,
                            onClick = {
                                showSourcePicker = true
                                onDismiss()
                            }
                        )
                    }

                    item {
                        PlayerMenuActionCard(
                            shape = RoundedCornerShape(8.dp),
                            icon = SongListAdd,
                            title = stringResource(R.string.add_to_songList),
                            onClick = {
                                openSongListBottomSheet = true
                                onDismiss()
                            }
                        )
                    }

                    item {
                        PlayerMenuActionCard(
                            shape = RoundedCornerShape(
                                topStart = 8.dp,
                                topEnd = 8.dp,
                                bottomStart = 16.dp,
                                bottomEnd = 16.dp
                            ),
                            icon = Icons.Outlined.Share,
                            title = stringResource(R.string.share),
                            onClick = {
                                currentSong?.id?.let {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, "https://music.163.com/#/song?id=$it")
                                    }
                                    context.startActivity(
                                        Intent.createChooser(
                                            shareIntent,
                                            context.getString(R.string.share_link)
                                        )
                                    )
                                }
                            }
                        )
                    }

                    item {
                        Spacer(Modifier.height(12.dp))
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
        SourcePickerSheet(
            songId = currentSong?.id,
            currentSource = perSongSourceOverride ?: globalSource,
            onDismiss = { showSourcePicker = false },
            onApply = { selected ->
                showSourcePicker = false
                currentSong?.id?.let { songId ->
                    scope.launch {
                        val effective = selected.takeIf { it != "AUTO" }
                        SongSourceCache.setForSong(songId, effective)
                        val controller = mediaController
                        val current = playerState?.currentMediaItem
                        val index = controller?.currentMediaItemIndex
                        if (controller != null && current != null &&
                            index != null && index != C.INDEX_UNSET
                        ) {
                            // Replace only the current item (queue preserved). The
                            // changed URI makes ResolvingDataSource re-resolve the
                            // song under the new per-song source, then reload it.
                            controller.setMediaItem(index, current.withSongSource(effective))
                        }
                        Toast.makeText(
                            context,
                            if (effective == null) "已恢复自动音源" else "已切换到 ${selectedLabel(selected)} 音源",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        )
    }

    SongListBottomSheet(song = currentSong, onDismiss = {
        openSongListBottomSheet = false
    }, openBottomSheet = openSongListBottomSheet)
}

/**
 * Map a raw unblock source value to its friendly label for toast/preview text.
 */
private fun selectedLabel(source: String): String =
    unblockSourceOptions.firstOrNull { it.value == source }?.label ?: source

/**
 * Picker for the per-song unblock source. Selecting "AUTO"/"自动" clears the
 * per-song override so the song follows the global setting; any other value is
 * persisted per-song and immediately re-resolves the current track.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourcePickerSheet(
    songId: Long?,
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
                text = "为「${songId ?: 0L}」选择音源",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            unblockSourceOptions.forEach { option ->
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

