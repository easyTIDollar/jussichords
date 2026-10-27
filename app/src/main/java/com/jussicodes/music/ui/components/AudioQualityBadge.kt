package com.jussicodes.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Tracks
import com.rcmiku.ncmapi.api.player.SongLevel

data class AudioQualitySpec(
    val label: String,
    val isHiRes: Boolean,
    val isLossless: Boolean
)

fun resolveAudioQualitySpec(tracks: Tracks?, preferredLevel: SongLevel): AudioQualitySpec {
    var selectedFormat: Format? = null
    if (tracks != null) {
        val audioGroup = tracks.groups.firstOrNull { it.type == C.TRACK_TYPE_AUDIO && it.isSelected }
        if (audioGroup != null) {
            for (i in 0 until audioGroup.length) {
                if (audioGroup.isTrackSelected(i)) {
                    selectedFormat = audioGroup.getTrackFormat(i)
                    break
                }
            }
            if (selectedFormat == null && audioGroup.length > 0) {
                selectedFormat = audioGroup.getTrackFormat(0)
            }
        }
    }

    if (selectedFormat != null) {
        val mime = selectedFormat.sampleMimeType.orEmpty().lowercase()
        val sampleRate = selectedFormat.sampleRate
        val bitrate = selectedFormat.bitrate
        val pcmEncoding = selectedFormat.pcmEncoding

        val isFlacOrWav = mime.contains("flac") || mime.contains("wav") || mime.contains("audio/x-flac")
        val isHiRes = isFlacOrWav && (sampleRate >= 48000 || pcmEncoding == C.ENCODING_PCM_24BIT || pcmEncoding == C.ENCODING_PCM_32BIT || bitrate > 1_000_000)

        if (isHiRes) {
            return AudioQualitySpec(
                label = if (preferredLevel == SongLevel.JYMASTER) "超清母带" else "Hi-Res",
                isHiRes = true,
                isLossless = true
            )
        }

        if (isFlacOrWav) {
            return AudioQualitySpec(
                label = "无损",
                isHiRes = false,
                isLossless = true
            )
        }

        val kbps = if (bitrate > 0) bitrate / 1000 else 0
        if (kbps >= 250 || preferredLevel == SongLevel.EXHIGH) {
            return AudioQualitySpec(
                label = "极高",
                isHiRes = false,
                isLossless = false
            )
        } else {
            return AudioQualitySpec(
                label = "标准",
                isHiRes = false,
                isLossless = false
            )
        }
    }

    // tracks 尚未解析出 format 时的兜底显示（基于当前选中的偏好档位）
    return when (preferredLevel) {
        SongLevel.STANDARD -> AudioQualitySpec("标准", isHiRes = false, isLossless = false)
        SongLevel.HIGHER -> AudioQualitySpec("较高", isHiRes = false, isLossless = false)
        SongLevel.EXHIGH -> AudioQualitySpec("极高", isHiRes = false, isLossless = false)
        SongLevel.LOSSLESS -> AudioQualitySpec("无损", isHiRes = false, isLossless = true)
        SongLevel.HIRES -> AudioQualitySpec("Hi-Res", isHiRes = true, isLossless = true)
        SongLevel.JYMASTER -> AudioQualitySpec("超清母带", isHiRes = true, isLossless = true)
        else -> AudioQualitySpec("标准", isHiRes = false, isLossless = false)
    }
}

@Composable
fun AudioQualityBadge(
    tracks: Tracks?,
    preferredLevel: SongLevel,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val spec = resolveAudioQualitySpec(tracks, preferredLevel)

    val (badgeBg, borderColor, textColor) = when {
        spec.isHiRes -> Triple(
            Color(0xFFE5A93C).copy(alpha = 0.15f),
            Color(0xFFE5A93C).copy(alpha = 0.45f),
            Color(0xFFE5A93C)
        )
        spec.isLossless -> Triple(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
            MaterialTheme.colorScheme.primary
        )
        else -> Triple(
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(badgeBg)
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = spec.label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.2.sp
            ),
            color = textColor
        )
    }
}
