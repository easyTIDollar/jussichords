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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.jussicodes.music.data.LTMember
import com.jussicodes.music.ui.icons.HeadphoneWire

private val AvatarSize = 30.dp
/** 耳机线比头像高一截，上下两端才露在圆盘外面（参考图里的效果）。 */
private val WireHeight = 40.dp

/**
 * 一起听房间成员小组件：两个圆形头像交叠叠放，**左右两侧各一根耳机线**压在头像之下，
 * 复刻参考图「线从圆盘后面绕出来」的层次。
 *
 * "不违和"的几个刻意选择：
 * - 耳机线是**开口细弧**（不是闭合图形）且被头像遮住中段——闭合轮廓在小尺寸下会读成
 *   一个独立物体，挂在头像旁边像贴了两个图标；开口弧才读成一根线从圆盘后面绕出来。
 * - 线比头像高（[WireHeight] > [AvatarSize]），上下两端露在外面。
 * - 线用 onSurfaceVariant、常态 0.55 不透明度，与头像那圈浅色描边同一套色阶，一起退到背景层。
 * - 第二个槽位在「还只有自己」时显示呼吸加载圈，不画灰色空盘（见 [ListenTogetherAvatar]）。
 *
 * @param members 房间成员，取前两个；只有一个时第二个槽位显示加载动画。
 * @param reconnecting 正在重新同步房间：耳机线做轻微呼吸，其余状态静态。
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
    val wireAlpha = if (reconnecting) {
        val transition = rememberInfiniteTransition(label = "ListenTogetherChipReconnect")
        val breath by transition.animateFloat(
            initialValue = 0.25f,
            targetValue = 0.8f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 700),
                repeatMode = RepeatMode.Reverse
            ),
            label = "ListenTogetherChipBreath"
        )
        breath
    } else {
        0.55f
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        // 不用负间距（Arrangement.spacedBy 传负 Dp 行为不可靠），改用每个子项自己的
        // offset：头像 -9dp 互相压叠，同时向两侧的耳机线底下探进去。
        modifier = modifier.clickable(onClick = onClick)
    ) {
        HeadphoneWireMark(tint, wireAlpha, mirrored = true)
        ListenTogetherAvatar(members.getOrNull(0), slotColor, tint)
        ListenTogetherAvatar(members.getOrNull(1), slotColor, tint, loading = members.size < 2)
        HeadphoneWireMark(tint, wireAlpha, mirrored = false)
    }
}

/**
 * 一根耳机线。[mirrored]=true 时水平翻转作左侧那根（复用同一矢量，不另画一份）。
 * 左右各向内偏移一点，让线的中段正好被头像压住。
 */
@Composable
private fun HeadphoneWireMark(tint: Color, alpha: Float, mirrored: Boolean) {
    Icon(
        imageVector = HeadphoneWire,
        contentDescription = null,
        tint = tint,
        modifier = Modifier
            .offset(x = if (mirrored) (-2).dp else 2.dp)
            .size(width = WireHeight * 0.62f, height = WireHeight)
            .alpha(alpha)
            .then(if (mirrored) Modifier.scale(scaleX = -1f) else Modifier)
    )
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
                    // 呼吸加载：中心亮、边缘淡，循环往复。
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
            // 浅色描边是参考图里"圆盘浮在耳机线上"的层次关键。
            .border(2.dp, outline.copy(alpha = 0.9f), CircleShape)
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