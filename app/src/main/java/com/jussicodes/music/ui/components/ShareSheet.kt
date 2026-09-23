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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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

/**
 * “分享至”底部 sheet：一条横滑行 = 无底系统分享入口（首位，左右滑可看全部可私信联系人），
 * 点联系人 → 输入框（右侧发送按钮，不输入直接发卡片）→ MsgApi.send* 发私信。
 * 需登录；未登录 / 无联系人 / 拉取失败时不显示联系人，仅留分享链接位。
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
    // 列表全量不过滤（含系统号/商家），分享联系人本地只留可私信类型，避免给系统号发私信
    val contacts = (cachedItems.orEmpty())
        .filter { it.user.userType in MsgSessionCache.allowedUserTypes }

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
                        loading && contacts.isEmpty() -> Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(72.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }

                        else -> {
                            // 单条横滑行：首位 = 无底系统分享入口（左右滑可看全部可私信联系人，不再截断 5 个）
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    start = 4.dp,
                                    end = 4.dp
                                )
                            ) {
                                item(key = "share_link") {
                                    ShareLinkItem {
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, payload.webUrl)
                                        }
                                        context.startActivity(
                                            Intent.createChooser(shareIntent, context.getString(R.string.share_link))
                                        )
                                    }
                                }
                                items(contacts, key = { it.user.userId }) { contact ->
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
            Box(
                modifier = Modifier.size(56.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .size(56.dp)
                        .clickable(enabled = !sending, onClick = onBack)
                ) {
                    ContactAvatar(contact)
                }
                // 返回图标叠在头像正中：显式告知"可返回重新选用户"（原隐式点击头像返回）
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "重新选用户",
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
                    tint = MaterialTheme.colorScheme.onSurface
                )
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

/** 分享行首位：系统分享兜底（“分享链接”），无底色圆——与联系人同高的裸图标 + 文案，
 * 视觉上排在第一格、横向可滑出后续联系人。 */
@Composable
private fun ShareLinkItem(
    title: String = stringResource(R.string.share_link),
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
                .clip(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Share,
                contentDescription = title,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
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
