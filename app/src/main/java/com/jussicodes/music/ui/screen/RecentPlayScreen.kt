package com.jussicodes.music.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import com.jussicodes.music.LocalPlayerController
import com.jussicodes.music.LocalPlayerState
import com.jussicodes.music.R
import com.jussicodes.music.constants.ListThumbnailSize
import com.jussicodes.music.constants.ThumbnailCornerRadius
import com.jussicodes.music.extensions.playMediaAtId
import com.jussicodes.music.extensions.setPlaylist
import com.jussicodes.music.ui.components.ListItem
import com.jussicodes.music.ui.components.SongListItem
import com.jussicodes.music.ui.icons.History
import com.jussicodes.music.ui.navigation.AlbumNav
import com.jussicodes.music.ui.navigation.PlaylistNav
import com.jussicodes.music.utils.CoverImageSize
import com.jussicodes.music.utils.toCoverImageUrl
import com.jussicodes.music.viewModel.RecentPlayViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val RECENT_TIME_FORMAT = DateTimeFormatter.ofPattern("MM-dd HH:mm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentPlayScreen(
    navController: NavHostController,
    recentPlayViewModel: RecentPlayViewModel = hiltViewModel()
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    // 歌曲 / 歌单 / 专辑
    val titles = listOf(
        stringResource(R.string.song),
        stringResource(R.string.playlists),
        stringResource(R.string.album)
    )

    val songs = recentPlayViewModel.songs.collectAsState().value
    val playlists = recentPlayViewModel.playlists.collectAsState().value
    val albums = recentPlayViewModel.albums.collectAsState().value

    val mediaController = LocalPlayerController.current.controller
    val playerState = LocalPlayerState.current
    val isPlaying = playerState?.isPlaying == true
    val currentMediaId = playerState?.currentMediaItem?.mediaId?.toLongOrNull()

    // 下拉刷新（重新拉取最近播放，让刚打卡的歌尽快显示）
    val coroutineScope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }
    val pullToRefreshState = rememberPullToRefreshState()
    val onRefresh: () -> Unit = {
        if (!isRefreshing) {
            isRefreshing = true
            coroutineScope.launch {
                recentPlayViewModel.refresh()
                delay(600)
                isRefreshing = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.recent_play)) },
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
                Indicator(
                    modifier = Modifier.align(Alignment.TopCenter),
                    isRefreshing = isRefreshing,
                    state = pullToRefreshState
                )
            }
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                item {
                    Column {
                        SecondaryTabRow(selectedTabIndex = selectedTab) {
                            titles.forEachIndexed { index, title ->
                                Tab(
                                    selected = selectedTab == index,
                                    onClick = { selectedTab = index },
                                    text = { Text(title) }
                                )
                            }
                        }
                    }
                }
                when (selectedTab) {
                    0 -> songs?.let { list ->
                        if (list.isEmpty()) {
                            item { EmptyRecentPlaceholder() }
                        } else {
                            itemsIndexed(list) { index, entry ->
                                SongListItem(
                                    song = entry.data,
                                    songIndex = index + 1,
                                    isPlaying = isPlaying,
                                    isActive = currentMediaId == entry.data.id,
                                    modifier = Modifier.clickable {
                                        mediaController?.setPlaylist(list.map { it.data })
                                        mediaController?.playMediaAtId(entry.data.id)
                                    },
                                    trailingContent = {
                                        Text(
                                            text = formatRecentTime(entry.playTime),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                )
                            }
                        }
                    }
                    1 -> playlists?.let { list ->
                        if (list.isEmpty()) {
                            item { EmptyRecentPlaceholder() }
                        } else {
                            itemsIndexed(list) { _, entry ->
                                val data = entry.data
                                ListItem(
                                    title = data.name,
                                    subtitle = "上次播放 · ${data.lastSong.name.ifBlank { "-" }}",
                                    thumbnailContent = {
                                        AsyncImage(
                                            model = data.coverImgUrl.toCoverImageUrl(CoverImageSize.LIST),
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(ThumbnailCornerRadius))
                                                .size(ListThumbnailSize)
                                        )
                                    },
                                    trailingContent = {
                                        Text(
                                            text = formatRecentTime(entry.playTime),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    },
                                    modifier = Modifier.clickable {
                                        navController.navigate(
                                            PlaylistNav(
                                                playlistId = data.id,
                                                noCache = true
                                            )
                                        )
                                    }
                                )
                            }
                        }
                    }
                    else -> albums?.let { list ->
                        if (list.isEmpty()) {
                            item { EmptyRecentPlaceholder() }
                        } else {
                            itemsIndexed(list) { _, entry ->
                                val data = entry.data
                                ListItem(
                                    title = data.name,
                                    subtitle = "${data.size}首 · ${data.artists.joinToString("/") { it.name }.ifBlank { "-" }}",
                                    thumbnailContent = {
                                        AsyncImage(
                                            model = data.picUrl.toCoverImageUrl(CoverImageSize.LIST),
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(ThumbnailCornerRadius))
                                                .size(ListThumbnailSize)
                                        )
                                    },
                                    trailingContent = {
                                        Text(
                                            text = formatRecentTime(entry.playTime),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    },
                                    modifier = Modifier.clickable {
                                        navController.navigate(AlbumNav(albumId = data.id))
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

@Composable
private fun EmptyRecentPlaceholder() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = History,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(56.dp)
        )
        Text(
            text = "暂无最近播放记录",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

private fun formatRecentTime(ms: Long): String {
    if (ms <= 0) return ""
    return Instant.ofEpochMilli(ms)
        .atZone(ZoneId.systemDefault())
        .format(RECENT_TIME_FORMAT)
}
