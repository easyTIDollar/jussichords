package com.jussicodes.music.ui.screen

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.paging.compose.collectAsLazyPagingItems
import com.jussicodes.music.LocalPlayerState
import com.jussicodes.music.R
import com.jussicodes.music.constants.MiniPlayerHeight
import com.jussicodes.music.ui.components.AlbumListItem
import com.jussicodes.music.ui.components.ListThumbnailImage
import com.jussicodes.music.ui.navigation.AlbumNav
import com.jussicodes.music.viewModel.AlbumSublistScreenViewmodel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun AlbumSublistScreen(
    navController: NavHostController,
    albumSublistScreenViewmodel: AlbumSublistScreenViewmodel = hiltViewModel(),
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope
) {
    val albumSublist = albumSublistScreenViewmodel.albumSublist.collectAsLazyPagingItems()
    // 小播放器是否显示（有歌在队列）：列表末尾避让空白块只在有歌时留
    val hasMiniPlayer = (LocalPlayerState.current?.player?.mediaItemCount ?: 0) != 0

    Scaffold(topBar = {
        TopAppBar(
            title = {
                Text(
                    text = stringResource(R.string.my_album),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            navigationIcon = {
                IconButton(onClick = {
                    navController.navigateUp()
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null
                    )
                }
            },
        )
    }) { padding ->
        LazyColumn(
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(albumSublist.itemCount) { index ->
                albumSublist[index]?.let {
                    with(sharedTransitionScope) {
                        AlbumListItem(
                            album = it, thumbnailContent = {
                                ListThumbnailImage(
                                    url = it.picUrl,
                                    modifier = Modifier.sharedElement(
                                        sharedTransitionScope.rememberSharedContentState(
                                            key = it.id
                                        ),
                                        animatedVisibilityScope = animatedContentScope
                                    )
                                )
                            }, modifier = Modifier
                                .clickable {
                                    navController.navigate(AlbumNav(albumId = it.id))
                                })
                    }

                }
            }
            item {
                // 小播放器避让：有歌时末尾垫 64dp，最后一行不被悬浮播放器盖住
                if (hasMiniPlayer) Spacer(modifier = Modifier.height(MiniPlayerHeight))
                Spacer(modifier = Modifier.navigationBarsPadding())
            }
        }
    }
}