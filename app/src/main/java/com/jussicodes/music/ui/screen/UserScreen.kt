package com.jussicodes.music.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import androidx.compose.material3.OutlinedButton
import com.jussicodes.music.ui.components.PlaylistListItem
import com.jussicodes.music.ui.navigation.PlaylistNav
import com.jussicodes.music.ui.navigation.UserFollowNav
import com.jussicodes.music.viewModel.UserFollowType
import com.jussicodes.music.viewModel.UserScreenViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserScreen(
    navController: NavHostController,
    userScreenViewModel: UserScreenViewModel = hiltViewModel()
) {
    val userDetailState by userScreenViewModel.userDetail.collectAsState()
    val userPlaylists by userScreenViewModel.userPlaylists.collectAsState()
    val isFollowed by userScreenViewModel.isFollowed.collectAsState()
    val isFollowUpdating by userScreenViewModel.isFollowUpdating.collectAsState()
    val isSelf by userScreenViewModel.isSelf.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text("用户主页") }) }) { padding ->
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AsyncImage(
                        model = userDetailState?.profile?.avatarUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(84.dp)
                            .clip(CircleShape)
                    )
                    Text(text = userDetailState?.profile?.nickname.orEmpty())
                    Text(text = userDetailState?.profile?.signature?.takeIf { it.isNotBlank() } ?: "暂无签名")
                    if (!isSelf) {
                        OutlinedButton(
                            onClick = userScreenViewModel::toggleFollow,
                            enabled = !isFollowUpdating && userDetailState != null
                        ) {
                            Text(text = if (isFollowed) "取消关注" else "关注")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "关注：${userDetailState?.profile?.followsCount ?: 0}",
                            modifier = Modifier.clickable {
                                navController.navigate(
                                    UserFollowNav(
                                        userId = userDetailState?.profile?.userId ?: 0,
                                        type = UserFollowType.FOLLOWS.name
                                    )
                                )
                            }
                        )
                        Text(
                            text = "粉丝：${userDetailState?.profile?.followedsCount ?: 0}",
                            modifier = Modifier.clickable {
                                navController.navigate(
                                    UserFollowNav(
                                        userId = userDetailState?.profile?.userId ?: 0,
                                        type = UserFollowType.FOLLOWEDS.name
                                    )
                                )
                            }
                        )
                    }
                }
            }

            items(userPlaylists.size) { index ->
                val playlist = userPlaylists[index]
                PlaylistListItem(
                    playlist = playlist,
                    modifier = Modifier.clickable {
                        navController.navigate(
                            PlaylistNav(
                                playlistId = playlist.id,
                                limit = playlist.trackCount
                            )
                        )
                    }
                )
            }
        }
    }
}
