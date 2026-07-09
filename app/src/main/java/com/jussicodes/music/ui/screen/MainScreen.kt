package com.jussicodes.music.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.jussicodes.music.LocalPlayerState
import com.jussicodes.music.constants.BottomNavigationHeight
import com.jussicodes.music.constants.DURATION_ENTER
import com.jussicodes.music.constants.EmphasizedDecelerateEasing
import com.jussicodes.music.constants.MiniPlayerHeight
import com.jussicodes.music.constants.currentPlayMediaIdKey
import com.jussicodes.music.ui.components.tabs
import com.jussicodes.music.ui.navigation.NavGraph
import com.jussicodes.music.ui.navigation.Screen
import com.jussicodes.music.utils.rememberPreference
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val isTabRoute =
        currentDestination?.hierarchy?.any { tabs.any { tab -> it.route == tab.route } } == true
    val playerState = LocalPlayerState.current
    var position by rememberSaveable(playerState) {
        mutableLongStateOf(playerState?.player?.currentPosition ?: 0)
    }
    var duration by rememberSaveable(playerState) {
        mutableLongStateOf(playerState?.player?.duration ?: 0)
    }
    val isPlaying = playerState?.isPlaying == true
    val lifecycleOwner = LocalLifecycleOwner.current
    val showMiniPlayer = (playerState?.player?.mediaItemCount ?: 0) != 0
    val currentMediaId = playerState?.currentMediaItem?.mediaId
    var currentPlayMediaId by rememberPreference(currentPlayMediaIdKey, 0)
    var showPlayer by remember { mutableStateOf(false) }
    var homePage by rememberSaveable { mutableIntStateOf(0) }
    var homePageScroll by remember { mutableFloatStateOf(homePage.toFloat()) }
    val homePagerState = rememberPagerState(initialPage = homePage) { tabs.size }
    val coroutineScope = rememberCoroutineScope()
    val isHomeRoute = isTabRoute
    val isRoamRoute = currentDestination?.route == Screen.Roam.route
    val showNavigationBar = isTabRoute
    val showMiniPlayerChrome = showMiniPlayer && !isRoamRoute

    LaunchedEffect(playerState, isPlaying, lifecycleOwner) {
        val player = playerState?.player ?: return@LaunchedEffect
        position = player.currentPosition
        duration = player.duration
        if (!isPlaying) return@LaunchedEffect

        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                position = player.currentPosition
                duration = player.duration
                delay(1000)
            }
        }
    }

    LaunchedEffect(currentMediaId) {
        position = 0L
        currentMediaId?.toLongOrNull()?.let {
            currentPlayMediaId = it
        }
    }

    LaunchedEffect(currentDestination?.route) {
        val targetPage = tabs.indexOfFirst { it.route == currentDestination?.route }
            .takeIf { it >= 0 }

        if (targetPage != null) {
            homePage = targetPage
            homePageScroll = targetPage.toFloat()
            if (homePagerState.currentPage != targetPage || homePagerState.currentPageOffsetFraction != 0f) {
                homePagerState.scrollToPage(targetPage)
            }
        }
    }

    fun navigateRootTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        bottomBar = {
            Column {
                AnimatedVisibility(
                    showNavigationBar,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    NavigationBar(modifier = Modifier.height(BottomNavigationHeight)) {
                        BoxWithConstraints(Modifier.fillMaxSize()) {
                            val itemWidth = maxWidth / tabs.size
                            val indicatorWidth = 72.dp
                            val indicatorOffset = itemWidth * if (isHomeRoute) homePageScroll else homePage.toFloat()

                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .offset(x = indicatorOffset + (itemWidth - indicatorWidth) / 2)
                                    .width(indicatorWidth)
                                    .height(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.secondaryContainer)
                            )

                            Row(Modifier.fillMaxSize()) {
                                tabs.forEach { item ->
                                    val tabIndex = tabs.indexOf(item).coerceAtLeast(0)
                                    val homeProgress = if (isHomeRoute) {
                                        (1f - abs(homePageScroll - tabIndex)).coerceIn(0f, 1f)
                                    } else {
                                        0f
                                    }
                                    val selected = if (isHomeRoute) {
                                        tabs.getOrNull(homePageScroll.roundToInt())?.route == item.route
                                    } else {
                                        currentDestination?.hierarchy?.any { it.route == item.route } == true
                                    }
                                    val iconScale = if (isHomeRoute) {
                                        0.92f + 0.26f * homeProgress
                                    } else if (selected) {
                                        1.18f
                                    } else {
                                        0.92f
                                    }
                                    val iconAlpha = if (isHomeRoute) {
                                        0.68f + 0.32f * homeProgress
                                    } else if (selected) {
                                        1f
                                    } else {
                                        0.68f
                                    }

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxSize()
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null
                                            ) {
                                                val targetPage = tabs.indexOfFirst { it.route == item.route }
                                                if (targetPage < 0) return@clickable

                                                if (isHomeRoute) {
                                                    coroutineScope.launch {
                                                        if (homePagerState.targetPage != targetPage || homePagerState.currentPageOffsetFraction != 0f) {
                                                            homePagerState.animateScrollToPage(
                                                                page = targetPage,
                                                                animationSpec = tween(
                                                                    durationMillis = DURATION_ENTER,
                                                                    easing = EmphasizedDecelerateEasing
                                                                )
                                                            )
                                                        }
                                                    }
                                                } else {
                                                    navigateRootTab(item.route)
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = item.icon,
                                            contentDescription = stringResource(id = item.titleRes),
                                            tint = if (selected) {
                                                MaterialTheme.colorScheme.onSecondaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            modifier = Modifier
                                                .size(28.dp)
                                                .graphicsLayer {
                                                    scaleX = iconScale
                                                    scaleY = iconScale
                                                    alpha = iconAlpha
                                                }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        content = { padding ->
            var bottomPadding = if (!showPlayer) {
                padding.calculateBottomPadding()
            } else {
                0.dp
            }

            if (!showPlayer && !showNavigationBar && showMiniPlayerChrome) {
                bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            }

            Box(
                contentAlignment = Alignment.BottomCenter,
                modifier = Modifier
                    .zIndex(1f)
                    .fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .defaultMinSize(minHeight = MiniPlayerHeight)
                        .windowInsetsPadding(WindowInsets(bottom = bottomPadding)),
                ) {
                    if (showMiniPlayerChrome) playerState?.mediaMetadata?.let {
                        PlayerTransform(
                            mediaMetadata = it,
                            position = position,
                            duration = duration,
                            onBackPressed = { showPlayer = false },
                            onClick = { showPlayer = true },
                            onPositionUpdate = { updatePosition ->
                                position = updatePosition
                            },
                            navController = navController
                        )
                    }
                }
            }

            NavGraph(
                navController = navController,
                bottomPadding = bottomPadding,
                showMiniPlayer = showMiniPlayerChrome,
                homePagerState = homePagerState,
                onHomePageChange = { homePage = it },
                onHomePageScroll = { homePageScroll = it }
            )
        }
    )
}
