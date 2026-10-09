package com.jussicodes.music.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaMetadata
import coil3.compose.AsyncImage
import com.jussicodes.music.LocalPlayerState
import com.jussicodes.music.ui.icons.ChevronDown
import com.jussicodes.music.ui.icons.Delete
import com.jussicodes.music.ui.icons.Favorite
import com.jussicodes.music.ui.icons.FavoriteFill
import com.jussicodes.music.ui.icons.FilterList
import com.rcmiku.ncmapi.api.account.AccountApi
import com.rcmiku.ncmapi.api.comment.CommentApi
import com.rcmiku.ncmapi.model.Comment
import com.rcmiku.ncmapi.model.CommentNewData
import com.rcmiku.ncmapi.model.CommentUser
import com.rcmiku.ncmapi.model.UserProfile
import com.rcmiku.ncmapi.utils.CookieProvider
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

private data class CommentSortOption(
    val title: String,
    val type: Int
)

private val commentSortOptions = listOf(
    CommentSortOption("推荐", 1),
    CommentSortOption("热度", 2),
    CommentSortOption("时间", 3)
)

/**
 * 播放页半屏评论面板（M3 ModalBottomSheet，和播放页右上角菜单 PlayerMenuBottomSheet 同款）。
 * 半屏→上滑全屏两态由 M3 grab handle 内建；ModalBottomSheet 内建处理键盘 insets，
 * 键盘弹出时 sheet 收缩、底部输入条贴键盘上沿可见（半屏 / 全屏 / 键盘弹出三态均可见）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerComments(
    mediaId: Long?,
    mediaMetadata: MediaMetadata,
    commentType: Int = 0,
    modifier: Modifier = Modifier,
    onBackPressed: () -> Unit = {}
) {
    val resolvedMediaId = mediaId ?: LocalPlayerState.current?.currentMediaItem?.mediaId?.toLongOrNull()
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var selectedSort by remember { mutableStateOf(commentSortOptions.first()) }
    var commentData by remember(resolvedMediaId) { mutableStateOf<CommentNewData?>(null) }
    var comments by remember(resolvedMediaId, selectedSort.type) { mutableStateOf(emptyList<Comment>()) }
    var pageNo by remember(resolvedMediaId, selectedSort.type) { mutableIntStateOf(1) }
    var cursor by remember(resolvedMediaId, selectedSort.type) { mutableStateOf("") }
    var hasMore by remember(resolvedMediaId, selectedSort.type) { mutableStateOf(false) }
    var isLoading by remember(resolvedMediaId) { mutableStateOf(false) }
    var isLoadingMore by remember(resolvedMediaId, selectedSort.type) { mutableStateOf(false) }
    var errorMessage by remember(resolvedMediaId) { mutableStateOf<String?>(null) }

    // 和现有评论页绿包（9b45cf6）外壳一致：M3 ModalBottomSheet + 半屏/全屏两态
    //（skipPartiallyExpanded=false）。打开后落在半屏,上滑到全屏,下滑收起。
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    LaunchedEffect(sheetState) {
        if (!sheetState.isVisible) {
            sheetState.show()
        }
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()

    // 发/回复/删除评论所需的身份与输入状态（登录态才渲染输入条）。
    val isLoggedIn = remember { CookieProvider.isLoggedIn() }
    var currentUid by remember { mutableStateOf<Long?>(null) }
    var currentProfile by remember { mutableStateOf<UserProfile?>(null) }
    var draft by remember(resolvedMediaId) { mutableStateOf("") }
    var replyTarget by remember(resolvedMediaId) { mutableStateOf<Comment?>(null) }
    var deleteTarget by remember(resolvedMediaId) { mutableStateOf<Comment?>(null) }
    var isSending by remember { mutableStateOf(false) }

    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            AccountApi.account().getOrNull()?.let { info ->
                currentUid = info.account?.profile?.userId
                currentProfile = info.account?.profile
            }
        }
    }

    fun loadMoreComments() {
        val songId = resolvedMediaId ?: return
        if (isLoading || isLoadingMore || !hasMore) return
        scope.launch {
            isLoadingMore = true
            val nextPage = pageNo + 1
            CommentApi.newComments(
                id = songId,
                type = commentType,
                sortType = selectedSort.type,
                pageSize = 20,
                pageNo = nextPage,
                cursor = cursor.takeIf { it.isNotBlank() }
            ).onSuccess {
                commentData = it.data
                comments = comments + it.data.comments
                pageNo = nextPage
                hasMore = it.data.hasMore
                cursor = (it.data.cursor as? JsonPrimitive)?.content?.takeIf { c -> c.isNotBlank() }
                    ?: it.data.comments.lastOrNull()?.time?.toString()
                    ?: cursor
            }.onFailure {
                Toast.makeText(context, it.message ?: "加载更多评论失败", Toast.LENGTH_SHORT).show()
            }
            isLoadingMore = false
        }
    }

    // 发送 / 回复：提交到 NCM，成功后把新评论插到列表顶部 + 清空输入。
    fun submitComment() {
        val songId = resolvedMediaId ?: return
        val content = draft.trim()
        if (content.isEmpty() || isSending) return
        val target = replyTarget
        isSending = true
        scope.launch {
            val result = CommentApi.postComment(
                id = songId,
                type = commentType,
                content = content,
                commentId = target?.commentId
            )
            isSending = false
            result
                .onSuccess {
                    val fresh = Comment(
                        commentId = -System.nanoTime(),
                        content = content,
                        user = CommentUser(
                            userId = currentUid ?: 0L,
                            nickname = currentProfile?.nickname.orEmpty().ifBlank { "我" },
                            avatarUrl = currentProfile?.avatarUrl.orEmpty()
                        )
                    )
                    comments = listOf(fresh) + comments
                    draft = ""
                    replyTarget = null
                    Toast.makeText(context, if (target == null) "评论已发布" else "回复已发布", Toast.LENGTH_SHORT).show()
                }
                .onFailure {
                    Toast.makeText(context, it.message ?: "发送评论失败", Toast.LENGTH_SHORT).show()
                }
        }
    }

    // 删除：仅本人主评论可删；确认后从列表移除。
    fun deleteComment(cid: Long) {
        val songId = resolvedMediaId ?: return
        scope.launch {
            val result = CommentApi.deleteComment(id = songId, type = commentType, cid = cid)
            result
                .onSuccess {
                    comments = comments.filter { it.commentId != cid }
                    Toast.makeText(context, "评论已删除", Toast.LENGTH_SHORT).show()
                }
                .onFailure {
                    Toast.makeText(context, it.message ?: "删除评论失败", Toast.LENGTH_SHORT).show()
                }
        }
    }

    LaunchedEffect(resolvedMediaId, selectedSort.type) {
        val songId = resolvedMediaId ?: return@LaunchedEffect
        isLoading = true
        errorMessage = null
        comments = emptyList()
        pageNo = 1
        cursor = ""
        hasMore = false
        CommentApi.newComments(
            id = songId,
            type = commentType,
            sortType = selectedSort.type,
            pageSize = 20,
            pageNo = 1
        ).onSuccess {
            commentData = it.data
            comments = it.data.comments
            hasMore = it.data.hasMore
            cursor = (it.data.cursor as? JsonPrimitive)?.content?.takeIf { c -> c.isNotBlank() }
                ?: it.data.comments.lastOrNull()?.time?.toString()
                ?: ""
        }.onFailure {
            errorMessage = it.message ?: "评论加载失败"
        }
        isLoading = false
    }

    LaunchedEffect(lazyListState, comments.size, hasMore, isLoading, isLoadingMore) {
        snapshotFlow {
            val layoutInfo = lazyListState.layoutInfo
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            val totalItemsCount = layoutInfo.totalItemsCount
            totalItemsCount > 0 && lastVisibleIndex >= totalItemsCount - 4
        }
            .distinctUntilChanged()
            .filter { it }
            .collect {
                loadMoreComments()
            }
    }

    ModalBottomSheet(
        onDismissRequest = onBackPressed,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {

                    // 标题行（封面 + 标题 + 排序）
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 8.dp, bottom = 12.dp)
                    ) {
                        AsyncImage(
                            model = mediaMetadata.artworkUri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(MaterialTheme.shapes.small)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "评论",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = mediaMetadata.title?.toString().orEmpty(),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.basicMarquee()
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            commentData?.totalCount?.takeIf { it > 0 }?.let {
                                Text(
                                    text = it.toString(),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(
                                text = selectedSort.title,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.clickable { sortMenuExpanded = true }
                            )
                            IconButton(onClick = { sortMenuExpanded = true }) {
                                Icon(
                                    imageVector = FilterList,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            DropdownMenu(
                                expanded = sortMenuExpanded,
                                onDismissRequest = { sortMenuExpanded = false }
                            ) {
                                commentSortOptions.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option.title) },
                                        onClick = {
                                            selectedSort = option
                                            sortMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = DividerDefaults.color.copy(alpha = 0.4f))

                    // 列表区：占中间弹性空间，底部固定输入条不被挤掉。
                    Column(modifier = Modifier.weight(1f)) {
                        when {
                            isLoading -> {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator()
                                }
                            }

                            errorMessage != null -> {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = errorMessage.orEmpty(),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            comments.isEmpty() -> {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "暂无评论",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            else -> {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    state = lazyListState,
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                    contentPadding = WindowInsets.navigationBars.asPaddingValues()
                                ) {
                                    items(
                                        items = comments,
                                        key = { it.commentId }
                                    ) { comment ->
                                        CommentItem(
                                            resourceId = resolvedMediaId,
                                            resourceType = commentType,
                                            comment = comment,
                                            currentUid = currentUid,
                                            onReply = { replyTarget = it },
                                            onDelete = { deleteTarget = it },
                                            onToast = { message ->
                                                Toast.makeText(
                                                    context,
                                                    message ?: "操作失败",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        )
                                    }
                                    if (hasMore || isLoadingMore) {
                                        item(key = "comment-loading-more") {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 16.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (isLoadingMore) {
                                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                                } else {
                                                    Text(
                                                        text = "上滑加载更多",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    CommentInputBar(
                        draft = draft,
                        replyTarget = replyTarget,
                        isLoggedIn = isLoggedIn,
                        isSending = isSending,
                        onDraftChange = { draft = it },
                        onCancelReply = { replyTarget = null },
                        onSend = { submitComment() }
                    )

                    deleteTarget?.let { target ->
                        CommentDeleteDialog(
                            comment = target,
                            onConfirm = {
                                deleteComment(target.commentId)
                                deleteTarget = null
                            },
                            onDismiss = { deleteTarget = null }
                        )
                    }
                }
            }
}
/**
 * 主评论条目：头像 + 昵称 + 归属地(省)·日期 + 内容 + 点赞(右上) + 「查看 N 条回复」
 * 折叠入口(点按才拉楼中楼) + 本人删除。头像保持方头(需求：不改圆头)。
 */
