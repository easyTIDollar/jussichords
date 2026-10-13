package com.jussicodes.music.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import kotlinx.coroutines.flow.distinctUntilChanged
import com.jussicodes.music.constants.DURATION_ENTER
import com.jussicodes.music.constants.DURATION_EXIT_SHORT
import com.jussicodes.music.constants.EmphasizedAccelerateEasing
import com.jussicodes.music.constants.EmphasizedDecelerateEasing
import com.jussicodes.music.ui.components.tabs
import com.jussicodes.music.ui.screen.AlbumScreen
import com.jussicodes.music.ui.screen.AlbumSublistScreen
import com.jussicodes.music.ui.screen.ArtistScreen
import com.jussicodes.music.ui.screen.CloudSongScreen
import com.jussicodes.music.ui.screen.ExploreScreen
import com.jussicodes.music.ui.screen.LibraryScreen
import com.jussicodes.music.ui.screen.LoginScreen
import com.jussicodes.music.ui.screen.MessagesScreen
import com.jussicodes.music.ui.screen.PlaylistScreen
import com.jussicodes.music.ui.screen.PrivateChatScreen
import com.jussicodes.music.ui.screen.ProgramRadioScreen
import com.jussicodes.music.ui.screen.RecentPlayScreen
import com.jussicodes.music.ui.screen.RecordScreen
import com.jussicodes.music.ui.screen.RoamScreen
import com.jussicodes.music.ui.screen.SearchScreen
import com.jussicodes.music.ui.screen.SettingsScreen
import com.jussicodes.music.ui.screen.UserScreen
import com.jussicodes.music.ui.screen.UserFollowScreen
import com.jussicodes.music.ui.screen.UserPlaylistScreen
import com.jussicodes.music.viewModel.UserFollowType

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun NavGraph(
    navController: NavHostController,
    bottomPadding: Dp,
    homePagerState: PagerState,
    onHomePageChange: (Int) -> Unit,
    onHomePageScroll: (Float) -> Unit
) {
    // 我的/探索两个 destination 都渲染同一个 pager。横滑 settle 后 MainScreen 会
    // navigateRootTab 同步路由（返回栈需要），NavHost 随即销毁旧 destination 下的
    // 整棵 HomePager → LibraryScreen → LibraryUserCard 并在新 destination 下重建：
    // 新建的 AsyncImage 走 crossfade，空窗期露出头像占位底色，开「主页透明卡片」时
    // 就是用户看到的「探索划回主页信息卡闪一下白」。
    // 用 movableContentOf 把 HomePager 变成可迁移内容：destination 切换时同一份
    // composition（含 LazyColumn 滚动位置、AsyncImage painter、各 remember 状态）
    // 整体搬到新 destination，不销毁不重建，闪白从机制上消失。
    // 注意：movableContentOf 必须在 NavGraph 这一层 remember，不能放进
    // composable{} 里——否则两个 destination 各 remember 一份，就谈不上迁移。
    val homePagerContent = remember {
        movableContentOf { currentPage: Int ->
            HomePager(
                navController = navController,
                pagerState = homePagerState,
                requestedPage = currentPage,
                onPageChange = onHomePageChange,
                onPageScroll = onHomePageScroll
            )
        }
    }

    // home<->home 只做返回栈记账，没有任何动画：交叉淡入两个相同 pager 会被看成闪一下。
    val homeRoutes = setOf(Screen.Library.route, Screen.Explore.route)

    SharedTransitionLayout {
        NavHost(
            navController = navController,
            startDestination = Screen.Library.route,
            Modifier
                .windowInsetsPadding(WindowInsets(bottom = bottomPadding)),
            enterTransition = {
                if (targetState.destination.route in homeRoutes) {
                    EnterTransition.None
                } else {
                    fadeIn(
                        animationSpec = tween(
                            durationMillis = DURATION_ENTER,
                            easing = EmphasizedDecelerateEasing
                        )
                    )
                }
            },
            exitTransition = {
                if (targetState.destination.route in homeRoutes) {
                    // home <-> home is pager bookkeeping only: instant switch,
                    // crossfading two identical pagers reads as a flash.
                    ExitTransition.None
                } else {
                    // push into a detail page: crossfade the home out under it
                    // instead of letting it vanish to a blank window.
                    fadeOut(
                        animationSpec = tween(
                            durationMillis = DURATION_EXIT_SHORT,
                            easing = EmphasizedAccelerateEasing
                        )
                    )
                }
            },
            popEnterTransition = {
                if (targetState.destination.route in homeRoutes) {
                    EnterTransition.None
                } else {
                    fadeIn(
                        animationSpec = tween(
                            durationMillis = DURATION_ENTER,
                            easing = EmphasizedDecelerateEasing
                        )
                    )
                }
            },
            popExitTransition = {
                if (initialState.destination.route in homeRoutes) {
                    ExitTransition.None
                } else {
                    fadeOut(
                        animationSpec = tween(
                            durationMillis = DURATION_EXIT_SHORT,
                            easing = EmphasizedAccelerateEasing
                        )
                    )
                }
            }
        ) {
            composable(Screen.Library.route) {
                homePagerContent(tabs.indexOfFirst { it.route == Screen.Library.route })
            }
            composable(Screen.Explore.route) {
                homePagerContent(tabs.indexOfFirst { it.route == Screen.Explore.route })
            }
            composable(Screen.Roam.route) {
                RoamScreen(
                    navController = navController,
                    onBackPressed = { navController.popBackStack() }
                )
            }
            composable(Screen.RecentPlay.route) {
                RecentPlayScreen(navController = navController)
            }
            composable(Screen.Settings.route) { SettingsScreen(navController = navController) }
            composable(Screen.Login.route) { LoginScreen(navController = navController) }
            composable(Screen.Messages.route) { MessagesScreen(navController = navController) }
            composable(Screen.Search.route) { SearchScreen(navController = navController) }
            composable<PlaylistNav> {
                PlaylistScreen(
                    navController = navController,
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedContentScope = this@composable
                )
            }
            composable<AlbumNav> {
                AlbumScreen(
                    navController = navController,
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedContentScope = this@composable
                )
            }
            composable(Screen.AlbumSublist.route) {
                AlbumSublistScreen(
                    navController = navController,
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedContentScope = this@composable
                )
            }
            composable<UserFollowNav> { backStackEntry ->
                val route = backStackEntry.toRoute<UserFollowNav>()
                UserFollowScreen(
                    navController = navController,
                    userId = route.userId,
                    showArtistFollows = route.showArtistFollows,
                    type = when {
                        route.type.equals(UserFollowType.FOLLOWEDS.name, ignoreCase = true) ->
                            UserFollowType.FOLLOWEDS
                        route.type.equals("followeds", ignoreCase = true) ->
                            UserFollowType.FOLLOWEDS
                        else -> UserFollowType.FOLLOWS
                    }
                )
            }
            composable<RecordNav> {
                RecordScreen(navController = navController)
            }
            composable<CloudSongNav> {
                CloudSongScreen(navController = navController)
            }
            composable<ArtistNav> {
                ArtistScreen(navController = navController)
            }
            composable<UserNav> {
                UserScreen(navController = navController)
            }
            composable<PrivateChatNav> {
                PrivateChatScreen(navController = navController)
            }
            composable<RadioNav> {
                ProgramRadioScreen(
                    navController = navController,
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedContentScope = this@composable
                )
            }
        }
    }
}

