package com.jussicodes.music.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
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
import com.jussicodes.music.constants.MiniPlayerHeight
import com.jussicodes.music.ui.screen.AlbumScreen
import com.jussicodes.music.ui.screen.AlbumSublistScreen
import com.jussicodes.music.ui.screen.ArtistScreen
import com.jussicodes.music.ui.screen.CloudSongScreen
import com.jussicodes.music.ui.screen.ExploreScreen
import com.jussicodes.music.ui.screen.LibraryScreen
import com.jussicodes.music.ui.screen.LoginScreen
import com.jussicodes.music.ui.screen.PlaylistScreen
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
    showMiniPlayer: Boolean,
    homePagerState: PagerState,
    onHomePageChange: (Int) -> Unit,
    onHomePageScroll: (Float) -> Unit
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

    SharedTransitionLayout {
        NavHost(
            navController = navController,
            startDestination = Screen.Library.route,
            Modifier
                .background(MaterialTheme.colorScheme.background)
                .windowInsetsPadding(WindowInsets(bottom = bottomPadding + if (showMiniPlayer) MiniPlayerHeight else 0.dp)),
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
                HomePager(
                    navController = navController,
                    pagerState = homePagerState,
                    onPageChange = onHomePageChange,
                    onPageScroll = onHomePageScroll
                )
            }
            composable(Screen.Explore.route) {
                HomePager(
                    navController = navController,
                    pagerState = homePagerState,
                    onPageChange = onHomePageChange,
                    onPageScroll = onHomePageScroll
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
    onPageScroll: (Float) -> Unit
) {
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
