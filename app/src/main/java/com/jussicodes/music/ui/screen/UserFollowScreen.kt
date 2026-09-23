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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PullToRefreshBox
import androidx.compose.material3.PullToRefreshDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import com.jussicodes.music.utils.CoverImageSize
import com.jussicodes.music.utils.toCoverImageUrl
import com.jussicodes.music.viewModel.UserFollowScreenViewModel
import com.jussicodes.music.viewModel.UserFollowType
import com.rcmiku.ncmapi.model.SearchArtist
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
    val errorMessage by userFollowScreenViewModel.errorMessage.collectAsState()
    val sortedArtists = artists.sortedBy { it.name.toPinyinSortKey() }
    val followedArtistUsers = users
        .filter { it.userType == 2 || it.userType == 4 }
        .sortedBy { it.nickname.toPinyinSortKey() }
    val followedUsers = users
        .filterNot { it.userType == 2 || it.userType == 4 }
        .sortedBy { it.nickname.toPinyinSortKey() }
    var selectedType by remember(type, showArtistFollows) {
        mutableStateOf(if (type == UserFollowType.FOLLOWS) UserFollowType.ARTISTS else type)
    }
    var previewAvatarUrl by remember { mutableStateOf<String?>(null) }
    var horizontalDragAmount by remember { mutableStateOf(0f) }
    val isContentEmpty = when (selectedType) {
        UserFollowType.ARTISTS -> if (showArtistFollows) sortedArtists.isEmpty() else followedArtistUsers.isEmpty()
        UserFollowType.FOLLOWS -> followedUsers.isEmpty()
        UserFollowType.FOLLOWEDS -> users.isEmpty()
    }

    // 下拉刷新
    val coroutineScope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }
    val pullToRefreshState = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()
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
                            loadMoreItem(
                                hasMore = hasMore,
                                isLoadingMore = isLoadingMore,
                                onClick = { userFollowScreenViewModel.loadMore() }
                            )
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
                            loadMoreItem(
                                hasMore = hasMore,
                                isLoadingMore = isLoadingMore,
                                onClick = { userFollowScreenViewModel.loadMore() }
                            )
                        }
                    }
                    UserFollowType.FOLLOWS,
                    UserFollowType.FOLLOWEDS -> {
                        if (isLoading && isContentEmpty) {
                            loadingListItems()
                        } else if (errorMessage != null && isContentEmpty) {
                            emptyMessageItem(errorMessage.orEmpty())
                        } else {
                            val displayedUsers = if (selectedType == UserFollowType.FOLLOWS) followedUsers else users
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
                            loadMoreItem(
                                hasMore = hasMore,
                                isLoadingMore = isLoadingMore,
                                onClick = { userFollowScreenViewModel.loadMore() }
                            )
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
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.loadMoreItem(
    hasMore: Boolean,
    isLoadingMore: Boolean,
    onClick: () -> Unit
) {
    if (!hasMore && !isLoadingMore) return
    item {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            TextButton(
                enabled = !isLoadingMore,
                onClick = onClick
            ) {
                if (isLoadingMore) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                } else {
                    Text(text = "加载下一页")
                }
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
