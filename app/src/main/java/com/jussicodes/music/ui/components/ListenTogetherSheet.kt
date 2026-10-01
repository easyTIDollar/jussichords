package com.jussicodes.music.ui.components

import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.outlined.Share
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.jussicodes.music.data.MsgSendTextResponse
import com.jussicodes.music.data.MsgSessionCache
import com.jussicodes.music.playback.ListenTogetherSession
import com.jussicodes.music.utils.CoverImageSize
import com.jussicodes.music.utils.toCoverImageUrl
import com.rcmiku.ncmapi.api.apiGet
import kotlinx.coroutines.launch

/**
 * 一起听底部面板：未进房时提供「创建房间 / 粘贴官方邀请链接进房」，
 * 进房后展示成员、邀请链接（复制/分享）与结束按钮。
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
    val scope = rememberCoroutineScope()
    val uiState by ListenTogetherSession.state.collectAsState()
    var invitationText by remember { mutableStateOf("") }
    // 好友选择条：复用私信会话缓存（/msg/private + /msg/recentcontact 在线状态），
    // 进房时拉一次；只留可私信类型（普通 0 / 音乐人 207），点好友直接 /send/text 发邀请链接。
    val cachedItems by MsgSessionCache.items.collectAsState()
    var sendingUserId by remember { mutableStateOf<Long?>(null) }
    var sendNote by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(openBottomSheet) {
        if (openBottomSheet) {
            bottomSheetState.show()
            MsgSessionCache.ensureLoaded(scope)
        }
    }

    val contacts = (cachedItems.orEmpty())
        .filter { it.user.userType in MsgSessionCache.allowedUserTypes }

    /** 把邀请链接直接私信给好友（/send/text，type=text），成功自动发完关面板。 */
    fun sendInviteToFriend(userId: Long, nickname: String, url: String) {
        val target = userId
        scope.launch {
            sendingUserId = target
            sendNote = null
            val result = apiGet<MsgSendTextResponse>(
                "/send/text",
                mapOf("user_ids" to target, "msg" to "和我一起听歌：$url")
            )
            result.onSuccess { body ->
                if (body.code == 200) {
                    Toast.makeText(context, "已发送给 ${nickname.ifBlank { "对方" }}", Toast.LENGTH_SHORT).show()
                    onDismiss()
                } else {
                    // 已知 NCM 错误码映射（见 NCM API issue #1844：2201 = 私信风控「发送频繁」，
                    // 账号级冷却，数分钟后自动恢复，与消息内容无关）；其余码原样透出 msg 便于定位。
                    val serverMsg = body.msg?.takeIf { it.isNotBlank() } ?: ""
                    sendNote = when (body.code) {
                        301 -> "登录已失效，请重新登录"
                        2201 -> "发送过于频繁（NCM 私信风控），请过几分钟再试$serverMsg".trim()
                        else -> "发送失败（code ${body.code}）$serverMsg".trim()
                    }
                }
            }.onFailure {
                sendNote = it.message ?: "发送失败"
            }
            sendingUserId = null
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
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    uiState.room?.let { room ->
                        Text(
                            text = if (uiState.isHost) "房主" else "成员",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
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
                    // 进房中：成员 + 邀请 + 结束
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
                                Text(
                                    text = "${room.members.size} 人",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                room.members.take(6).forEach { member ->
                                    Box(
                                        modifier = Modifier.size(32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AsyncImage(
                                            model = member.avatarUrl,
                                            contentDescription = member.nickname,
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(CircleShape)
                                        )
                                    }
                                }
                                if (room.members.size > 6) {
                                    Text(
                                        text = "+${room.members.size - 6}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            uiState.inviteUrl?.let { url ->
                                Text(
                                    text = url,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    // 邀请好友：横滑行 = 无底系统分享兜底（首位）+ 可私信联系人（复用私信缓存），
                    // 点好友 → /send/text 直接把邀请链接发成私信，对方在私信里收到即可点进房。
                    InviteFriendRow(
                        contacts = contacts,
                        loading = cachedItems == null && contacts.isEmpty(),
                        inviteUrl = uiState.inviteUrl,
                        sendingUserId = sendingUserId,
                        sendNote = sendNote,
                        onPickFriend = { item, url ->
                            sendInviteToFriend(item.user.userId, item.user.nickname, url)
                        },
                        onSystemShare = { url -> shareInviteLink(url, context) }
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { copyInviteLink(uiState.inviteUrl, context) },
                            enabled = uiState.inviteUrl != null,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("复制邀请")
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

private fun copyInviteLink(url: String?, context: android.content.Context) {
    if (url.isNullOrBlank()) return
    val manager = context.getSystemService(ClipboardManager::class.java) ?: return
    manager.setPrimaryClip(ClipData.newPlainText("listen-together", url))
    Toast.makeText(context, "邀请链接已复制", Toast.LENGTH_SHORT).show()
}

private fun shareInviteLink(url: String?, context: android.content.Context) {
    if (url.isNullOrBlank()) return
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "和我一起在网易云听歌：$url")
    }
    context.startActivity(Intent.createChooser(intent, "邀请一起听"))
}

/**
 * 邀请好友横滑行：首位是无底系统分享兜底（与 [com.jussicodes.music.ui.components.ShareSheet]
 * 同款「分享链接」裸图标），后续是可私信联系人（在线绿点复用私信缓存）。
 * 点联系人 → 直接把邀请链接 /send/text 发成私信；发送中该行锁输入并显示回执文案。
 */
@Composable
private fun InviteFriendRow(
    contacts: List<MsgSessionCache.Item>,
    loading: Boolean,
    inviteUrl: String?,
    sendingUserId: Long?,
    sendNote: String?,
    onPickFriend: (MsgSessionCache.Item, String) -> Unit,
    onSystemShare: (String?) -> Unit
) {
    val enabled = inviteUrl != null && sendingUserId == null
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
    ) {
        item(key = "share_link") {
            ShareLinkItem(
                onClick = { onSystemShare(inviteUrl) },
                enabled = enabled
            )
        }
        items(contacts, key = { it.user.userId }) { contact ->
            FriendItem(
                contact = contact,
                sending = sendingUserId == contact.user.userId,
                onClick = {
                    val url = inviteUrl
                    if (url != null) onPickFriend(contact, url)
                }
            )
        }
        if (loading && contacts.isEmpty()) {
            item(key = "loading") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
    // 发送回执 / 错误
    sendNote?.let { note ->
        Text(
            text = note,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

/** 横滑行首位：系统分享兜底（“分享链接”），无底色圆——与联系人同高的裸图标 + 文案。 */
@Composable
private fun ShareLinkItem(
    onClick: () -> Unit,
    enabled: Boolean
) {
    val title = "分享链接"
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Share,
                contentDescription = title,
                modifier = Modifier.size(24.dp),
                tint = if (enabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            modifier = Modifier.width(64.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

/** 好友头像 + 在线绿点 + 昵称，点击把邀请链接私信给该好友。 */
@Composable
private fun FriendItem(
    contact: MsgSessionCache.Item,
    sending: Boolean,
    onClick: () -> Unit
) {
    val other = contact.user
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(enabled = !sending, onClick = onClick)
            .padding(vertical = 4.dp)
    ) {
        Box(modifier = Modifier.size(56.dp)) {
            AsyncImage(
                model = other.avatarUrl.toCoverImageUrl(CoverImageSize.LIST),
                contentDescription = other.nickname,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop
            )
            // 在线点（右下角），与私信列表同款
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(if (contact.onlined) Color(0xFF4CAF50) else Color.Gray)
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (sending) "发送中…" else other.nickname.ifBlank { "用户${other.userId}" },
            style = MaterialTheme.typography.labelSmall,
            color = if (sending) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(64.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
