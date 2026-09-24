package com.jussicodes.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jussicodes.music.R

/** 小状态标签（VIP / 互关）：灰底圆角，与私信列表同款样式。 */
@Composable
fun StateTag(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(start = 6.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 4.dp, vertical = 1.dp)
    )
}

/** 昵称下的 VIP / 互关状态行：无任何状态时不渲染（调用侧按需包 Row）。 */
@Composable
fun UserStateTags(vipType: Int, mutual: Boolean) {
    if (vipType == 0 && !mutual) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (vipType != 0) StateTag(stringResource(R.string.msg_contact_vip))
        if (mutual) StateTag(stringResource(R.string.msg_contact_mutual))
    }
}

/** 在线状态点（绿 = 在线，灰 = 离线），样式与私信列表头像角标一致。 */
@Composable
fun OnlineDot(online: Boolean, size: Dp = 10.dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (online) Color(0xFF4CAF50) else Color(0xFF9E9E9E))
            .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape)
    )
}
