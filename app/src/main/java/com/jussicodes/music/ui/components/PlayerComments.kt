package com.jussicodes.music.ui.components

import android.widget.Toast
import androidx.core.graphics.ColorUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaMetadata
import coil3.compose.AsyncImage
import com.jussicodes.music.LocalPlayerState
import com.jussicodes.music.ui.icons.ChevronDown
import com.jussicodes.music.ui.icons.Delete
import com.jussicodes.music.ui.icons.Favorite
import com.jussicodes.music.ui.icons.FavoriteFill
import com.jussicodes.music.ui.icons.FilterList
import com.jussicodes.music.utils.CoverImageSize
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
import kotlin.math.abs
import kotlin.math.roundToInt

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
 * 整页"不透明"辅助：把半透明 tint 色烘焙到面板底色(surfaceContainer)上，
 * 返回不透明实色。避免任何 copy(alpha=…) 引入透明像素。视觉与半透明叠在面板上一致。
 */
@Composable
private fun opaqueTint(fg: Color, ratio: Float): Color =
    Color(ColorUtils.blendARGB(MaterialTheme.colorScheme.surfaceContainer.toArgb(), fg.toArgb(), ratio))

/**
 * 评论区核心内容与交互逻辑：包含标题、排序、评论列表、楼中楼、输入条以及发表/回复/删除逻辑。
 */
