package com.jussicodes.music.ui.screen

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import com.jussicodes.music.R
import com.jussicodes.music.LocalPlayerController
import com.jussicodes.music.extensions.playMediaAtId
import com.jussicodes.music.extensions.setPlaylist
import com.jussicodes.music.ui.navigation.AlbumNav
import com.jussicodes.music.ui.navigation.PlaylistNav
import com.jussicodes.music.utils.CoverImageSize
import com.jussicodes.music.utils.formatTimestamp
import com.jussicodes.music.utils.toCoverImageUrl
import com.jussicodes.music.viewModel.PrivateChatViewModel
import com.rcmiku.ncmapi.model.Artist
import com.rcmiku.ncmapi.model.MsgPrivateInner
import com.rcmiku.ncmapi.model.MsgPrivateMessage
import com.rcmiku.ncmapi.model.MsgPrivatePlaylist
import com.rcmiku.ncmapi.model.MsgPrivateSong
import com.rcmiku.ncmapi.model.Song
import com.rcmiku.ncmapi.model.SongAlbum
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** 内层 JSON（私信 msg 字段）容错解析。 */
private val chatInnerJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

/** 把私信内层的歌曲小模型拼成可起播的 Song（NCM 播放只需 id/name/封面，其余走默认值）。 */
private fun MsgPrivateSong.toPlayableSong(cover: String): Song = Song(
    id = id,
    name = name,
    ar = artists.map { Artist(name = it.name) },
    al = SongAlbum(picUrl = cover)
)

/**
 * 从内层 JSON 原始 song 对象挖真封面。
 * NCM 私信里 song.picUrl 顶层恒为 null，封面实际在 song.album.picUrl（兜底 blurPicUrl）；
 * MsgPrivateSong 没带 album 字段，故直接对原始 JSON 取值。
 */
private fun songCoverFromRaw(rawSong: JsonObject?): String {
    fun str(obj: JsonObject?, key: String): String? =
        (obj?.get(key) as? JsonPrimitive)?.content
    val top = str(rawSong, "picUrl")
    val album = rawSong?.get("album") as? JsonObject
    val albumPic = str(album, "picUrl")
    val blur = str(album, "blurPicUrl")
    return top?.takeIf { it.isNotBlank() }
        ?: albumPic?.takeIf { it.isNotBlank() }
        ?: blur?.takeIf { it.isNotBlank() }
        ?: ""
}

/** 原始 JsonObject 取字符串字段（容错：缺失/null 返回空）。 */
private fun JsonObject?.jStr(key: String): String? =
    (this?.get(key) as? JsonPrimitive)?.content

/** 原始 JsonObject 取 long 字段（容错）。 */
private fun JsonObject?.jLong(key: String): Long =
    (this?.get(key) as? JsonPrimitive)?.content?.toLongOrNull() ?: 0L

/** 私信内层的歌单小模型。 */
private fun playlistCardName(p: MsgPrivatePlaylist): String =
    p.name.ifBlank { p.coverImgUrl.takeIf { it.isNotBlank() } ?: "歌单" }

