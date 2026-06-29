package com.jussicodes.music.ui.components

import android.content.Intent
import android.os.Bundle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.unit.dp
import com.jussicodes.music.LocalPlayerController
import com.jussicodes.music.LocalPlayerState
import com.jussicodes.music.R
import com.jussicodes.music.constants.MediaSessionConstants
import com.jussicodes.music.ui.icons.Repeat
import com.jussicodes.music.ui.icons.RepeatOne
import com.jussicodes.music.ui.icons.Shuffle
import com.jussicodes.music.ui.icons.SongListAdd
import com.jussicodes.music.ui.icons.Timelapse
import com.jussicodes.music.ui.icons.Timer
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
    val repeatIcon = when (repeatMode) {
        1 -> RepeatOne
        else -> Repeat
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