@Composable
fun PlayerCommentsContent(
    mediaId: Long?,
    mediaMetadata: MediaMetadata,
    commentType: Int = 0,
    modifier: Modifier = Modifier,
    headerModifier: Modifier = Modifier,
    listModifier: Modifier = Modifier,
    lazyListState: LazyListState = rememberLazyListState(),
    onUserClick: (userId: Long) -> Unit = {}
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

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

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
                    Toast.makeText(context, it.message ?: "发送失败", Toast.LENGTH_SHORT).show()
                }
        }
    }

    // 删除：提交到 NCM，成功后从本地列表直接剔除（不重新请求整页，保住当前滚动位）。
    fun deleteComment(cid: Long) {
        val songId = resolvedMediaId ?: return
        scope.launch {
            CommentApi.deleteComment(id = songId, type = commentType, cid = cid)
                .onSuccess {
                    comments = comments.filter { c -> c.commentId != cid }
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

    Column(modifier = modifier) {
        // 标题行（封面 + 标题 + 排序）
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .then(headerModifier)
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
                JussiDropdownMenu(
                    expanded = sortMenuExpanded,
                    onDismissRequest = { sortMenuExpanded = false }
                ) {
                    commentSortOptions.forEach { option ->
                        JussiMenuItem(
                            text = option.title,
                            icon = when (option.type) {
                                2 -> Icons.Outlined.LocalFireDepartment
                                3 -> Icons.Outlined.Schedule
                                else -> Icons.Outlined.ThumbUp
                            },
                            selected = selectedSort.type == option.type,
                            onClick = {
                                selectedSort = option
                                sortMenuExpanded = false
                            }
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = opaqueTint(DividerDefaults.color, 0.4f))

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
                        modifier = Modifier
                            .fillMaxSize()
                            .then(listModifier),
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
                                },
                                onUserClick = { onUserClick(it) }
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
            onSend = { submitComment() },
            modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars.exclude(WindowInsets.ime))
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

/**
 * 播放页半屏评论面板（M3 ModalBottomSheet 包装，供其它独立页面调用）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerComments(
    mediaId: Long?,
    mediaMetadata: MediaMetadata,
    commentType: Int = 0,
    modifier: Modifier = Modifier,
    onBackPressed: () -> Unit = {},
    onUserClick: (userId: Long) -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(sheetState) {
        if (!sheetState.isVisible) {
            sheetState.show()
        }
    }

    ResponsiveModalBottomSheet(
        onDismissRequest = onBackPressed,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        scrimColor = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .fillMaxHeight()
        ) {
            PlayerCommentsContent(
                mediaId = mediaId,
                mediaMetadata = mediaMetadata,
                commentType = commentType,
                onUserClick = onUserClick,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * 播放页内嵌跟手评论面板（物理手势与位移驱动，与小播放器手势同款手感）。
 *
 * 核心交互：
 * 1. 快速轻扫直接展开/收起：结合 VelocityTracker 判定松手垂直速度；
 * 2. 慢慢滑像素级跟手：随着手指纵向位移实时平滑拖拽，支持中途反向拉回取消；
 * 3. 动画可打断：展开/收起飞行过程中手指按下即可立即中断动画并重新接管；
 * 4. 列表嵌套滚动：评论列表滑到最顶部后继续下拉，接管带动面板下滑收起。
 */
@Composable
fun PlayerCommentsPanel(
    mediaId: Long?,
    mediaMetadata: MediaMetadata,
    commentType: Int = 0,
    translationY: Float,
    progress: Float,
    onDismiss: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: (velocityY: Float) -> Unit,
    onUserClick: (userId: Long) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val lazyListState = rememberLazyListState()

    val nestedScrollConnection = remember(translationY) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val deltaY = available.y
                // 面板在半开状态（已拉下一截）时，向上推优先把面板推回顶部 0f
                if (deltaY < 0f && translationY > 0f) {
                    onDrag(deltaY)
                    return Offset(0f, deltaY)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                val deltaY = available.y
                // 列表在最顶端继续向下拉动，接管为向下拉动面板
                if (deltaY > 0f && source == NestedScrollSource.UserInput) {
                    onDrag(deltaY)
                    return Offset(0f, deltaY)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (translationY > 0f) {
                    onDragEnd(available.y)
                    return available
                }
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (available.y > 0f && lazyListState.firstVisibleItemIndex == 0 && lazyListState.firstVisibleItemScrollOffset == 0) {
                    onDragEnd(available.y)
                    return available
                }
                return Velocity.Zero
            }
        }
    }

    val dragModifier = Modifier.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val velocityTracker = VelocityTracker()
            velocityTracker.addPosition(down.uptimeMillis, down.position)
            var isDragging = false
            var totalDragY = 0f

            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break

                velocityTracker.addPosition(change.uptimeMillis, change.position)
                val positionChange = change.positionChange()
                totalDragY += positionChange.y

                if (!isDragging && abs(totalDragY) > viewConfiguration.touchSlop) {
                    isDragging = true
                }

                if (isDragging) {
                    change.consume()
                    onDrag(positionChange.y)
                }
            }

            if (isDragging) {
                val velocityY = velocityTracker.calculateVelocity().y
                onDragEnd(velocityY)
            }
        }
    }

    Surface(
        modifier = modifier
            .offset { IntOffset(0, translationY.roundToInt()) }
            .fillMaxWidth()
            .fillMaxHeight(),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 16.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // 顶部抓手条 Drag Handle（可拖动）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(dragModifier)
                    .padding(top = 10.dp, bottom = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .background(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(2.dp)
                        )
                )
            }

            PlayerCommentsContent(
                mediaId = mediaId,
                mediaMetadata = mediaMetadata,
                commentType = commentType,
                headerModifier = dragModifier,
                listModifier = Modifier.nestedScroll(nestedScrollConnection),
                lazyListState = lazyListState,
                onUserClick = onUserClick,
                modifier = Modifier.weight(1f)
            )
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
    onToast: (String?) -> Unit,
    onUserClick: (Long) -> Unit = {}
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
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        CoverAsyncImage(
            url = comment.user.avatarUrl,
            size = CoverImageSize.LIST,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier
                .size(36.dp)
                .clickable { if (comment.user.userId != 0L) onUserClick(comment.user.userId) }
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            // 昵称（与头像上沿对齐：外层 Row 已 Top 对齐，此为 Column 首个子元素；点击跳主页）
            Text(
                text = comment.user.nickname,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable {
                    if (comment.user.userId != 0L) onUserClick(comment.user.userId)
                }
            )
            Spacer(Modifier.height(4.dp))
            // 正文 + 点赞（移至正文右侧并列）
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = comment.content,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp,
                        lineHeight = 22.sp
                    ),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
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
                    modifier = Modifier.size(32.dp)
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
            Spacer(Modifier.height(4.dp))
            // 操作行：时间·属地 + 回复(胶囊) + 本人删除（紧凑一行）
            val date = comment.timeStr
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (location != null || date.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (location != null) {
                            Text(
                                text = location,
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
                    Spacer(Modifier.width(12.dp))
                }
                Text(
                    text = "回复",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(opaqueTint(MaterialTheme.colorScheme.primary, 0.10f))
                        .clickable { onReply(comment) }
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                )
                if (isOwn) {
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
                        .padding(start = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            if (!floorExpanded) {
                                floorExpanded = true
                                loadFloors()
                            } else {
                                floorExpanded = false
                            }
                        }
                        .background(opaqueTint(MaterialTheme.colorScheme.primary, 0.08f))
                        .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 4.dp)
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

                // 楼层回复区（按需展开）：左侧引用竖线 + 浅色胶囊底，拉开层级
                if (floorExpanded) {
                    Row(
                        modifier = Modifier
                            .padding(top = 6.dp, start = 8.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .fillMaxHeight()
                                .background(
                                    opaqueTint(MaterialTheme.colorScheme.outlineVariant, 0.5f),
                                    RoundedCornerShape(2.dp)
                                )
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 8.dp, top = 4.dp, bottom = 4.dp)
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
                                    onToast = { onToast(it) },
                                    onUserClick = { onUserClick(it) }
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
    onToast: (String?) -> Unit,
    onUserClick: (Long) -> Unit = {}
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
        CoverAsyncImage(
            url = floor.user.avatarUrl,
            size = CoverImageSize.AVATAR,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier
                .size(28.dp)
                .clickable { if (floor.user.userId != 0L) onUserClick(floor.user.userId) }
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            // 昵称（与头像上沿对齐：外层 Row 已 Top 对齐，此为 Column 首个子元素；点击跳主页）
            Text(
                text = floor.user.nickname,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable {
                    if (floor.user.userId != 0L) onUserClick(floor.user.userId)
                }
            )
            Spacer(Modifier.height(4.dp))
            // 正文 + 点赞（移至正文右侧并列）
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = floor.content,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp,
                        lineHeight = 22.sp
                    ),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
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
            Spacer(Modifier.height(4.dp))
            val date = floor.timeStr
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                if (location != null || date.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (location != null) {
                            Text(
                                text = location,
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
                    Spacer(Modifier.width(12.dp))
                }
                Text(
                    text = "回复",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(opaqueTint(MaterialTheme.colorScheme.primary, 0.10f))
                        .clickable { onReply(floor) }
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                )
                if (isOwn) {
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
                        opaqueTint(MaterialTheme.colorScheme.primary, 0.08f),
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
