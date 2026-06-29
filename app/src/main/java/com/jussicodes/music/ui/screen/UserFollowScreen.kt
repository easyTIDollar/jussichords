package com.jussicodes.music.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.jussicodes.music.ui.components.ArtistListItem
import com.jussicodes.music.ui.navigation.UserNav
import com.jussicodes.music.viewModel.UserFollowScreenViewModel
import com.jussicodes.music.viewModel.UserFollowType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserFollowScreen(
    navController: NavHostController,
    userId: Long,
    type: UserFollowType,
    userFollowScreenViewModel: UserFollowScreenViewModel = hiltViewModel()
) {
    val users by userFollowScreenViewModel.users.collectAsState()

    LaunchedEffect(userId, type) {
        if (userId > 0) {
            userFollowScreenViewModel.fetch(userId, type)
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
                            UserFollowType.FOLLOWS -> "关注列表"
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
            items(users) { user ->
                ArtistListItem(
                    artist = com.rcmiku.ncmapi.model.SearchArtist(
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
        }
    }
}
