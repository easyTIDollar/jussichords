package com.jussicodes.music.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.jussicodes.music.R
import com.rcmiku.ncmapi.api.player.SongLevel

@Composable
fun SongQualityDialog(
    currentLevel: SongLevel,
    onDismiss: () -> Unit,
    onQualitySelected: (SongLevel) -> Unit
) {
    val isLandscape = isLandscapeOrientation()

    ResponsiveDialogWindow(onDismissRequest = onDismiss) {
        ScaledDialogContent {
            Card(
                shape = MaterialTheme.shapes.extraLarge
            ) {
                if (isLandscape) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 16.dp)
                    ) {
                        Text(
                            text = "选择播放音质",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                        val pairs = SongLevel.entries.chunked(2)
                        pairs.forEach { rowLevels ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowLevels.forEach { level ->
                                    QualityOptionRow(
                                        level = level,
                                        selected = (level == currentLevel),
                                        onClick = {
                                            onQualitySelected(level)
                                            onDismiss()
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (rowLevels.size < 2) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .selectableGroup()
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 20.dp)
                    ) {
                        Text(
                            text = "选择播放音质",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
                        )
                        SongLevel.entries.forEach { level ->
                            QualityOptionRow(
                                level = level,
                                selected = (level == currentLevel),
                                onClick = {
                                    onQualitySelected(level)
                                    onDismiss()
                                },
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QualityOptionRow(
    level: SongLevel,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton,
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = null
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = when (level) {
                SongLevel.STANDARD -> stringResource(R.string.standard)
                SongLevel.HIGHER -> stringResource(R.string.higer)
                SongLevel.EXHIGH -> stringResource(R.string.exhigh)
                SongLevel.LOSSLESS -> stringResource(R.string.lossless)
                SongLevel.HIRES -> stringResource(R.string.hi_res)
                SongLevel.JYEFFECT -> stringResource(R.string.jyeffect)
                SongLevel.SKY -> stringResource(R.string.sky)
                SongLevel.DOLBY -> stringResource(R.string.dolby)
                SongLevel.JYMASTER -> stringResource(R.string.jymaster)
            },
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1
        )
    }
}
