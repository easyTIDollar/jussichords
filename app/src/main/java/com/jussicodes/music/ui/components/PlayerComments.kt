package com.jussicodes.music.ui.components

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaMetadata
import coil3.compose.AsyncImage
import com.jussicodes.music.LocalPlayerState
import com.jussicodes.music.constants.DURATION_ENTER
import com.jussicodes.music.constants.DURATION_EXIT
import com.jussicodes.music.ui.icons.Favorite
import com.jussicodes.music.ui.icons.FavoriteFill
import com.jussicodes.music.ui.icons.FilterList
import com.rcmiku.ncmapi.api.comment.CommentApi
import com.rcmiku.ncmapi.model.Comment
import com.rcmiku.ncmapi.model.CommentNewData
import kotlin.math.abs
import kotlinx.coroutines.delay
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
    var cursor by remember(resolvedMediaId, selectedSort.type) { mutableLongStateOf(0L) }
    var hasMore by remember(resolvedMediaId, selectedSort.type) { mutableStateOf(false) }
    var isLoading by remember(resolvedMediaId) { mutableStateOf(false) }
    var isLoadingMore by remember(resolvedMediaId, selectedSort.type) { mutableStateOf(false) }
    var errorMessage by remember(resolvedMediaId) { mutableStateOf<String?>(null) }
    var isFullScreen by remember { mutableStateOf(false) }
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val halfSheetHeight = configuration.screenHeightDp.dp * 0.5f
    val fullSheetHeight = configuration.screenHeightDp.dp
    val sheetHeight = if (isFullScreen) fullSheetHeight else halfSheetHeight
    val animatedSheetHeight by animateDpAsState(
        targetValue = sheetHeight,
        animationSpec = tween(durationMillis = DURATION_ENTER),
        label = "CommentSheetHeight"
    )
    val expandThresholdPx = with(density) { 36.dp.toPx() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()
    var sheetVisible by remember { mutableStateOf(false) }
    fun dismissWithAnimation() {
        scope.launch {
            sheetVisible = false
            delay(DURATION_EXIT.toLong())
            onBackPressed()
        }
    }

    BackHandler(onBack = ::dismissWithAnimation)

    LaunchedEffect(Unit) {
        sheetVisible = true
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
                cursor = cursor.takeIf { selectedSort.type == 3 && it > 0L }
            ).onSuccess {
                commentData = it.data
                comments = comments + it.data.comments
                pageNo = nextPage
                hasMore = it.data.hasMore
                cursor = it.data.cursor.takeIf { value -> value > 0L }
                    ?: it.data.comments.lastOrNull()?.time
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

    LaunchedEffect(resolvedMediaId, selectedSort.type) {
        val songId = resolvedMediaId ?: return@LaunchedEffect
        isLoading = true
        errorMessage = null
        comments = emptyList()
        pageNo = 1
        cursor = 0L
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
            cursor = it.data.cursor.takeIf { value -> value > 0L }
                ?: it.data.comments.lastOrNull()?.time
                ?: 0L
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = ::dismissWithAnimation
            ),
        contentAlignment = Alignment.BottomCenter
    ) {
        AnimatedVisibility(
            visible = sheetVisible,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(durationMillis = DURATION_ENTER)
            ),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(durationMillis = DURATION_EXIT)
            )
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = MaterialTheme.shapes.large,
                modifier = modifier
                    .fillMaxWidth()
                    .height(animatedSheetHeight)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(120.dp)
                                .height(32.dp)
                                .pointerInput(isFullScreen, expandThresholdPx) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    var totalDragY = 0f
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                        if (!change.pressed) break
                                        totalDragY += change.positionChange().y
                                        if (abs(totalDragY) >= expandThresholdPx) {
                                            if (totalDragY < 0f) {
                                                isFullScreen = true
                                            } else if (isFullScreen) {
                                                isFullScreen = false
                                            } else {
                                                dismissWithAnimation()
                                            }
                                            change.consume()
                                            break
                                        }
                                    }
                                }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(72.dp)
                                    .height(5.dp)
                                    .clip(MaterialTheme.shapes.small)
                                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f))
                            )
                        }
                    }
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
            }
        }
    }
}

@Composable
private fun CommentItem(
    resourceId: Long?,
    resourceType: Int,
    comment: Comment,
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
        }
    }
}
