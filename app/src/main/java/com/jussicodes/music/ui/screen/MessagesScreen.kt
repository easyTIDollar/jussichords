package com.jussicodes.music.ui.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
import com.jussicodes.music.data.MsgSessionCache
import com.jussicodes.music.ui.navigation.PrivateChatNav
import com.jussicodes.music.utils.CoverImageSize
import com.jussicodes.music.utils.formatTimestamp
import com.jussicodes.music.utils.toCoverImageUrl
import com.jussicodes.music.viewModel.MessagesScreenViewModel
import com.jussicodes.music.ui.icons.DragHandle
import com.jussicodes.music.ui.icons.Funnel
import com.rcmiku.ncmapi.model.MsgComment
import com.rcmiku.ncmapi.model.MsgForward
import com.rcmiku.ncmapi.model.MsgNotice
import com.rcmiku.ncmapi.model.MsgNoticeInner
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

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
    // 会话列表读缓存（单一数据源）：loadMore/refresh/markRead/拖拽/删除都回写缓存，
    // 进聊天页本地已读后角标自动消；viewModel 仅负责 loading / hasMore / loadMore。
    val cacheSessions by MsgSessionCache.items.collectAsState()
    val sessions = cacheSessions.orEmpty()
    val sessionsLoading by viewModel.sessionsLoading.collectAsState()
    val sessionsHasMore by viewModel.sessionsHasMore.collectAsState()
    val sessionsMoreFailed by viewModel.sessionsMoreFailed.collectAsState()
    val commentsLoading by viewModel.commentsLoading.collectAsState()
    val forwardsLoading by viewModel.forwardsLoading.collectAsState()
    val noticesLoading by viewModel.noticesLoading.collectAsState()
    // 顶栏菜单状态（持久化在缓存：重启生效）
    val manualOrderActive by MsgSessionCache.manualOrderActive.collectAsState()
    val deletedUids by MsgSessionCache.deletedUids.collectAsState()
    val filterUserTypes by MsgSessionCache.filterUserTypes.collectAsState()
    var deleteTarget by remember { mutableStateOf<MsgSessionCache.Item?>(null) }

    val listState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        // key = userId（Long）；缓存层按当前展示序移动并只改内存，松手再落盘
        (from.key as? Long)?.let { f -> (to.key as? Long)?.let { t ->
            if (f != t) MsgSessionCache.moveItem(f, t)
        } }
    }
    // 拖拽松手 → 落盘（applyManualOrderLive 只改内存，flush 才写 DataStore）
    val dragging = reorderableState.isAnyItemDragging
    LaunchedEffect(dragging) {
        if (!dragging) MsgSessionCache.flushManualOrder()
    }

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
                },
                actions = {
                    if (selectedTab == 0) {
                        SessionsFilterMenu(
                            active = filterUserTypes != null,
                            activeTypes = filterUserTypes,
                            onSet = { MsgSessionCache.setFilterUserTypes(it) }
                        )
                        SessionsTopMenu(
                            manualOrderActive = manualOrderActive,
                            hasDeleted = deletedUids.isNotEmpty(),
                            onResetOrder = { MsgSessionCache.resetManualOrder() },
                            onRestoreAllDeleted = { MsgSessionCache.restoreAllDeleted() }
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            state = listState,
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
                    // 冷启动：磁盘快照已有数据时直接铺列表（后台静默刷新），只有"无数据且加载中"才转圈
                    if (sessionsLoading && sessions.isEmpty()) {
                        item { MsgLoadingRow() }
                    } else if (sessions.isEmpty()) {
                        item { MsgEmptyRow(stringResource(R.string.msg_empty_sessions)) }
                    } else {
                        // 私信会话：全量（不过滤 userType）− 本地已删除 + 手动序；
                        // 拖拽手柄排序（reorderable 库，长按 DragHandle 起拖）、长按弹删除确认、
                        // 接近底部（倒数第 4 行）自动补拉下一页、仅加载失败时显"点击重试"尾行。
                        itemsIndexed(
                            sessions,
                            key = { _, item -> item.user.userId }
                        ) { index, item ->
                            val autoLoadMore = index >= sessions.size - 4
                            ReorderableItem(
                                state = reorderableState,
                                key = item.user.userId,
                            ) { isDragging ->
                                MsgSessionRow(
                                    session = item,
                                    isDragging = isDragging,
                                    autoLoadMore = autoLoadMore,
                                    onLoadMore = { viewModel.loadMoreSessions() },
                                    onLongClick = { deleteTarget = item },
                                    onClick = {
                                        navController.navigate(
                                            PrivateChatNav(
                                                userId = item.user.userId,
                                                nickname = item.user.nickname,
                                                avatarUrl = item.user.avatarUrl
                                            )
                                        )
                                    },
                                    handleModifier = Modifier.longPressDraggableHandle()
                                )
                            }
                        }
                        if (sessionsMoreFailed) {
                            item { MsgRetryRow(onClick = { viewModel.loadMoreSessions() }) }
                        }
                    }
                }
                1 -> {
                    if (commentsLoading) {
                        item { MsgLoadingRow() }
                    } else if (comments.isEmpty()) {
                        item { MsgEmptyRow(stringResource(R.string.msg_empty_comments)) }
                    } else {
                        items(comments.size, key = { comments[it].commentId }) { index ->
                            MsgCommentRow(comments[index])
                        }
                    }
                }
                2 -> {
                    if (forwardsLoading) {
                        item { MsgLoadingRow() }
                    } else if (forwards.isEmpty()) {
                        item { MsgEmptyRow(stringResource(R.string.msg_empty_mentions)) }
                    } else {
                        items(forwards.size, key = { it }) { index ->
                            MsgForwardRow(forwards[index])
                        }
                    }
                }
                3 -> {
                    if (noticesLoading) {
                        item { MsgLoadingRow() }
                    } else if (notices.isEmpty()) {
                        item { MsgEmptyRow(stringResource(R.string.msg_empty_notices)) }
                    } else {
                        items(notices.size, key = { notices[it].id }) { index ->
                            MsgNoticeRow(notices[index])
                        }
                    }
                }
            }
        }

        // 长按会话行 → 删除确认框（本地隐藏该会话，持久化；顶栏 ⋮ 可一键恢复）
        deleteTarget?.let { target ->
            AlertDialog(
                onDismissRequest = { deleteTarget = null },
                title = { Text(stringResource(R.string.msg_session_delete_title)) },
                text = { Text(stringResource(R.string.msg_session_delete_text, target.user.nickname)) },
                confirmButton = {
                    TextButton(onClick = {
                        MsgSessionCache.deleteSession(target.user.userId)
                        deleteTarget = null
                    }) {
                        Text(stringResource(R.string.confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deleteTarget = null }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }
    }
}

/** 单个区块尚未加载完成时，列表位转圈（不挡住 tab 切换）。 */
@Composable
private fun MsgLoadingRow() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(Modifier.size(28.dp))
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

/** 私信会话行：头像（右下角在线点，绿在线/灰离线）；首行昵称；次行 [VIP]/[互关] + 预览；
 * 右侧定宽列放未读角标 + 时间（严格右对齐成列）；grip 手柄长按拖拽排序（松手落盘）。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MsgSessionRow(
    session: MsgSessionCache.Item,
    isDragging: Boolean,
    autoLoadMore: Boolean,
    handleModifier: Modifier,
    onLoadMore: () -> Unit,
    onLongClick: () -> Unit,
    onClick: () -> Unit
) {
    val other = session.user

    // 接近底部（倒数第 4 行进入视口）自动补拉下一页（loadMoreSessions 内部防抖/去重）
    LaunchedEffect(autoLoadMore) {
        if (autoLoadMore) onLoadMore()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isDragging) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent
            )
            .clip(RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 行体（头像 + 昵称/预览）= 单击进聊天、长按删除确认；
        // 拖拽手柄在外层单独节点，两个手势域互不嵌套，长按手柄起拖不再误弹删除框
        Row(
            modifier = Modifier
                .weight(1f)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(ListThumbnailSize)) {
                AsyncImage(
                    model = other.avatarUrl.toCoverImageUrl(CoverImageSize.LIST),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(ThumbnailCornerRadius))
                )
                // 在线状态点：头像右下角（绿 = 在线，灰 = 离线）
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .align(Alignment.BottomEnd)
                        .clip(CircleShape)
                        .background(if (session.onlined) Color(0xFF4CAF50) else Color(0xFF9E9E9E))
                        .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape)
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = other.nickname,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // 次行：[VIP] [互关] 标签 + 预览（标签统一从次行行首开始，不受昵称长度影响）
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val hasTags = session.vipType != 0 || session.mutual
                    if (session.vipType != 0) MsgStateTag(stringResource(R.string.msg_contact_vip))
                    if (session.mutual) MsgStateTag(stringResource(R.string.msg_contact_mutual))
                    if (hasTags) Spacer(Modifier.width(4.dp))
                    Text(
                        text = session.preview,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
            }
        }
        // 拖拽手柄：长按 grip 起拖排序（行体长按 = 删除确认，两节点无嵌套零冲突）
        Icon(
            imageVector = DragHandle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = handleModifier
                .size(20.dp)
                .padding(start = 4.dp)
        )
        // 右侧定宽列：未读角标 + 时间戳（End 对齐，所有行严格成一条竖线）
        Column(
            modifier = Modifier
                .width(64.dp)
                .padding(start = 4.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (session.newMsgCount > 0) {
                Text(
                    text = if (session.newMsgCount > 99) "99+" else session.newMsgCount.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error)
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                )
            }
            Text(
                text = session.displayTime,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** 小状态标签（VIP / 互关）：灰底圆角，次行行首并排。 */
@Composable
private fun MsgStateTag(text: String) {
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

/** 顶栏筛选（仅私信 tab）：按 userType 多选保留。
 * activeTypes = null 表示未筛选（全部勾选）；选项与计数来自缓存全量类型分布。 */
@Composable
private fun SessionsFilterMenu(
    active: Boolean,
    activeTypes: Set<Int>?,
    onSet: (Set<Int>?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val counts by MsgSessionCache.typeCounts.collectAsState()
    val allChecked = activeTypes == null
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Funnel,
                contentDescription = null,
                tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            // 首行"显示全部"：取消筛选
            DropdownMenuItem(
                text = { Text("显示全部类型") },
                leadingIcon = {
                    FilterCheckIcon(checked = allChecked)
                },
                onClick = { onSet(null); expanded = false }
            )
            counts.entries.sortedBy { it.key }.forEach { (type, count) ->
                val label = USER_TYPE_LABELS[type] ?: "类型$type"
                val checked = allChecked || (activeTypes?.contains(type) ?: false)
                DropdownMenuItem(
                    text = { Text("$label（$count）") },
                    leadingIcon = { FilterCheckIcon(checked = checked) },
                    onClick = {
                        val base = if (allChecked) counts.keys.toMutableSet() else activeTypes!!.toMutableSet()
                        if (type in base) base.remove(type) else base.add(type)
                        onSet(if (base.size == counts.size) null else base)
                        expanded = false
                    }
                )
            }
        }
    }
}

/** 筛选菜单勾选图标：勾选 = 主色，未勾选 = 灰色。 */
@Composable
private fun FilterCheckIcon(checked: Boolean) {
    Icon(
        imageVector = Icons.Filled.Check,
        contentDescription = null,
        tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** userType → 展示名（NCM 未公开完整枚举，已知码给友好名，未知码兜底"类型N"）。 */
private val USER_TYPE_LABELS = mapOf(
    0 to "普通用户",
    4 to "歌手",
    10 to "官方",
    207 to "音乐达人"
)

/** 顶栏 ⋮ 菜单（仅私信 tab）：手动排序生效时可"恢复按时间排序"；有被删会话时可一键恢复。 */
@Composable
private fun SessionsTopMenu(
    manualOrderActive: Boolean,
    hasDeleted: Boolean,
    onResetOrder: () -> Unit,
    onRestoreAllDeleted: () -> Unit
) {
    if (!manualOrderActive && !hasDeleted) return
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Outlined.MoreVert, contentDescription = null)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            if (manualOrderActive) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.msg_menu_reset_order)) },
                    onClick = { onResetOrder(); expanded = false }
                )
            }
            if (hasDeleted) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.msg_menu_restore_deleted)) },
                    onClick = { onRestoreAllDeleted(); expanded = false }
                )
            }
        }
    }
}

/** 分页加载失败尾行（无缝加载：正常时不显示任何尾行，仅失败时给"点击重试"）。 */
@Composable
private fun MsgRetryRow(onClick: () -> Unit) {
    Text(
        text = stringResource(R.string.msg_more_load_failed),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        textAlign = TextAlign.Center
    )
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