@Composable
private fun HomePager(
    navController: NavHostController,
    pagerState: PagerState,
    requestedPage: Int,
    onPageChange: (Int) -> Unit,
    onPageScroll: (Float) -> Unit
) {
    // 新 destination 挂载时按该路由对应的 tab 落页（通常是 no-op，因为横滑已把
    // pagerState 带到目标页；只有从二级页/播放器跳过来时才真正生效）。
    // 用 scrollToPage 不用 animateScrollToPage：无动画切换本来就是 home 的设计。
    LaunchedEffect(requestedPage) {
        if (pagerState.currentPage != requestedPage || pagerState.currentPageOffsetFraction != 0f) {
            pagerState.scrollToPage(requestedPage)
        }
    }

    LaunchedEffect(pagerState.settledPage) {
        onPageChange(pagerState.settledPage)
        onPageScroll(pagerState.settledPage.toFloat())
    }

    LaunchedEffect(pagerState) {
        snapshotFlow {
            if (pagerState.isScrollInProgress) {
                (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                    .coerceIn(0f, pagerState.pageCount.minus(1).coerceAtLeast(0).toFloat())
            } else {
                pagerState.settledPage.toFloat()
            }
        }
            .distinctUntilChanged()
            .collect(onPageScroll)
    }

    Box {
        HorizontalPager(
            state = pagerState,
            flingBehavior = PagerDefaults.flingBehavior(
                state = pagerState,
                snapPositionalThreshold = 0.5f
            )
        ) { page ->
            when (page) {
                0 -> LibraryScreen(navController = navController)
                1 -> ExploreScreen(navController = navController)
            }
        }
    }
}
