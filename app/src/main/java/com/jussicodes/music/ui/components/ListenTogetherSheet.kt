package com.jussicodes.music.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.jussicodes.music.playback.ListenTogetherSession
import com.jussicodes.music.ui.icons.Crown
import com.jussicodes.music.ui.icons.Users

/**
 * 一起听底部面板：未进房时提供「创建房间 / 粘贴官方邀请链接进房」，
 * 进房后展示房间信息，头部显示人数（👥图标）/成员头像/房主（👑图标），
 * 「分享」只调系统分享菜单（复制由专用按钮负责），「结束」退出房间。
 * 实际同步逻辑全在 [ListenTogetherSession]（挂在 PlaybackService 上）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListenTogetherSheet(
    openBottomSheet: Boolean,
    onDismiss: () -> Unit
) {
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val uiState by ListenTogetherSession.state.collectAsState()
    var invitationText by remember { mutableStateOf("") }

    LaunchedEffect(openBottomSheet) {
        if (openBottomSheet) {
            bottomSheetState.show()
        }
    }

    if (openBottomSheet) {
        ModalBottomSheet(onDismissRequest = onDismiss, sheetState = bottomSheetState) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "关闭"
                        )
                    }
                    Text(
                        text = "一起听",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    uiState.room?.let { room ->
                        Spacer(modifier = Modifier.width(10.dp))
                        Icon(
                            imageVector = Users,
                            contentDescription = "房间人数",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${room.members.size} 人",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            room.members.take(4).forEach { member ->
                                AsyncImage(
                                    model = member.avatarUrl,
                                    contentDescription = member.nickname,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                )
                            }
                            if (room.members.size > 4) {
                                Text(
                                    text = "+${room.members.size - 4}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            if (uiState.isHost) {
                                Icon(
                                    imageVector = Crown,
                                    contentDescription = "房主",
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "房主",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Text(
                                    text = "成员",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                if (uiState.reconnecting) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        text = "正在重新同步房间…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                uiState.error?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                uiState.notice?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                val room = uiState.room
                if (room == null) {
                    // 未进房：创建 / 链接进房
                    val busy = uiState.isLoading
                    Button(
                        onClick = { ListenTogetherSession.create() },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("创建房间（同步当前播放）")
                    }
                    OutlinedTextField(
                        value = invitationText,
                        onValueChange = { invitationText = it },
                        label = { Text("官方一起听邀请链接") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedButton(
                        onClick = { ListenTogetherSession.join(invitationText) },
                        enabled = invitationText.isNotBlank() && !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("进入房间")
                    }
                } else {
                    // 进房中：成员 + 分享 + 结束
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "房间 ${room.id}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                TextButton(
                                    onClick = { copyText(room.id, "房间号已复制", context) }
                                ) {
                                    Text(
                                        text = "复制",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                            uiState.inviteUrl?.let { url ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = url,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    TextButton(
                                        onClick = { copyText(url, "邀请链接已复制", context) }
                                    ) {
                                        Text(
                                            text = "复制",
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { shareInviteLink(uiState.inviteUrl, context) },
                            enabled = uiState.inviteUrl != null,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("分享")
                        }
                        OutlinedButton(
                            onClick = {
                                ListenTogetherSession.end()
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("结束")
                        }
                    }
                }

                if (uiState.isLoading) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

private fun copyText(text: String, toast: String, context: android.content.Context) {
    if (text.isBlank()) return
    val manager = context.getSystemService(ClipboardManager::class.java) ?: return
    manager.setPrimaryClip(ClipData.newPlainText("listen-together", text))
    Toast.makeText(context, toast, Toast.LENGTH_SHORT).show()
}

private fun shareInviteLink(url: String?, context: android.content.Context) {
    if (url.isNullOrBlank()) return
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "和我一起在网易云听歌：$url")
    }
    context.startActivity(Intent.createChooser(intent, "邀请一起听"))
}
