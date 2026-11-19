package com.jussicodes.music.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.jussicodes.music.data.SavedAccount
import com.jussicodes.music.ui.icons.Delete
import com.jussicodes.music.ui.icons.Logout
import com.jussicodes.music.ui.icons.Plus
import com.jussicodes.music.utils.CoverImageSize
import com.jussicodes.music.utils.toCoverImageUrl

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSwitcherSheet(
    accounts: List<SavedAccount>,
    activeUserId: Long,
    loggedIn: Boolean,
    onDismiss: () -> Unit,
    onSwitch: (SavedAccount) -> Unit,
    onRemove: (SavedAccount) -> Unit,
    onAddAccount: () -> Unit,
    onLogout: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ResponsiveModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surfaceContainerHigh,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 480.dp)
                .navigationBarsPadding(),
        ) {
            items(accounts, key = { "${it.userId}_${it.addedAt}" }) { account ->
                val active = account.userId == activeUserId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !active) { onSwitch(account) }
                        .heightIn(min = 64.dp)
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(
                        model = account.avatarUrl.toCoverImageUrl(CoverImageSize.LIST),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)),
                    )
                    Text(
                        text = account.nickname.ifBlank { "未命名账号" },
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // 对勾和删除按钮放进同样 48dp 的盒子居中，两个图标才落在同一条竖线上。
                    // 直接混用 Icon 和 IconButton 会因为 IconButton 自带内边距而错位。
                    Box(
                        modifier = Modifier.size(48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (active) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "当前账号",
                                modifier = Modifier.size(24.dp),
                                tint = colors.primary,
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .clickable { onRemove(account) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Delete,
                                    contentDescription = "移除账号",
                                    modifier = Modifier.size(24.dp),
                                    tint = colors.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onAddAccount)
                        .heightIn(min = 56.dp)
                        .padding(horizontal = 26.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(imageVector = Plus, contentDescription = null, tint = colors.primary)
                    Text("添加账号", color = colors.primary)
                }
            }
            if (loggedIn) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onLogout)
                            .heightIn(min = 56.dp)
                            .padding(horizontal = 26.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(imageVector = Logout, contentDescription = null, tint = colors.error)
                        Text("切到游客模式", color = colors.error)
                    }
                }
            }
        }
    }
}
