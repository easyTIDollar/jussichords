package com.jussicodes.music.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.toRoute
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import com.jussicodes.music.animation.MotionSpec
import com.jussicodes.music.ui.screen.AlbumScreen
import com.jussicodes.music.ui.screen.AlbumSublistScreen
import com.jussicodes.music.ui.screen.ArtistScreen
import com.jussicodes.music.ui.screen.CloudSongScreen
import com.jussicodes.music.ui.screen.DesktopLyricSettingsScreen
import com.jussicodes.music.ui.screen.ExploreScreen
import com.jussicodes.music.ui.screen.LibraryScreen
import com.jussicodes.music.ui.screen.LoginScreen
import com.jussicodes.music.ui.screen.LxPluginScreen
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
    onHomePageScroll: (Float) -> Unit,
    floatingNavBarEnabled: Boolean = true
) {
    // The two home destinations (library/explore) render the same HorizontalPager;
    // switching between them is route bookkeeping only (driven by MainScreen's
    // navigateRootTab when the settled page changes). Animating that switch
    // crossfades two identical pagers, which reads as a flash + press-down.
    // Keep home<->home switches instant; only animate pushes/pops into detail
    // screens. Detail pushes use a soft crossfade only — the earlier
    // scaleIn(0.985) + EmphasizedDecelerate combo read as a stiff "press down"
    // (search / personal-FM / artist pushes especially). Pure fade with a
    // gentle decelerate feels like a Material-style page reveal.
    val homeRoutes = setOf(Screen.Library.route, Screen.Explore.route)

    // 悬浮底栏是「盖在页面上」的一层：主页内容必须能滚到它下面，胶囊外的区域才透得出封面。
    // 这里只在主页路由取消 NavHost 的整体底部让位，避让改由主页列表末尾留白承担；
    // 二级页仍整体让位（它们没有悬浮穿透需求，让位可以保证列表最后一项不被底栏压住）。
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val homeRouteActive = currentRoute?.let { it in homeRoutes } == true
    val navHostBottomInset = if (floatingNavBarEnabled && homeRouteActive) 0.dp else bottomPadding

    SharedTransitionLayout {
        NavHost(
            navController = navController,
            startDestination = Screen.Library.route,
            Modifier
                .windowInsetsPadding(WindowInsets(bottom = navHostBottomInset)),
            enterTransition = {
                val isHomeToHome = (initialState.destination.route in homeRoutes || initialState.destination.route == null) &&
                    targetState.destination.route in homeRoutes
                if (isHomeToHome) {
                    EnterTransition.None
                } else {
                    MotionSpec.spatialScreenEnter()
                }
            },
            exitTransition = {
                val isHomeToHome = initialState.destination.route in homeRoutes &&
                    targetState.destination.route in homeRoutes
                if (isHomeToHome) {
                    ExitTransition.None
                } else {
                    MotionSpec.spatialScreenExit()
                }
            },
            popEnterTransition = {
                val isHomeToHome = initialState.destination.route in homeRoutes &&
                    targetState.destination.route in homeRoutes
                if (isHomeToHome) {
                    EnterTransition.None
                } else {
                    MotionSpec.spatialScreenPopEnter()
                }
            },
            popExitTransition = {
                val isHomeToHome = initialState.destination.route in homeRoutes &&
                    targetState.destination.route in homeRoutes
                if (isHomeToHome) {
                    ExitTransition.None
                } else {
                    MotionSpec.spatialScreenPopExit()
                }
            }
        ) {
            composable(Screen.Library.route) {
                HomePager(
                    navController = navController,
                    pagerState = homePagerState,
                    onPageChange = onHomePageChange,
                    onPageScroll = onHomePageScroll,
                    bottomContentPadding = if (floatingNavBarEnabled) bottomPadding else 0.dp
                )
            }
            composable(Screen.Explore.route) {
                HomePager(
                    navController = navController,
                    pagerState = homePagerState,
                    onPageChange = onHomePageChange,
                    onPageScroll = onHomePageScroll,
                    bottomContentPadding = if (floatingNavBarEnabled) bottomPadding else 0.dp
                )
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
            composable(Screen.LxPlugins.route) { LxPluginScreen(navController = navController) }
            composable(Screen.DesktopLyricSettings.route) {
                DesktopLyricSettingsScreen(navController = navController)
            }
            composable(Screen.DownloadManager.route) {
                com.jussicodes.music.ui.screen.DownloadScreen(navController = navController)
            }
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
    onPageChange: (Int) -> Unit,
    onPageScroll: (Float) -> Unit,
    bottomContentPadding: Dp = 0.dp
) {
    val coroutineScope = rememberCoroutineScope()
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

    // 冷启动首屏完全专注渲染 LibraryScreen（beyondViewportPageCount = 0 零多余开销）；
    // 首屏渲染完成后延迟 2.5 秒，等主线程与渲染完全静止空闲时，提升为 1，
    // 后台静默预热 ExploreScreen 布局，消除后续用户滑动切换时的首帧重组掉帧。
    var beyondViewportCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        delay(2500)
        beyondViewportCount = 1
    }

    Box {
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = beyondViewportCount,
            flingBehavior = PagerDefaults.flingBehavior(
                state = pagerState,
                snapPositionalThreshold = 0.5f
            )
        ) { page ->
            when (page) {
                0 -> LibraryScreen(
                    navController = navController,
                    bottomContentPadding = bottomContentPadding
                )
                1 -> ExploreScreen(
                    navController = navController,
                    bottomContentPadding = bottomContentPadding
                )
                2 -> SettingsScreen(
                    navController = navController,
                    onBackClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(1)
                        }
                    },
                    bottomContentPadding = bottomContentPadding
                )
            }
        }
    }
}
