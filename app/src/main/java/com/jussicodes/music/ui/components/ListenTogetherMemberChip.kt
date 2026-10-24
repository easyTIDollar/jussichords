package com.jussicodes.music.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.jussicodes.music.data.LTMember

private val AvatarSize = 30.dp

/**
 * 一起听房间成员小组件：最多两个圆形头像交叠叠放（第二个盖在第一个上）。
 *
 * - [reconnecting]（正在重新同步房间）时整体轻微呼吸，其余状态静态；
 * - 房里还只有自己时，第二个槽位显示呼吸渐变的等待位，不画灰色空盘。
 *
 * @param members 房间成员，取前两个；只有一个时第二个槽位显示加载动画。
 * @param reconnecting 正在重新同步房间。
 * @param onClick 点击打开一起听面板。
 */
@Composable
fun ListenTogetherMemberChip(
    members: List<LTMember>,
    reconnecting: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    val slotColor = MaterialTheme.colorScheme.surfaceVariant
    // 只有"正在重新同步"才动，且幅度压小，避免在播放页有持续跳动感。
    val overallAlpha = if (reconnecting) {
        val transition = rememberInfiniteTransition(label = "ListenTogetherChipReconnect")
        val breath by transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 700),
                repeatMode = RepeatMode.Reverse
            ),
            label = "ListenTogetherChipBreath"
        )
        breath
    } else {
        1f
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        // 不用 Arrangement.spacedBy 负间距（传负 Dp 行为不可靠），改用子项自己的 offset。
        modifier = modifier.clickable(onClick = onClick)
    ) {
        ListenTogetherAvatar(members.getOrNull(0), slotColor, tint, overallAlpha)
        ListenTogetherAvatar(
            member = members.getOrNull(1),
            slotColor = slotColor,
            outline = tint,
            overallAlpha = overallAlpha,
            loading = members.size < 2
        )
    }
}

/**
 * 单个成员头像。[member] 为 null 时：
 * - 第二个槽位（[loading]=true，即房里还只有自己）→ 呼吸渐变，表示"等别人进来"；
 * - 否则画同尺寸浅色空槽，保持左右对称不塌。
 */
@Composable
private fun ListenTogetherAvatar(
    member: LTMember?,
    slotColor: Color,
    outline: Color,
    overallAlpha: Float,
    loading: Boolean = false,
) {
    val loadingAlpha = if (loading) {
        val transition = rememberInfiniteTransition(label = "ListenTogetherSlotWaiting")
        val pulse by transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900),
                repeatMode = RepeatMode.Reverse
            ),
            label = "ListenTogetherSlotPulse"
        )
        pulse
    } else {
        1f
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            // Row 内相邻头像互相压叠：负 offset 让后者盖在前者上。
            .offset(x = (-9).dp)
            .size(AvatarSize)
            .clip(CircleShape)
            .background(
                if (loading) {
                    // 等待位：中心亮、边缘淡，循环呼吸。
                    Brush.radialGradient(
                        listOf(
                            slotColor.copy(alpha = 0.9f * loadingAlpha),
                            slotColor.copy(alpha = 0.2f * loadingAlpha)
                        )
                    )
                } else {
                    Brush.linearGradient(listOf(slotColor, slotColor))
                }
            )
            .border(2.dp, outline.copy(alpha = 0.9f * overallAlpha), CircleShape)
    ) {
        if (member != null) {
            AsyncImage(
                model = member.avatarUrl,
                contentDescription = member.nickname,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}