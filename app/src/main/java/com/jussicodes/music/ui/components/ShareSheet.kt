package com.jussicodes.music.ui.components

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.align
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.jussicodes.music.R
import com.jussicodes.music.data.MsgSessionCache
import com.jussicodes.music.utils.CoverImageSize
import com.jussicodes.music.utils.toCoverImageUrl
import com.rcmiku.ncmapi.api.msg.MsgApi
import com.rcmiku.ncmapi.model.Album
import com.rcmiku.ncmapi.model.Playlist
import com.rcmiku.ncmapi.model.Song
import kotlinx.coroutines.launch

/** 分享负载：歌曲 / 歌单 / 专辑 三选一。 */
sealed interface SharePayload {
    data class SongShare(val song: Song) : SharePayload
    data class PlaylistShare(val playlist: Playlist) : SharePayload
    data class AlbumShare(val album: Album) : SharePayload

    val kind: String
        get() = when (this) {
            is SongShare -> "歌曲"
            is PlaylistShare -> "歌单"
            is AlbumShare -> "专辑"
        }

    /** 系统分享兜底用的网页链接。 */
    val webUrl: String
        get() = when (this) {
            is SongShare -> "https://music.163.com/#/song?id=${song.id}"
            is PlaylistShare -> "https://music.163.com/#/playlist?id=${playlist.id}"
            is AlbumShare -> "https://music.163.com/#/album?id=${album.id}"
        }
}

/** 分享菜单上排展示的联系人数量上限。 */
private const val MAX_CONTACTS = 5

/**
 * “分享至”底部 sheet：上排最近联系人（[MAX_CONTACTS] 个，头像 + 昵称），下排系统分享兜底。
 * 点联系人 → 输入框（右侧发送按钮，不输入直接发卡片）→ MsgApi.send* 发私信。
 * 需登录；未登录 / 无联系人 / 拉取失败时隐藏上排，只留系统分享行。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareSheet(
    payload: SharePayload,
    openBottomSheet: Boolean,
    onDismiss: () -> Unit,
) {
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedContact by remember { mutableStateOf<MsgSessionCache.Item?>(null) }
    // 复用消息页私信会话缓存（/msg/private + /msg/recentcontact 在线状态），分享入口不再自发起请求
    val cachedItems by MsgSessionCache.items.collectAsState()
    val loading = cachedItems == null

    LaunchedEffect(openBottomSheet, payload) {
        if (openBottomSheet) {
            bottomSheetState.show()
            selectedContact = null
            MsgSessionCache.ensureLoaded(scope)
        }
    }
    val contacts = (cachedItems.orEmpty()).take(MAX_CONTACTS)

    if (openBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = bottomSheetState,
        ) {
            val chosen = selectedContact
            if (chosen != null) {
                // 点联系人后：替换为私信输入框
                NcmMsgSendRow(
                    contact = chosen,
                    payload = payload,
                    onBack = { selectedContact = null },
                    onClose = onDismiss
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                ) {
                    Text(
                        text = "分享至",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    when {
                        loading -> Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(72.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }

                        contacts.isNotEmpty() -> Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            contacts.forEach { contact ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clickable { selectedContact = contact }
                                        .padding(vertical = 4.dp)
                                ) {
                                    ContactAvatar(contact)
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = contact.user.nickname.ifBlank { "用户${contact.user.userId}" },
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.width(64.dp),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ContactAppItem(title = stringResource(R.string.share_link)) {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, payload.webUrl)
                            }
                            context.startActivity(
                                Intent.createChooser(shareIntent, context.getString(R.string.share_link))
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 联系人私信输入框：发送按钮在右；不输入直接发卡片，有文字则一并带出。 */
@Composable
private fun NcmMsgSendRow(
    contact: MsgSessionCache.Item,
    payload: SharePayload,
    onBack: () -> Unit,
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var msg by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val other = contact.user

    fun send(text: String) {
        scope.launch {
            sending = true
            error = null
            val result = when (payload) {
                is SharePayload.SongShare -> MsgApi.sendSong(other.userId.toString(), payload.song.id, text)
                is SharePayload.PlaylistShare -> MsgApi.sendPlaylist(other.userId.toString(), payload.playlist.id, text)
                is SharePayload.AlbumShare -> MsgApi.sendAlbum(other.userId.toString(), payload.album.id, text)
            }
            sending = false
            val resp = result.getOrNull()
            if (resp != null && resp.code == 200) {
                Toast.makeText(context, "已发送给 ${other.nickname.ifBlank { "对方" }}", Toast.LENGTH_SHORT).show()
                onClose()
            } else {
                error = resp?.msg?.takeIf { it.isNotBlank() } ?: "发送失败，请稍后重试"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(56.dp)
                    .clickable(enabled = !sending, onClick = onBack)
            ) {
                ContactAvatar(contact)
            }
            Spacer(Modifier.size(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = other.nickname.ifBlank { "用户${other.userId}" },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "发送${payload.kind}私信",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (error != null) {
                    Text(
                        text = error!!,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = msg,
                onValueChange = { msg = it },
                placeholder = { Text("可留空，直接发送${payload.kind}卡片") },
                singleLine = true,
                enabled = !sending,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = if (sending) "…" else "发送",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = !sending) { send(msg.trim()) }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}

/** 分享联系人头像：圆形 + 在线绿点（recentcontact.onlined）。 */
@Composable
private fun ContactAvatar(contact: MsgSessionCache.Item) {
    val other = contact.user
    Box(modifier = Modifier.size(56.dp)) {
        AsyncImage(
            model = other.avatarUrl.toCoverImageUrl(CoverImageSize.LIST),
            contentDescription = other.nickname,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        // 在线点（右下角）
        Box(
            modifier = Modifier
                .size(12.dp)
                .align(Alignment.BottomEnd)
                .clip(CircleShape)
                .background(if (contact.onlined) Color(0xFF4CAF50) else Color.Gray)
                .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
        )
    }
}

/** 下排应用格：系统分享兜底（“更多…”语义，复用现有 R.string.share_link 文案）。 */
@Composable
private fun ContactAppItem(
    title: String,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Share,
                contentDescription = title,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            modifier = Modifier.width(64.dp),
            textAlign = TextAlign.Center
        )
    }
}
