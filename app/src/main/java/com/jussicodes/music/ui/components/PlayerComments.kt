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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaMetadata
import coil3.compose.AsyncImage
import com.jussicodes.music.LocalPlayerState
import com.jussicodes.music.ui.icons.Delete
import com.jussicodes.music.ui.icons.Favorite
import com.jussicodes.music.ui.icons.FavoriteFill
import com.jussicodes.music.ui.icons.FilterList
import com.jussicodes.music.ui.icons.Message
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
    // 和其他半屏页一致：M3 ModalBottomSheet。skipPartiallyExpanded=false 保留
    // 半屏(50%)→上滑全屏两态（和原来固定半屏+上滑全屏行为一致），下滑由 M3
    // grab handle 收起；全屏态内容铺满。
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
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

    LaunchedEffect(sheetState) {
        if (!sheetState.isVisible) {
            sheetState.show()
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
                Toast.makeText(
                    context,
                    it.message ?: "加载更多评论失败",
                    Toast.LENGTH_SHORT
                ).show()
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

    // 删除：仅本人评论可删；确认后从列表移除。
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
        Column(modifier = Modifier.fillMaxWidth()) {
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
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                        modifier = Modifier.fillMaxHeight(),
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
                                onFailure = { message ->
                                    Toast.makeText(
                                        context,
                                        message ?: "评论点赞失败",
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

@Composable
private fun CommentItem(
    resourceId: Long?,
    resourceType: Int,
    comment: Comment,
    currentUid: Long?,
    onReply: (Comment) -> Unit,
    onDelete: (Comment) -> Unit,
    onFailure: (String?) -> Unit
) {
    val scope = rememberCoroutineScope()
    var liked by remember(comment.commentId) { mutableStateOf(comment.liked) }
    var likedCount by remember(comment.commentId) { mutableLongStateOf(comment.likedCount) }

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
                                onFailure(it.message)
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
            if (comment.timeStr.isNotBlank()) {
                Text(
                    text = comment.timeStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = comment.content,
                style = MaterialTheme.typography.bodyMedium
            )
            comment.beReplied.firstOrNull()?.let { reply ->
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "${reply.user.nickname}: ${reply.content}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            val isOwn = currentUid != null && comment.user.userId == currentUid
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
        }
    }
}

/**
 * 底部输入条：登录态才渲染。左侧输入框 + 发送按钮；回复态时上方挂「回复 @昵称 ×」胶囊。
 * 未登录点发送 → toast 提示登录。
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
            .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
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
 * 删除确认 Dialog（同包复用 Dialog 组件；未登录或本人 uid 为空时不显示删除按钮）。
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
