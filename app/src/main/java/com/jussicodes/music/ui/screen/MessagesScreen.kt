package com.jussicodes.music.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import com.jussicodes.music.R
import com.jussicodes.music.constants.ListThumbnailSize
import com.jussicodes.music.constants.ThumbnailCornerRadius
import com.jussicodes.music.ui.navigation.PlaylistNav
import com.jussicodes.music.utils.CoverImageSize
import com.jussicodes.music.utils.formatTimestamp
import com.jussicodes.music.utils.toCoverImageUrl
import com.jussicodes.music.viewModel.MessagesScreenViewModel
import com.rcmiku.ncmapi.model.MsgComment
import com.rcmiku.ncmapi.model.MsgForward
import com.rcmiku.ncmapi.model.MsgNotice
import com.rcmiku.ncmapi.model.MsgNoticeInner
import com.rcmiku.ncmapi.model.MsgPrivateInner
import com.rcmiku.ncmapi.model.MsgPrivateMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** 内层 JSON（私信 msg / 通知 notice 都是字符串 JSON）容错解析。 */
private val innerJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesScreen(
    navController: NavHostController,
    viewModel: MessagesScreenViewModel = hiltViewModel()
) {
    val comments by viewModel.comments.collectAsState()
    val forwards by viewModel.forwards.collectAsState()
    val notices by viewModel.notices.collectAsState()
    val privateMsgs by viewModel.privateMsgs.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    val titles = listOf(
        stringResource(R.string.msg_tab_comments),
        stringResource(R.string.msg_tab_mentions),
        stringResource(R.string.msg_tab_notices),
        stringResource(R.string.msg_tab_private)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.messages)) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                item {
                    SecondaryTabRow(selectedTabIndex = selectedTab) {
                        titles.forEachIndexed { index, title ->
                            Tab(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                text = { Text(title) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.size(8.dp))
                }

                when (selectedTab) {
                    0 -> {
                        if (comments.isEmpty()) {
                            item { MsgEmptyRow(stringResource(R.string.msg_empty_comments)) }
                        } else {
                            items(comments.size, key = { comments[it].commentId }) { index ->
                                MsgCommentRow(comments[index])
                            }
                        }
                    }
                    1 -> {
                        if (forwards.isEmpty()) {
                            item { MsgEmptyRow(stringResource(R.string.msg_empty_mentions)) }
                        } else {
                            items(forwards.size, key = { it }) { index ->
                                MsgForwardRow(forwards[index])
                            }
                        }
                    }
                    2 -> {
                        if (notices.isEmpty()) {
                            item { MsgEmptyRow(stringResource(R.string.msg_empty_notices)) }
                        } else {
                            items(notices.size, key = { notices[it].id }) { index ->
                                MsgNoticeRow(notices[index])
                            }
                        }
                    }
                    else -> {
                        if (privateMsgs.isEmpty()) {
                            item { MsgEmptyRow(stringResource(R.string.msg_empty_private)) }
                        } else {
                            items(privateMsgs.size, key = { it }) { index ->
                                MsgPrivateRow(
                                    msg = privateMsgs[index],
                                    onClickPlaylist = { playlistId ->
                                        navController.navigate(
                                            PlaylistNav(playlistId = playlistId, noCache = true)
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 评论：谁评论/回复了你的评论，引用被回复的内容。 */
@Composable
private fun MsgCommentRow(comment: MsgComment) {
    val nickname = comment.user?.nickname.orEmpty()
    val author = nickname.ifBlank { "未知用户" }
    val emptyText = stringResource(R.string.msg_no_content)
    val subtitle = comment.content.ifBlank { comment.beRepliedContent }.ifBlank { emptyText }

    MsgRow(
        avatarUrl = comment.user?.avatarUrl.orEmpty(),
        title = author,
        subtitle = subtitle,
        time = comment.time
    )
}

/** @我：字段 NCM 未完全公开，尽力从 msg JSON 提取文本，解析不到则只显示来源用户。 */
@Composable
private fun MsgForwardRow(forward: MsgForward) {
    val rawText = forward.msg?.let { el ->
        val obj = el as? JsonObject
        val m = (obj?.get("msg") as? JsonPrimitive)?.content
        val c = (obj?.get("content") as? JsonPrimitive)?.content
        if (!m.isNullOrBlank()) m else c
    }
    val nickname = forward.fromUser?.nickname.orEmpty()
    val title = nickname.ifBlank { stringResource(R.string.msg_mention_unknown) }
    val fallback = stringResource(R.string.msg_empty_mentions)
    val subtitle = rawText?.ifBlank { fallback } ?: fallback

    MsgRow(
        avatarUrl = forward.fromUser?.avatarUrl.orEmpty(),
        title = title,
        subtitle = subtitle,
        time = forward.time
    )
}

/** 系统通知：评论互动（type 6）展示被引用的评论内容，其余原样展示。 */
@Composable
private fun MsgNoticeRow(notice: MsgNotice) {
    val inner = notice.notice
        .takeIf { it.isNotBlank() }
        ?.let { runCatching { innerJson.decodeFromString<MsgNoticeInner>(it) }.getOrNull() }
    val innerUser = inner?.user
    val quotedUser = inner?.comment?.user
    val quoted = inner?.comment?.content

    val emptyText = stringResource(R.string.msg_no_content)
    val title: String = when {
        !innerUser?.nickname.isNullOrBlank() -> innerUser.nickname
        else -> stringResource(R.string.msg_system_notice)
    }
    val subtitle: String = when {
        inner?.type == 6 && !quoted.isNullOrBlank() ->
            stringResource(R.string.msg_quote_prefix, quoted)
        !quoted.isNullOrBlank() -> quoted
        else -> emptyText
    }

    MsgRow(
        avatarUrl = quotedUser?.avatarUrl.orEmpty(),
        title = title,
        subtitle = subtitle,
        time = notice.time
    )
}

/** 私信：小秘书对话；内层 JSON 携带歌单时可点击跳转。 */
@Composable
private fun MsgPrivateRow(
    msg: MsgPrivateMessage,
    onClickPlaylist: (Long) -> Unit
) {
    val inner = msg.msg
        .takeIf { it.isNotBlank() }
        ?.let { runCatching { innerJson.decodeFromString<MsgPrivateInner>(it) }.getOrNull() }
    val playlistId = inner?.playlist?.id?.takeIf { it > 0 }
    val coverUrl = (inner?.song?.picUrl.orEmpty()).ifBlank {
        inner?.playlist?.coverImgUrl.orEmpty()
    }
    // 正文优先取内层 JSON 的 msg 字段；解析不到（如纯文本私信）则回退原始文本。
    val body = inner?.msg.orEmpty().ifBlank { msg.msg }
    val nickname = msg.fromUser?.nickname.orEmpty()
    val from = nickname.ifBlank { stringResource(R.string.msg_secretary) }
    val emptyText = stringResource(R.string.msg_no_content)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = playlistId != null) { onClickPlaylist(playlistId!!) }
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(ListThumbnailSize)
                .clip(RoundedCornerShape(ThumbnailCornerRadius))
        ) {
            if (coverUrl.isNotBlank()) {
                AsyncImage(
                    model = coverUrl.toCoverImageUrl(CoverImageSize.LIST),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(ThumbnailCornerRadius))
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp, top = 2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = from,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = body.ifBlank { emptyText },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = formatTimestamp(msg.time),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 通用消息行：头像 + 标题 + 副文本 + 时间。 */
@Composable
private fun MsgRow(
    avatarUrl: String,
    title: String,
    subtitle: String,
    time: Long
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(ListThumbnailSize)
                .clip(RoundedCornerShape(ThumbnailCornerRadius))
        ) {
            if (avatarUrl.isNotBlank()) {
                AsyncImage(
                    model = avatarUrl.toCoverImageUrl(CoverImageSize.LIST),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(ThumbnailCornerRadius))
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = formatTimestamp(time),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MsgEmptyRow(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        textAlign = TextAlign.Center
    )
}
