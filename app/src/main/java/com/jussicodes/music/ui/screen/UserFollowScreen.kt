package com.jussicodes.music.ui.screen

import android.icu.text.Transliterator
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.jussicodes.music.ui.components.ArtistListItem
import com.jussicodes.music.ui.components.LargeImageDialog
import com.jussicodes.music.ui.navigation.ArtistNav
import com.jussicodes.music.ui.navigation.UserNav
import com.jussicodes.music.ui.icons.Funnel
import com.jussicodes.music.utils.CoverImageSize
import com.jussicodes.music.utils.toCoverImageUrl
import com.jussicodes.music.viewModel.UserFollowScreenViewModel
import com.jussicodes.music.viewModel.UserFollowType
import com.rcmiku.ncmapi.model.SearchArtist
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserFollowScreen(
    navController: NavHostController,
    userId: Long,
    type: UserFollowType,
    showArtistFollows: Boolean,
    userFollowScreenViewModel: UserFollowScreenViewModel = hiltViewModel()
) {
    val users by userFollowScreenViewModel.users.collectAsState()
    val artists by userFollowScreenViewModel.artists.collectAsState()
    val isLoading by userFollowScreenViewModel.isLoading.collectAsState()
    val isLoadingMore by userFollowScreenViewModel.isLoadingMore.collectAsState()
    val hasMore by userFollowScreenViewModel.hasMore.collectAsState()
    val isFollowedsLimited by userFollowScreenViewModel.isFollowedsLimited.collectAsState()
    val errorMessage by userFollowScreenViewModel.errorMessage.collectAsState()
    val filterUserTypes by userFollowScreenViewModel.filterUserTypes.collectAsState()
    val userTypeCounts by userFollowScreenViewModel.userTypeCounts.collectAsState()
    val sortedArtists = artists.sortedBy { it.name.toPinyinSortKey() }
    val followedArtistUsers = users
        .filter { it.userType == 2 || it.userType == 4 }
        .sortedBy { it.nickname.toPinyinSortKey() }
    val followedUsers = users
        .filterNot { it.userType == 2 || it.userType == 4 }
        .sortedBy { it.nickname.toPinyinSortKey() }
    // "关注的用户" userType 筛选：null = 全部；空集 = 全部隐藏。只作用于用户列表（非歌手 tab）
    val filteredFollowedUsers = followedUsers.filter { filterUserTypes == null || it.userType in filterUserTypes }
    var selectedType by remember(type, showArtistFollows) {
        mutableStateOf(if (type == UserFollowType.FOLLOWS) UserFollowType.ARTISTS else type)
    }
    var previewAvatarUrl by remember { mutableStateOf<String?>(null) }
    var horizontalDragAmount by remember { mutableStateOf(0f) }
    val listState = rememberLazyListState()
    val isContentEmpty = when (selectedType) {
        UserFollowType.ARTISTS -> if (showArtistFollows) sortedArtists.isEmpty() else followedArtistUsers.isEmpty()
        UserFollowType.FOLLOWS -> filteredFollowedUsers.isEmpty()
        UserFollowType.FOLLOWEDS -> users.isEmpty()
    }

    // 下拉刷新
    val coroutineScope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }
    val pullToRefreshState = rememberPullToRefreshState()
    val onRefresh: () -> Unit = {
        if (!isRefreshing) {
            isRefreshing = true
            coroutineScope.launch {
                userFollowScreenViewModel.refresh(userId, selectedType)
                delay(600)
                isRefreshing = false
            }
        }
    }

    LaunchedEffect(userId, selectedType) {
        val fetchType = if (selectedType == UserFollowType.ARTISTS && !showArtistFollows) {
            UserFollowType.FOLLOWS
        } else {
            selectedType
        }
        if (fetchType == UserFollowType.ARTISTS || userId > 0) {
            userFollowScreenViewModel.fetch(userId, fetchType)
        } else {
            userFollowScreenViewModel.clear()
        }
    }

    // 无缝自动加载：滑到倒数第 4 项时自动拉下一页（与 PlayerComments 同款写法）
    LaunchedEffect(listState, users.size, artists.size, hasMore, isLoading, isLoadingMore) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            val totalItemsCount = layoutInfo.totalItemsCount
            totalItemsCount > 0 && lastVisibleIndex >= totalItemsCount - 4
        }
            .distinctUntilChanged()
            .filter { it }
            .collect {
                userFollowScreenViewModel.loadMore()
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (type) {
                            UserFollowType.FOLLOWS,
                            UserFollowType.ARTISTS -> "关注列表"
                            UserFollowType.FOLLOWEDS -> "粉丝列表"
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null
                        )
                    }
                },
                actions = {
                    if (type == UserFollowType.FOLLOWS) {
                        FollowsFilterMenu(
                            active = filterUserTypes != null,
                            activeTypes = filterUserTypes,
                            counts = userTypeCounts,
                            onSet = { userFollowScreenViewModel.setFilterUserTypes(it) }
                        )
                    }
                }
            )
        }
    ) { padding ->
        PullToRefreshBox(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            state = pullToRefreshState,
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            indicator = {
                PullToRefreshDefaults.Indicator(
                    modifier = Modifier.align(Alignment.TopCenter),
                    isRefreshing = isRefreshing,
                    state = pullToRefreshState
                )
            }
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(type) {
                        if (type == UserFollowType.FOLLOWS) {
                            detectHorizontalDragGestures(
                                onDragStart = { horizontalDragAmount = 0f },
                                onHorizontalDrag = { _, amount -> horizontalDragAmount += amount },
                                onDragEnd = {
                                    if (horizontalDragAmount < -80f) selectedType = UserFollowType.FOLLOWS
                                    if (horizontalDragAmount > 80f) selectedType = UserFollowType.ARTISTS
                                    horizontalDragAmount = 0f
                                },
                                onDragCancel = { horizontalDragAmount = 0f }
                            )
                        }
                    }
            ) {
                if (type == UserFollowType.FOLLOWS) {
                    item {
                        SecondaryTabRow(
                            selectedTabIndex = if (selectedType == UserFollowType.ARTISTS) 0 else 1
                        ) {
                            Tab(
                                selected = selectedType == UserFollowType.ARTISTS,
                                onClick = { selectedType = UserFollowType.ARTISTS },
                                text = { Text("关注的歌手", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            )
                            Tab(
                                selected = selectedType == UserFollowType.FOLLOWS,
                                onClick = { selectedType = UserFollowType.FOLLOWS },
                                text = { Text("关注的用户", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            )
                        }
                    }
                    if (!showArtistFollows) {
                        item {
                            Text(
                                text = "接口未提供关注时间，列表按 A-Z 排序，中文按拼音排序",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                            )
                        }
                    }
                }

                when (selectedType) {
                    UserFollowType.ARTISTS -> {
                        if (isLoading && isContentEmpty) {
                            loadingListItems()
                        } else if (errorMessage != null && isContentEmpty) {
                            emptyMessageItem(errorMessage.orEmpty())
                        } else if (!showArtistFollows) {
                            items(followedArtistUsers, key = { it.id }) { user ->
                                ArtistListItem(
                                    artist = SearchArtist(
                                        id = user.id,
                                        name = user.nickname,
                                        picUrl = user.avatarUrl,
                                        briefDesc = user.signature
                                    ),
                                    onThumbnailClick = { previewAvatarUrl = user.avatarUrl },
                                    modifier = Modifier.clickable {
                                        navController.navigate(UserNav(userId = user.id))
                                    }
                                )
                            }
                        } else {
                            items(sortedArtists, key = { it.id }) { artist ->
                                ArtistListItem(
                                    artist = artist,
                                    onThumbnailClick = { previewAvatarUrl = artist.cover },
                                    modifier = Modifier.clickable {
                                        navController.navigate(ArtistNav(artistId = artist.id))
                                    }
                                )
                            }
                        }
                        if (hasMore || isLoadingMore) {
                            loadMoreIndicator(hasMore = hasMore, isLoadingMore = isLoadingMore)
                        }
                    }
                    UserFollowType.FOLLOWS,
                    UserFollowType.FOLLOWEDS -> {
                        if (isLoading && isContentEmpty) {
                            loadingListItems()
                        } else if (errorMessage != null && isContentEmpty) {
                            emptyMessageItem(errorMessage.orEmpty())
                        } else {
                            val displayedUsers = if (selectedType == UserFollowType.FOLLOWS) filteredFollowedUsers else users
                            items(displayedUsers, key = { it.id }) { user ->
                                ArtistListItem(
                                    artist = SearchArtist(
                                        id = user.id,
                                        name = user.nickname,
                                        picUrl = user.avatarUrl,
                                        briefDesc = user.signature
                                    ),
                                    onThumbnailClick = { previewAvatarUrl = user.avatarUrl },
                                    modifier = Modifier.clickable {
                                        navController.navigate(UserNav(userId = user.id))
                                    }
                                )
                            }
                        }
                        // 游标翻到顶、仍有未加载剩余时提示（lasttime 翻不出新数据/空页兜底）
                        if (selectedType == UserFollowType.FOLLOWEDS && isFollowedsLimited) {
                            item {
                                Text(
                                    text = "粉丝已加载 ${users.size} 条，暂无更多可加载",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                                )
                            }
                        }
                        if (hasMore || isLoadingMore) {
                            loadMoreIndicator(hasMore = hasMore, isLoadingMore = isLoadingMore)
                        }
                    }
                }
            }
        }
    }
    previewAvatarUrl?.let { url ->
        LargeImageDialog(
            imageUrl = url.toCoverImageUrl(CoverImageSize.LARGE),
            onDismiss = { previewAvatarUrl = null },
            showSaveAction = true
        )
    }
}

/** 关注的用户 userType 筛选（顶栏漏斗）：交互对齐私信页 SessionsFilterMenu。
 * activeTypes = null 表示未筛选（全部）；counts 为已加载全量类型分布（含当前被筛掉的类型）。
 * 点项即时生效且不关菜单（可连续多选），点菜单外才关闭。 */
@Composable
private fun FollowsFilterMenu(
    active: Boolean,
    activeTypes: Set<Int>?,
    counts: Map<Int, Int>,
    onSet: (Set<Int>?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
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
            DropdownMenuItem(
                text = { Text("显示全部类型") },
                leadingIcon = {
                    Checkbox(
                        checked = allChecked,
                        onCheckedChange = { if (!allChecked) onSet(null) }
                    )
                },
                onClick = {}
            )
            counts.entries.sortedBy { it.key }.forEach { (type, count) ->
                val label = FOLLOW_USER_TYPE_LABELS[type] ?: "类型$type"
                val checked = allChecked || (activeTypes?.contains(type) ?: false)
                DropdownMenuItem(
                    text = { Text("$label（$count）") },
                    leadingIcon = {
                        Checkbox(checked = checked, onCheckedChange = { })
                    },
                    onClick = {
                        val base = if (allChecked) counts.keys.toMutableSet() else activeTypes!!.toMutableSet()
                        if (type in base) base.remove(type) else base.add(type)
                        // 全勾 = 取消筛选（null）；逐项即时生效，菜单保持打开
                        onSet(if (base.size == counts.size) null else base)
                    }
                )
            }
        }
    }
}

/** 关注的用户 userType → 展示名（对齐私信页；NCM 未公开完整枚举，未知码兜底"类型N"）。 */
private val FOLLOW_USER_TYPE_LABELS = mapOf(
    0 to "普通用户",
    2 to "音乐人入驻账号",
    4 to "歌手",
    10 to "官方",
    207 to "音乐达人"
)

private fun String.toPinyinSortKey(): String =
    FollowListPinyinTransliterator.transliterate(trim()).lowercase(Locale.ROOT)

private object FollowListPinyinTransliterator {
    private val transliterator by lazy {
        Transliterator.getInstance("Han-Latin/Names; Latin-ASCII")
    }

    fun transliterate(value: String): String = transliterator.transliterate(value)
}

private fun androidx.compose.foundation.lazy.LazyListScope.emptyMessageItem(message: String) {
    item {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 48.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.loadMoreIndicator(
    hasMore: Boolean,
    isLoadingMore: Boolean
) {
    item {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isLoadingMore) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            } else if (hasMore) {
                Text(
                    text = "上滑加载更多",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.loadingListItems(count: Int = 8) {
    items(count) {
        FollowLoadingItem()
    }
}

@Composable
private fun FollowLoadingItem() {
    val placeholderColor = MaterialTheme.colorScheme.surfaceContainerHigh
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .padding(vertical = 10.dp)
                .size(44.dp)
                .clip(CircleShape)
                .background(placeholderColor)
        )
        Box(
            modifier = Modifier
                .padding(top = 20.dp)
                .fillMaxWidth(0.58f)
                .height(16.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(placeholderColor)
        )
    }
}
