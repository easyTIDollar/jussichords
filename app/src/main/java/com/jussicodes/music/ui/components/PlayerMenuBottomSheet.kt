package com.jussicodes.music.ui.components

import android.content.Intent
import android.os.Bundle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.jussicodes.music.LocalPlayerController
import com.jussicodes.music.LocalPlayerState
import com.jussicodes.music.R
import com.jussicodes.music.constants.MediaSessionConstants
import com.jussicodes.music.constants.audioEffectIntensityKey
import com.jussicodes.music.constants.audioEffectModeKey
import com.jussicodes.music.constants.audioEffectSpeedKey
import com.jussicodes.music.playback.audio.AudioEffectMode
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
    var audioEffectIntensity by rememberPreference(audioEffectIntensityKey, 0.5f)
    var audioEffectSpeed by rememberPreference(audioEffectSpeedKey, 0.5f)
    var showAudioEffectPage by rememberSaveable { mutableStateOf(false) }
    val repeatIcon = when (repeatMode) {
        1 -> RepeatOne
        else -> Repeat
    }
    val audioEffectModeText = when (audioEffectMode) {
        AudioEffectMode.OFF -> "关闭"
        AudioEffectMode.EIGHT_D -> "8D 环绕"
        AudioEffectMode.BASS_BOOST -> "低音增强"
        AudioEffectMode.MUFFLED -> "闷声"
        AudioEffectMode.REVERB -> "混响"
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
                    audioEffectMode = audioEffectMode,
                    onAudioEffectModeChange = { audioEffectMode = it },
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
                            iconAlpha = if (audioEffectMode == AudioEffectMode.OFF) 0.4f else 1f,
                            onClick = { showAudioEffectPage = true }
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

    SongListBottomSheet(song = currentSong, onDismiss = {
        openSongListBottomSheet = false
    }, openBottomSheet = openSongListBottomSheet)
}

@Composable
private fun AudioEffectPage(
    audioEffectMode: AudioEffectMode,
    onAudioEffectModeChange: (AudioEffectMode) -> Unit,
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
                value = audioEffectMode.label,
                onClick = onBack
            )
        }

        AudioEffectMode.entries.forEach { mode ->
            item {
                AudioEffectOption(
                    mode = mode,
                    selected = audioEffectMode == mode,
                    onClick = { onAudioEffectModeChange(mode) }
                )
            }
        }

        item {
            AudioEffectSlider(
                title = when (audioEffectMode) {
                    AudioEffectMode.EIGHT_D -> "空间旋转幅度"
                    AudioEffectMode.BASS_BOOST -> "低音强度"
                    AudioEffectMode.MUFFLED -> "闷声程度"
                    AudioEffectMode.REVERB -> "混响强度"
                    AudioEffectMode.OFF -> "音效强度"
                },
                value = intensity,
                enabled = audioEffectMode != AudioEffectMode.OFF,
                onValueChange = onIntensityChange
            )
        }

        item {
            AudioEffectSlider(
                title = "8D 旋转速度",
                value = speed,
                enabled = audioEffectMode == AudioEffectMode.EIGHT_D,
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
    mode: AudioEffectMode,
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
                .selectable(
                    selected = selected,
                    onClick = onClick,
                    role = Role.RadioButton
                )
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = null)
            Text(
                text = mode.label,
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

private val AudioEffectMode.label: String
    get() = when (this) {
        AudioEffectMode.OFF -> "关闭"
        AudioEffectMode.EIGHT_D -> "8D 环绕"
        AudioEffectMode.BASS_BOOST -> "低音增强"
        AudioEffectMode.MUFFLED -> "闷声"
        AudioEffectMode.REVERB -> "混响"
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