@Composable
private fun CommentItem(
    resourceId: Long?,
    resourceType: Int,
    comment: Comment,
    currentUid: Long?,
    onReply: (Comment) -> Unit,
    onDelete: (Comment) -> Unit,
    onToast: (String?) -> Unit
) {
    val scope = rememberCoroutineScope()
    var liked by remember(comment.commentId) { mutableStateOf(comment.liked) }
    var likedCount by remember(comment.commentId) { mutableLongStateOf(comment.likedCount) }

    // 楼中楼：点「查看回复」才拉；状态全部本地。
    var floorExpanded by remember(comment.commentId) { mutableStateOf(false) }
    var floors by remember(comment.commentId) { mutableStateOf(emptyList<Comment>()) }
    var floorHasMore by remember(comment.commentId) { mutableStateOf(false) }
    var floorLoading by remember(comment.commentId) { mutableStateOf(false) }
    var floorTime by remember(comment.commentId) { mutableLongStateOf(-1L) }
    var floorError by remember(comment.commentId) { mutableStateOf<String?>(null) }
    var floorDeleteTarget by remember(comment.commentId) { mutableStateOf<Comment?>(null) }
    // 顶层捕获 context（LocalContext 只能在 Composable 作用域取），供 deleteFloor 的协程用。
    val context = LocalContext.current

    fun loadFloors() {
        val songId = resourceId ?: return
        if (floorLoading) return
        scope.launch {
            floorLoading = true
            floorError = null
            CommentApi.floorComments(
                id = songId,
                parentCommentId = comment.commentId,
                type = resourceType,
                time = floorTime
            ).onSuccess {
                val data = it.data
                floors = floors + data.comments
                floorHasMore = data.hasMore
                floorTime = if (data.time > 0L) data.time else (floors.lastOrNull()?.time ?: -1L)
            }.onFailure {
                floorError = it.message ?: "楼层加载失败"
            }
            floorLoading = false
        }
    }

    fun deleteFloor(cid: Long) {
        val songId = resourceId ?: return
        scope.launch {
            CommentApi.deleteComment(id = songId, type = resourceType, cid = cid)
                .onSuccess {
                    floors = floors.filter { f -> f.commentId != cid }
                    Toast.makeText(
                        context,
                        "回复已删除",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                .onFailure {
                    onToast(it.message ?: "删除回复失败")
                }
        }
    }

    val isOwn = currentUid != null && comment.user.userId == currentUid
    val location = comment.ipLocation?.location.orEmpty().takeIf { it.isNotBlank() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        AsyncImage(
            model = comment.user.avatarUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(36.dp)
                .clip(MaterialTheme.shapes.small)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            // 昵称 + 点赞(右上)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = comment.user.nickname,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        val songId = resourceId ?: return@IconButton
                        val targetLiked = !liked
                        liked = targetLiked
                        likedCount = (likedCount + if (targetLiked) 1 else -1).coerceAtLeast(0)
                        scope.launch {
                            CommentApi.likeComment(
                                id = songId,
                                cid = comment.commentId,
                                like = targetLiked,
                                type = resourceType
                            ).onFailure {
                                liked = !targetLiked
                                likedCount = (likedCount + if (targetLiked) -1 else 1).coerceAtLeast(0)
                                onToast(it.message)
                            }
                        }
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (liked) FavoriteFill else Favorite,
                        contentDescription = null,
                        tint = if (liked) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(18.dp)
                    )
                }
                if (likedCount > 0) {
                    Text(
                        text = likedCount.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            // 归属地 · 日期（都为空则整行不显示）
            val date = comment.timeStr
            if (location != null || date.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    location?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (location != null && date.isNotBlank()) Text(" · ")
                    if (date.isNotBlank()) {
                        Text(
                            text = date,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = comment.content,
                style = MaterialTheme.typography.bodyMedium
            )

            // 操作行：回复 + 本人删除
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "回复",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clickable { onReply(comment) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
                if (isOwn) {
                    Spacer(Modifier.width(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable { onDelete(comment) }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "删除",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // 楼中楼折叠入口（replyCount>0 才显示；点按才拉数据）
            if (comment.replyCount > 0) {
                Spacer(Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable {
                            if (!floorExpanded) {
                                floorExpanded = true
                                loadFloors()
                            } else {
                                floorExpanded = false
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = ChevronDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (floorExpanded) "收起回复" else "查看 ${comment.replyCount} 条回复",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // 楼层回复区（按需展开）
                if (floorExpanded) {
                    Column(
                        modifier = Modifier
                            .padding(top = 6.dp, start = 8.dp)
                            .background(
                                Color.Black.copy(alpha = 0.06f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(start = 8.dp, top = 4.dp)
                    ) {
                        if (floorError != null) {
                            Text(
                                text = floorError.orEmpty(),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(8.dp)
                            )
                        } else {
                            floors.forEach { floor ->
                                FloorReplyItem(
                                    resourceId = resourceId,
                                    resourceType = resourceType,
                                    floor = floor,
                                    currentUid = currentUid,
                                    onReply = { onReply(it) },
                                    onDelete = { floorDeleteTarget = it },
                                    onToast = { onToast(it) }
                                )
                            }
                        }
                        if (floorLoading) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "加载中…",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else if (floorHasMore && floors.isNotEmpty()) {
                            Text(
                                text = "点击加载更多回复",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .clickable { loadFloors() }
                                    .padding(8.dp)
                            )
                        }
                    }

                    floorDeleteTarget?.let { target ->
                        CommentDeleteDialog(
                            comment = target,
                            onConfirm = {
                                deleteFloor(target.commentId)
                                floorDeleteTarget = null
                            },
                            onDismiss = { floorDeleteTarget = null }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 楼中楼条目：昵称 + 归属地·日期 + 内容 + 点赞 + 本人删除 + 回复。
 * 删除仅当楼层作者 == 当前 uid 才显示（避免误删别人）。
 */
@Composable
private fun FloorReplyItem(
    resourceId: Long?,
    resourceType: Int,
    floor: Comment,
    currentUid: Long?,
    onReply: (Comment) -> Unit,
    onDelete: (Comment) -> Unit,
    onToast: (String?) -> Unit
) {
    val scope = rememberCoroutineScope()
    var liked by remember(floor.commentId) { mutableStateOf(floor.liked) }
    var likedCount by remember(floor.commentId) { mutableLongStateOf(floor.likedCount) }

    val isOwn = currentUid != null && floor.user.userId == currentUid
    val location = floor.ipLocation?.location.orEmpty().takeIf { it.isNotBlank() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        AsyncImage(
            model = floor.user.avatarUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(28.dp)
                .clip(MaterialTheme.shapes.small)
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = floor.user.nickname,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        val songId = resourceId ?: return@IconButton
                        val targetLiked = !liked
                        liked = targetLiked
                        likedCount = (likedCount + if (targetLiked) 1 else -1).coerceAtLeast(0)
                        scope.launch {
                            CommentApi.likeComment(
                                id = songId,
                                cid = floor.commentId,
                                like = targetLiked,
                                type = resourceType
                            ).onFailure {
                                liked = !targetLiked
                                likedCount = (likedCount + if (targetLiked) -1 else 1).coerceAtLeast(0)
                                onToast(it.message)
                            }
                        }
                    },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = if (liked) FavoriteFill else Favorite,
                        contentDescription = null,
                        tint = if (liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
                if (likedCount > 0) {
                    Text(
                        text = likedCount.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            val date = floor.timeStr
            if (location != null || date.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    location?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (location != null && date.isNotBlank()) Text(" · ")
                    if (date.isNotBlank()) {
                        Text(
                            text = date,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = floor.content,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "回复",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clickable { onReply(floor) }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
                if (isOwn) {
                    Spacer(Modifier.width(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable { onDelete(floor) }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = "删除",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

/**
 * 底部输入条：登录态才渲染。左侧输入框 + 发送按钮；回复态时上方挂「回复 @昵称 ×」胶囊。
 * 未登录点发送 → toast 提示登录。位于 ModalBottomSheet 内容底部，键盘弹出时贴键盘上沿。
 */
@Composable
private fun CommentInputBar(
    draft: String,
    replyTarget: Comment?,
    isLoggedIn: Boolean,
    isSending: Boolean,
    onDraftChange: (String) -> Unit,
    onCancelReply: () -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        replyTarget?.let { target ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "回复 @${target.user.nickname}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onCancelReply, modifier = Modifier.size(24.dp)) {
                    Text(
                        text = "×",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        text = if (isLoggedIn) "发一条评论…" else "登录后发表评论",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                singleLine = false,
                maxLines = 3,
                enabled = isLoggedIn,
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
            Spacer(Modifier.width(8.dp))
            TextButton(
                onClick = {
                    if (isLoggedIn) onSend()
                    else Toast.makeText(context, "请先登录后再发表评论", Toast.LENGTH_SHORT).show()
                },
                enabled = isLoggedIn && draft.isNotBlank() && !isSending
            ) {
                Text(text = if (replyTarget != null) "回复" else "发送")
            }
        }
    }
}

/**
 * 删除确认 Dialog（同包复用 Dialog 组件；仅本人评论/回复调用）。
 */
@Composable
private fun CommentDeleteDialog(
    comment: Comment,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        onConfirmation = onConfirm,
        dialogTitle = "删除评论",
        dialogText = "确定删除这条评论吗？"
    )
}
