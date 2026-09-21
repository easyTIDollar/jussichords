package com.jussicodes.music.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import com.jussicodes.music.R
import com.jussicodes.music.constants.ListThumbnailSize
import com.jussicodes.music.constants.ThumbnailCornerRadius
import com.jussicodes.music.data.MsgRecentContact
import com.jussicodes.music.ui.navigation.PrivateChatNav
import com.jussicodes.music.utils.CoverImageSize
import com.jussicodes.music.utils.formatTimestamp
import com.jussicodes.music.utils.toCoverImageUrl
import com.jussicodes.music.viewModel.MessagesScreenViewModel
import com.rcmiku.ncmapi.model.MsgComment
import com.rcmiku.ncmapi.model.MsgForward
import com.rcmiku.ncmapi.model.MsgNotice
import com.rcmiku.ncmapi.model.MsgNoticeInner
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
    val contacts by viewModel.contacts.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    val titles = listOf(
        stringResource(R.string.msg_tab_private),
        stringResource(R.string.msg_tab_comments),
        stringResource(R.string.msg_tab_mentions),
        stringResource(R.string.msg_tab_notices)
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
                        if (contacts.isEmpty()) {
                            item { MsgEmptyRow(stringResource(R.string.msg_empty_contacts)) }
                        } else {
                            items(contacts.size, key = { contacts[it].userId }) { index ->
                                MsgContactRow(
                                    contact = contacts[index],
                                    onClick = {
                                        navController.navigate(
                                            PrivateChatNav(
                                                userId = contacts[index].userId,
                                                nickname = contacts[index].nickname,
                                                avatarUrl = contacts[index].avatarUrl
                                            )
                                        )
                                    }
                                )
                            }
                        }
                    }
                    1 -> {
                        if (comments.isEmpty()) {
                            item { MsgEmptyRow(stringResource(R.string.msg_empty_comments)) }
                        } else {
                            items(comments.size, key = { comments[it].commentId }) { index ->
                                MsgCommentRow(comments[index])
                            }
                        }
                    }
                    2 -> {
                        if (forwards.isEmpty()) {
                            item { MsgEmptyRow(stringResource(R.string.msg_empty_mentions)) }
                        } else {
                            items(forwards.size, key = { it }) { index ->
                                MsgForwardRow(forwards[index])
                            }
                        }
                    }
                    3 -> {
                        if (notices.isEmpty()) {
                            item { MsgEmptyRow(stringResource(R.string.msg_empty_notices)) }
                        } else {
                            items(notices.size, key = { notices[it].id }) { index ->
                                MsgNoticeRow(notices[index])
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

/** 私信联系人：头像 + 昵称 + VIP 标记；头像右下角互关角标、左下角在线点。 */
@Composable
private fun MsgContactRow(
    contact: MsgRecentContact,
    onClick: () -> Unit
) {
    val isVip = contact.vipType != 0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(ListThumbnailSize)
                .clip(RoundedCornerShape(ThumbnailCornerRadius))
        ) {
            if (contact.avatarUrl.isNotBlank()) {
                AsyncImage(
                    model = contact.avatarUrl.toCoverImageUrl(CoverImageSize.LIST),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(ThumbnailCornerRadius))
                )
            }
            // 互关角标（右下角）
            if (contact.mutual) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .align(Alignment.BottomEnd)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.msg_contact_mutual),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 10.sp
                    )
                }
            }
            // 在线点（左下角）
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .align(Alignment.BottomStart)
                    .clip(CircleShape)
                    .background(if (contact.onlined) Color(0xFF4CAF50) else Color.Gray)
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = contact.nickname,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // VIP 标记：头像下方（昵称行下方）
            if (isVip) {
                Text(
                    text = stringResource(R.string.msg_contact_vip),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                )
            }
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