/**
 * 单人私信聊天页：历史消息气泡（/msg/private/history）+ 底部输入框（/send/text）。
 * 历史接口返回倒序（最新在前），UI 反转后旧消息在上、新消息在下；历史变化时自动滚底。
 * 私信内层 JSON 携带的 song / playlist 渲染成可交互小卡片：点歌曲直接起播、点歌单跳详情。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivateChatScreen(
    navController: NavHostController,
    viewModel: PrivateChatViewModel = hiltViewModel()
) {
    val history by viewModel.history.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isSending by viewModel.isSending.collectAsState()
    val sendError by viewModel.sendError.collectAsState()
    val refreshTick by viewModel.refreshTick.collectAsState()
    val mediaController = LocalPlayerController.current.controller

    var draft by remember { mutableStateOf("") }
    val chatList = remember(history) { history.asReversed() }
    val listState = rememberLazyListState()

    LaunchedEffect(refreshTick) {
        if (chatList.isNotEmpty()) listState.scrollToItem(chatList.lastIndex)
    }

    // 首次加载完成也滚到底（isLoading 由 true→false）
    LaunchedEffect(isLoading) {
        if (!isLoading && chatList.isNotEmpty()) listState.scrollToItem(chatList.lastIndex)
    }

    fun onSend() {
        if (isSending || draft.isBlank()) return
        viewModel.send(draft)
        draft = ""
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (viewModel.contactAvatar.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                            ) {
                                AsyncImage(
                                    model = viewModel.contactAvatar.toCoverImageUrl(CoverImageSize.LIST),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                                )
                            }
                            Spacer(Modifier.size(10.dp))
                        }
                        Text(viewModel.contactName, style = MaterialTheme.typography.titleMedium)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null
                        )
                    }
                }
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.msg_chat_input_hint)) },
                    maxLines = 4,
                    enabled = !isSending,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSend() })
                )
                IconButton(
                    onClick = { onSend() },
                    enabled = !isSending && draft.isNotBlank()
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = stringResource(R.string.msg_chat_send),
                        tint = if (draft.isNotBlank()) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (sendError != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(8.dp))
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(sendError!!, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (chatList.isEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.msg_chat_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        items(chatList.size, key = { index ->
                            val m = chatList[index]
                            "${m.time}_${m.msg.take(32)}_$index"
                        }) { index ->
                            ChatBubble(
                                msg = chatList[index],
                                contactId = viewModel.contactUserId,
                                mediaController = mediaController,
                                onOpenPlaylist = { playlistId ->
                                    navController.navigate(
                                        PlaylistNav(playlistId = playlistId, noCache = true)
                                    )
                                },
                                onOpenAlbum = { albumId ->
                                    navController.navigate(AlbumNav(albumId = albumId))
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 单条消息气泡：toUser == 对方 = 我发的（右对齐）；否则收到的（左对齐）。 */
@Composable
private fun ChatBubble(
    msg: MsgPrivateMessage,
    contactId: Long,
    mediaController: androidx.media3.session.MediaController?,
    onOpenPlaylist: (Long) -> Unit,
    onOpenAlbum: (Long) -> Unit
) {
    val isMine = msg.toUser?.userId == contactId
    val inner = msg.msg
        .takeIf { it.isNotBlank() }
        ?.let { runCatching { chatInnerJson.decodeFromString<MsgPrivateInner>(it) }.getOrNull() }
    val body = inner?.msg.orEmpty()
    val song = inner?.song?.takeIf { it.id > 0 }
    val playlist = inner?.playlist?.takeIf { it.id > 0 }
    // 封面真值在 song.album.picUrl，NCM 顶层 song.picUrl 恒为 null，故从原始 JSON 挖
    val rawObj = msg.msg
        .takeIf { it.isNotBlank() }
        ?.let { runCatching { chatInnerJson.parseToJsonElement(it) }.getOrNull() as? JsonObject }
    val songCover = if (song != null) songCoverFromRaw(rawObj?.get("song") as? JsonObject) else ""
    // 专辑 / MV / 一起听(generalMsg) 的卡片数据：ncmapi 模型只有 song/playlist，一律从原始 JSON 取
    val album = (rawObj?.get("album") as? JsonObject)?.takeIf { it.jLong("id") > 0 }
    val mv = (rawObj?.get("mv") as? JsonObject)?.takeIf { it.jLong("id") > 0 }
    val general = rawObj?.get("generalMsg") as? JsonObject
    val showGeneralCard = general != null &&
        (general.jStr("title").orEmpty().isNotBlank() || general.jStr("cover").orEmpty().isNotBlank())
    // 正文兜底：msg 文本为空时用 generalMsg.inboxBriefContent（"一起听"邀请的文案）
    val textBody = body.ifBlank { general?.jStr("inboxBriefContent").orEmpty() }

    val ctx = LocalContext.current
    fun openExternal(url: String) {
        if (url.isBlank()) return
        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }
    val cardColor = if (isMine) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurface

    // 卡片容器颜色：跟气泡底色区分，卡片自身用 surfaceVariant
    val cardBg = if (isMine) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.15f)
        else MaterialTheme.colorScheme.surfaceVariant

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
    ) {
        Column(
            horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Row(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = if (isMine) 16.dp else 4.dp,
                            topEnd = if (isMine) 4.dp else 16.dp,
                            bottomStart = 16.dp,
                            bottomEnd = 16.dp
                        )
                    )
                    .background(
                        if (isMine) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Column(modifier = Modifier.widthIn(max = 264.dp)) {
                    if (textBody.isNotBlank()) {
                        Text(
                            text = textBody,
                            style = MaterialTheme.typography.bodyMedium,
                            color = cardColor
                        )
                    }
                    // 歌曲交互卡片
                    if (song != null) {
                        Spacer(Modifier.size(6.dp))
                        SongCard(
                            song = song,
                            cover = songCover,
                            accent = cardColor,
                            cardBg = cardBg,
                            onClick = {
                                mediaController?.setPlaylist(
                                    listOf(song.toPlayableSong(songCover)),
                                    sourceName = "私信"
                                )
                                mediaController?.playMediaAtId(song.id)
                            }
                        )
                    }
                    // 歌单交互卡片
                    if (playlist != null) {
                        Spacer(Modifier.size(6.dp))
                        PlaylistCard(
                            playlist = playlist,
                            accent = cardColor,
                            cardBg = cardBg,
                            onClick = { onOpenPlaylist(playlist.id) }
                        )
                    }
                    // 专辑卡片：封面 + 专辑名/歌手，点击进专辑详情
                    if (album != null) {
                        Spacer(Modifier.size(6.dp))
                        MsgResourceCard(
                            title = album.jStr("name") ?: "",
                            subtitle = (album?.get("artist") as? JsonObject)?.jStr("name") ?: "",
                            tag = stringResource(R.string.msg_chat_album_title),
                            cover = album.jStr("picUrl")
                                ?.ifBlank { album.jStr("blurPicUrl") }
                                ?: "",
                            accent = cardColor,
                            cardBg = cardBg,
                            onClick = { onOpenAlbum(album.jLong("id")) }
                        )
                    }
                    // MV 卡片：封面 + 歌名/歌手，点击开网易云 MV 页
                    if (mv != null) {
                        Spacer(Modifier.size(6.dp))
                        MsgResourceCard(
                            title = mv.jStr("name") ?: "",
                            subtitle = mv.jStr("artistName") ?: "",
                            tag = stringResource(R.string.msg_chat_mv_title),
                            cover = mv.jStr("imgurl")
                                ?.ifBlank { mv.jStr("imgurl16v9") }
                                ?: "",
                            accent = cardColor,
                            cardBg = cardBg,
                            onClick = { openExternal("https://music.163.com/mv/${mv.jLong("id")}") }
                        )
                    }
                    // 一起听 / 运营通知卡片：generalMsg（封面 + 标题，文案兜底在正文）
                    if (showGeneralCard) {
                        Spacer(Modifier.size(6.dp))
                        MsgResourceCard(
                            title = general?.jStr("title") ?: "",
                            subtitle = "",
                            tag = stringResource(R.string.msg_chat_general_hint),
                            cover = general?.jStr("cover") ?: "",
                            accent = cardColor,
                            cardBg = cardBg,
                            clickable = false
                        )
                    }
                }
            }
            Text(
                text = formatTimestamp(msg.time),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp).padding(horizontal = 4.dp)
            )
        }
    }
}

