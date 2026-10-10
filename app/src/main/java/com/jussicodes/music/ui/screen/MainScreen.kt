package com.jussicodes.music.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.jussicodes.music.constants.MediaSessionConstants
import com.jussicodes.music.constants.BottomNavigationHeight
import com.jussicodes.music.constants.DURATION_ENTER
import com.jussicodes.music.constants.DURATION_EXIT_SHORT
import com.jussicodes.music.constants.EmphasizedDecelerateEasing
import com.jussicodes.music.constants.MiniPlayerHeight
import com.jussicodes.music.constants.currentPlayMediaIdKey
import com.jussicodes.music.ui.components.HomeBackgroundLayer
import com.jussicodes.music.ui.components.rememberHomeBackground
import com.jussicodes.music.ui.components.tabs
import com.jussicodes.music.ui.navigation.NavGraph
import com.jussicodes.music.ui.navigation.Screen
import com.jussicodes.music.extensions.toMediaItemList
import com.jussicodes.music.utils.MenuSnackbarBus
import com.jussicodes.music.utils.PlayerExpandBus
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
    // 主页背景（"我的"/"探索"底层）：提升到 MainScreen 全屏层渲染，布局与 bottomPadding
    // 解耦——路由切换时不再随内容区高度突变重裁切（"背景跟着小播放器下滑"的根因）。
    val homeBackground = rememberHomeBackground()

    LaunchedEffect(
        playerState?.currentMediaItem,
        playerState?.mediaItemIndex,
        playerState?.timeline?.windowCount
    ) {
        val player = playerState?.player ?: return@LaunchedEffect
        val sourceName = playerState.currentMediaItem?.mediaMetadata?.extras
            ?.getString(MediaSessionConstants.EXTRA_SOURCE_NAME)
        if (sourceName != PERSONAL_FM_SOURCE) return@LaunchedEffect

        val itemCount = player.mediaItemCount
        if (itemCount == 0 || player.currentMediaItemIndex < itemCount - 2) return@LaunchedEffect

        PersonalFmQueueLoader.load().onSuccess { songs ->
            val existingIds = (0 until player.mediaItemCount)
                .mapTo(mutableSetOf()) { player.getMediaItemAt(it).mediaId }
            val newSongs = songs.filter { it.id.toString() !in existingIds }
            if (newSongs.isNotEmpty()) {
                player.addMediaItems(
                    newSongs.toMediaItemList(
                        sourceName = PERSONAL_FM_SOURCE,
                        sourceType = MediaSessionConstants.SOURCE_TYPE_ROAM
                    )
                )
            }
        }
    }

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

    LaunchedEffect(homePagerState.settledPage) {
        // Keep the navigation route in sync with the settled home page: the
        // pager can settle on 我的/探索 without the route changing, and a stale
        // route made back/return from detail pages land on the wrong home tab.
        if (!isTabRoute) return@LaunchedEffect
        val tabRoute = tabs.getOrNull(homePagerState.settledPage)?.route ?: return@LaunchedEffect
        if (currentDestination?.route != tabRoute) {
            navigateRootTab(tabRoute)
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        coroutineScope.launch {
            MenuSnackbarBus.events.collect { event ->
                val undo = event.onUndo
                val label = event.undoLabel
                if (undo != null && label != null) {
                    // material3 1.3.1 的 showSnackbar 没有 withAction 参数：
                    // 传 actionLabel + Indefinite，等用户点击返回 ActionPerformed 再执行撤销。
                    val result = snackbarHostState.showSnackbar(
                        message = event.message,
                        actionLabel = label,
                        duration = SnackbarDuration.Indefinite,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        undo()
                    }
                } else {
                    snackbarHostState.showSnackbar(event.message)
                }
            }
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
        bottomBar = {
            Column {
                AnimatedVisibility(
                    showNavigationBar,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    // 底栏底色跟随主题 surface（壁纸动态取色时一起变），不再用 M3 默认的
                    // surfaceContainerHigh（它不随动态壁纸色）。
                    NavigationBar(
                        modifier = Modifier.height(BottomNavigationHeight),
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ) {
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
            // 小播放器贴底目标间距（布局值，随路由即时跳变）：
            // - 全屏播放器展开：0（Player 自己占满整屏）
            // - 首页 Tab：底部导航栏（Scaffold 已含）+ 小播放器高度（避让悬浮层）
            // - 其他二级页：仅系统导航栏（贴最底）
            // 小播放器贴底目标间距（基础值，不再额外加小播放器高度——它要紧贴底栏/导航栏上方，
            // 加高度会把小播放器和底栏顶开一道缝）：
            // - 全屏播放器展开：0
            // - 首页 Tab：Scaffold 底栏（导航栏）高度
            // - 其他二级页：仅系统导航栏（贴最底）
            val targetMiniBottomPadding = when {
                showPlayer -> 0.dp
                showNavigationBar -> padding.calculateBottomPadding()
                else -> WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            }
            // 下滑/回位弹簧：与 MiniPlayer 拖拽回位同款参数（MediumBouncy + MediumLow），
            // 套在布局值上，路由切换时小播放器和内容区一起平滑过渡而不是瞬间跳变。
            val miniBottomPadding by animateDpAsState(
                targetValue = targetMiniBottomPadding,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "MiniPlayerBottomPadding"
            )
            // 内容区贴底目标间距：首页 Tab 额外加小播放器高度，保证任意列表滚到底时
            // 最后一段停在小播放器上方不被挡；二级页不加（内容仍到最底）。
            val targetContentBottomPadding = when {
                showPlayer -> 0.dp
                showNavigationBar ->
                    padding.calculateBottomPadding() + if (showMiniPlayerChrome) MiniPlayerHeight else 0.dp
                else -> WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            }
            val contentBottomPadding by animateDpAsState(
                targetValue = targetContentBottomPadding,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "ContentBottomPadding"
            )

            // 主页背景（"我的"/"探索"底层）：全屏渲染，高度与路由/bottomPadding 完全解耦——
            // 进二级页不再随内容区高度突变重裁切（"背景跟着小播放器下滑"的根因）。
            // 非首页路由时淡出，避免背景色渗入二级页的进入/退出交叉淡入；
            // 二级页各自的实色背景会把它盖住，返回首页再淡入。
            AnimatedVisibility(
                visible = isTabRoute,
                enter = fadeIn(tween(DURATION_ENTER)),
                exit = fadeOut(tween(DURATION_EXIT_SHORT)),
                label = "HomeBackgroundLayer"
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    HomeBackgroundLayer(config = homeBackground)
                }
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
                        .windowInsetsPadding(WindowInsets(bottom = miniBottomPadding)),
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
                            navController = navController,
                            expandSignal = PlayerExpandBus.token.collectAsState().value
                        )
                    }
                }
            }

            NavGraph(
                navController = navController,
                bottomPadding = contentBottomPadding,
                homePagerState = homePagerState,
                onHomePageChange = { homePage = it },
                onHomePageScroll = { homePageScroll = it }
            )
        }
    )
}
