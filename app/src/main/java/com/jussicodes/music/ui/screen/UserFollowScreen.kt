package com.jussicodes.music.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.jussicodes.music.ui.components.ArtistListItem
import com.jussicodes.music.ui.navigation.ArtistNav
import com.jussicodes.music.ui.navigation.UserNav
import com.jussicodes.music.viewModel.UserFollowScreenViewModel
import com.jussicodes.music.viewModel.UserFollowType
import com.rcmiku.ncmapi.model.SearchArtist

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserFollowScreen(
    navController: NavHostController,
    userId: Long,
    type: UserFollowType,
    userFollowScreenViewModel: UserFollowScreenViewModel = hiltViewModel()
) {
    val users by userFollowScreenViewModel.users.collectAsState()
    val artists by userFollowScreenViewModel.artists.collectAsState()
    val isLoading by userFollowScreenViewModel.isLoading.collectAsState()
    val isLoadingMore by userFollowScreenViewModel.isLoadingMore.collectAsState()
    val hasMore by userFollowScreenViewModel.hasMore.collectAsState()
    var selectedType by remember(type) {
        mutableStateOf(if (type == UserFollowType.FOLLOWS) UserFollowType.ARTISTS else type)
    }
    val isContentEmpty = when (selectedType) {
        UserFollowType.ARTISTS -> artists.isEmpty()
        UserFollowType.FOLLOWS,
        UserFollowType.FOLLOWEDS -> users.isEmpty()
    }

    LaunchedEffect(userId, selectedType) {
        if (selectedType == UserFollowType.ARTISTS || userId > 0) {
            userFollowScreenViewModel.fetch(userId, selectedType)
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
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
            }

            when (selectedType) {
                UserFollowType.ARTISTS -> {
                    if (isLoading && isContentEmpty) {
                        loadingListItems()
                    } else {
                        items(artists, key = { it.id }) { artist ->
                            ArtistListItem(
                                artist = artist,
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
                    } else {
                        items(users, key = { it.id }) { user ->
                            ArtistListItem(
                                artist = SearchArtist(
                                    id = user.id,
                                    name = user.nickname,
                                    picUrl = user.avatarUrl,
                                    briefDesc = user.signature
                                ),
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
                    Text(text = "\u52a0\u8f7d\u4e0b\u4e00\u9875")
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
