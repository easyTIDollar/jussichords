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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.jussicodes.music.data.LTMember
import com.jussicodes.music.ui.icons.AirpodsCase

private val AvatarSize = 30.dp
private val AirpodsSize = 34.dp

/**
 * 一起听房间成员小组件：两个圆形头像交叠叠放，**左右两侧各一只 AirPods 充电盒轮廓**
 * 作为陪衬，压在头像之下（声明顺序在头像之前，靠 z 序被遮挡），
 * 复刻参考图「耳机线从头像后面露出来」的层次。
 *
 * "不违和"的几个刻意选择：
 * - AirPods 只描边、尺寸比头像**略大**，盒身自然从头像两侧探出，而不是缩在旁边像个独立按钮；
 * - 用 onSurfaceVariant 染色 + 常态 0.6 不透明度，与头像的浅色描边一起退到背景层；
 * - 被头像压住一半，模拟参考图里"线被圆盘切掉"的层次；
 * - 两个头像重叠约 40%，第二个盖在第一个之上，与参考图一致。
 *
 * @param members 房间成员，取前两个；不足两个时补同尺寸空槽，保持左右对称不塌。
 * @param reconnecting 正在重新同步房间：此时 Airpods 轻微呼吸，其余状态静态。
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
    val airpodsAlpha = if (reconnecting) {
        val transition = rememberInfiniteTransition(label = "ListenTogetherChipReconnect")
        val breath by transition.animateFloat(
            initialValue = 0.3f,
            targetValue = 0.8f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 700),
                repeatMode = RepeatMode.Reverse
            ),
            label = "ListenTogetherChipBreath"
        )
        breath
    } else {
        0.6f
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        // 不用负间距（Arrangement.spacedBy 传负 Dp 行为不可靠），改用每个子项自己的
        // offset：头像 -9dp 互相压叠、同时向两侧的 Airpods 底下探进去。
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Icon(
            imageVector = AirpodsCase,
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .offset(x = (-4).dp)
                .size(AirpodsSize)
                .alpha(airpodsAlpha)
        )
        ListenTogetherAvatar(members.getOrNull(0), slotColor, tint)
        ListenTogetherAvatar(members.getOrNull(1), slotColor, tint)
        Icon(
            imageVector = AirpodsCase,
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .offset(x = 4.dp)
                .size(AirpodsSize)
                .alpha(airpodsAlpha)
        )
    }
}

/** 单个成员头像；[member] 为 null 时画同尺寸空槽，保持左右对称。 */
@Composable
private fun ListenTogetherAvatar(
    member: LTMember?,
    slotColor: Color,
    outline: Color,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            // Row 内相邻头像互相压叠：负 offset 让后者盖在前者上。
            .offset(x = (-9).dp)
            .size(AvatarSize)
            .clip(CircleShape)
            .background(slotColor)
            // 浅色描边是参考图里"圆盘浮在耳机线上"的层次关键。
            .border(2.dp, outline.copy(alpha = 0.9f), CircleShape)
    ) {
        if (member != null) {
            AsyncImage(
                model = member.avatarUrl,
                contentDescription = member.nickname,
                contentScale = ContentScale.Crop,
                // 描边会吃掉 2dp，这里按 AvatarSize 铺满由 clip(CircleShape) 裁圆。
                modifier = Modifier.size(AvatarSize)
            )
        }
    }
}