/** 歌曲小卡片：封面 + 歌名/歌手 + 播放按钮，点击起播。 */
@Composable
private fun SongCard(
    song: MsgPrivateSong,
    cover: String,
    accent: androidx.compose.ui.graphics.Color,
    cardBg: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    val artist = song.artists.firstOrNull()?.name
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(cardBg)
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (cover.isNotBlank()) {
                AsyncImage(
                    model = cover.toCoverImageUrl(CoverImageSize.LIST),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp))
                )
            }
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(cardBg.copy(alpha = 0.5f), CircleShape)
                    .padding(6.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.name.ifBlank { stringResource(R.string.msg_no_content) },
                style = MaterialTheme.typography.bodyMedium,
                color = accent,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            artist?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = accent.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** 歌单小卡片：封面 + 歌单名，点击进歌单详情。 */
@Composable
private fun PlaylistCard(
    playlist: MsgPrivatePlaylist,
    accent: androidx.compose.ui.graphics.Color,
    cardBg: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    val cover = playlist.coverImgUrl.ifBlank { playlist.picUrl }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(cardBg)
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (cover.isNotBlank()) {
                AsyncImage(
                    model = cover.toCoverImageUrl(CoverImageSize.LIST),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp))
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlistCardName(playlist),
                style = MaterialTheme.typography.bodyMedium,
                color = accent,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(R.string.msg_chat_playlist_hint),
                style = MaterialTheme.typography.bodySmall,
                color = accent.copy(alpha = 0.7f)
            )
        }
    }
}

/** 资源小卡片（专辑 / MV / 一起听等）：封面 + 名称 + 副标题（类型 · 歌手），点击进详情/外链。 */
@Composable
private fun MsgResourceCard(
    title: String,
    tag: String,
    subtitle: String,
    cover: String,
    accent: androidx.compose.ui.graphics.Color,
    cardBg: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit = {},
    clickable: Boolean = true
) {
    val secondLine = listOf(tag, subtitle).filter { it.isNotBlank() }.joinToString(" · ")
    val clickableModifier = if (clickable) Modifier.clickable(onClick = onClick) else Modifier
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(cardBg)
            .then(clickableModifier)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (cover.isNotBlank()) {
                AsyncImage(
                    model = cover.toCoverImageUrl(CoverImageSize.LIST),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp))
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title.ifBlank { stringResource(R.string.msg_no_content) },
                style = MaterialTheme.typography.bodyMedium,
                color = accent,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            if (secondLine.isNotBlank()) {
                Text(
                    text = secondLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = accent.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
    }
}
