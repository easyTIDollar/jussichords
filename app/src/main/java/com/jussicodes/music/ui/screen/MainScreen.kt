package com.jussicodes.music.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.layout.onSizeChanged
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
import com.jussicodes.music.constants.DURATION_EXIT
import com.jussicodes.music.constants.DURATION_EXIT_SHORT
import com.jussicodes.music.constants.EmphasizedDecelerateEasing
import com.jussicodes.music.constants.MiniPlayerHeight
import com.jussicodes.music.constants.currentPlayMediaIdKey
import com.jussicodes.music.data.HomeBgEditor
import com.jussicodes.music.ui.components.HomeBackgroundLayer
import com.jussicodes.music.ui.components.HomeBgEditPanel
import com.jussicodes.music.ui.components.rememberHomeBackground
import com.jussicodes.music.ui.components.tabs
import com.jussicodes.music.ui.navigation.NavGraph
import com.jussicodes.music.ui.navigation.Screen
import com.jussicodes.music.extensions.toMediaItemList
import com.jussicodes.music.utils.MenuSnackbarBus
import com.jussicodes.music.utils.MiniPlayerHiddenBus
import com.jussicodes.music.utils.PlayerExpandBus
import com.jussicodes.music.utils.rememberPreference
import com.jussicodes.music.utils.SettingsAppearanceBus
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot
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
    // 贴底输入框的页面（私信聊天页）自己通过 MiniPlayerHiddenBus 声明"我在时需要隐藏小播放条"，
    // 不再用 destination.route 字符串匹配（类型安全参数路由的 route 格式不可靠，已踩坑三次）。
    val miniPlayerHidden by MiniPlayerHiddenBus.hidden.collectAsState()
    val showMiniPlayerChrome = showMiniPlayer && !isRoamRoute && !miniPlayerHidden
    // 主页背景（"我的"/"探索"底层）：提升到 MainScreen 全屏层渲染，布局与 bottomPadding
    // 解耦——路由切换时不再随内容区高度突变重裁切（"背景跟着小播放器下滑"的根因）。
    val homeBackground = rememberHomeBackground()
    // 沉浸式编辑模式（草稿态）：draft 非 null 时主页被手势层接管，参数实时重绘、保存前零 I/O。
    val bgDraft by HomeBgEditor.draft.collectAsState()
    val effectiveHomeBg = bgDraft ?: homeBackground
    val context = LocalContext.current
    var showBgCancelConfirm by remember { mutableStateOf(false) }
    var bgViewW by remember { mutableIntStateOf(0) }
    var bgViewH by remember { mutableIntStateOf(0) }
    // 缩放/位置手动调整模式（编辑面板按钮开关，默认关）：关时不接管任何手势，
    // 主页左右滑 = 正常切「我的·探索」tab；开时居中手势区才消费双指缩放/单指平移。
    var bgAdjustMode by remember { mutableStateOf(false) }
    // 已加载背景图的宽高比（HomeBackgroundLayer onState 回流）：平移边界按图片
    // 真实 Crop 溢出计算，而不是视口近似——横屏图 scale=1 时也能左右移动看全图。
    var bgImgAspect by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(effectiveHomeBg.path) { bgImgAspect = 0f }

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
            // 小播放器贴底间距（与 main 一致的位置逻辑：首页=底栏上方，二级页=贴系统导航栏，
            // 不加额外高度避免和底栏顶开缝），套 200ms 缓入缓出：进出设置页时下滑/回位平滑。
            val targetMiniBottomPadding = when {
                showPlayer -> 0.dp
                showNavigationBar -> padding.calculateBottomPadding()
                else -> WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            }
            // 内容区跟小播放器用同一个贴底间距（无整体垫高）：首页列表的底部避让
            // 由各列表自己在末尾加空白块实现（如专辑墙），这里不再全局 +64dp。
            val miniBottomPadding by animateDpAsState(
                targetValue = targetMiniBottomPadding,
                animationSpec = tween(
                    durationMillis = DURATION_EXIT,
                    easing = FastOutSlowInEasing
                ),
                label = "MiniPlayerBottomPadding"
            )

            // 主页背景（"我的"/"探索"底层）：全屏渲染，高度与路由/bottomPadding 完全解耦——
            // 进二级页不再随内容区高度突变重裁切。
            // 非首页路由时淡出，避免背景色渗入二级页的进入/退出交叉淡入；
            // 二级页各自的实色背景会把它盖住，返回首页再淡入。
            AnimatedVisibility(
                visible = isTabRoute,
                enter = fadeIn(tween(DURATION_ENTER)),
                exit = fadeOut(tween(DURATION_EXIT_SHORT)),
                label = "HomeBackgroundLayer"
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    HomeBackgroundLayer(
                        config = effectiveHomeBg,
                        onImageAspect = { bgImgAspect = it },
                    )
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
                bottomPadding = miniBottomPadding,
                homePagerState = homePagerState,
                onHomePageChange = { homePage = it },
                onHomePageScroll = { homePageScroll = it }
            )

            // 主页背景沉浸式编辑模式：draft 非 null 时接管全屏手势 + 底部参数面板。
            val editingBg = bgDraft
            if (editingBg != null && isTabRoute) {
                // 平移边界：按图片真实宽高比 + 当前缩放算 Crop 溢出（每轴独立）。
                // 旧公式 (scale-1)*min(W,H)/2 以视口近似，横屏图在 scale=1 时边界为 0，
                // 横向被裁的部分完全移不动——改按实际溢出后每张图都能移到看全。
                // 宽高比未回流（图还在加载）时退回旧公式，保证行为不比旧版差。
                fun panLimit(scale: Float): Offset {
                    val fallback = (scale - 1f) * minOf(bgViewW, bgViewH) / 2f
                    if (bgImgAspect <= 0f || bgViewW <= 0 || bgViewH <= 0) {
                        return Offset(fallback, fallback)
                    }
                    val vw = bgViewW.toFloat()
                    val vh = bgViewH.toFloat()
                    val drawnW: Float
                    val drawnH: Float
                    if (vw / vh >= bgImgAspect) {
                        drawnH = vh
                        drawnW = vh * bgImgAspect
                    } else {
                        drawnW = vw
                        drawnH = vw / bgImgAspect
                    }
                    val limX = ((drawnW * scale) - vw).coerceAtLeast(0f) / 2f
                    val limY = ((drawnH * scale) - vh).coerceAtLeast(0f) / 2f
                    return Offset(
                        maxOf(limX, fallback).coerceAtLeast(0f),
                        maxOf(limY, fallback).coerceAtLeast(0f),
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(2f)
                ) {
                    // 手势层（编辑模式）：外层取全屏视区尺寸供 offset 限制计算；
                    // 实际手势只在居中的「手势区」内接管，区外事件穿透（列表/切页/底栏正常）。
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .onSizeChanged {
                                bgViewW = it.width
                                bgViewH = it.height
                            }
                    ) {
                    // 缩放/位置模式：全屏接管手势（屏蔽 tab 横滑/底栏/列表），只能缩放移动图片。
                    // bgAdjustMode=false 时 pointerInput 以该值为 key 重启、直接返回，事件全部穿透。
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(bgAdjustMode) {
                                if (!bgAdjustMode) return@pointerInput
                                awaitEachGesture {
                                    val cur0 = HomeBgEditor.draft.value
                                        ?: return@awaitEachGesture
                                    val pointers = linkedMapOf<Any, Offset>()
                                    var singleStart = Offset.Zero
                                    var singleBaseX = cur0.offsetX
                                    var singleBaseY = cur0.offsetY
                                    var prevCount = 0
                                    var scaleAccum = cur0.scale
                                    var baseOffsetX = cur0.offsetX
                                    var baseOffsetY = cur0.offsetY
                                    var pinchRef = 0f
                                    var pinchMid = Offset.Zero

                                    val first = awaitFirstDown()
                                    pointers[first.id] = first.position
                                    singleStart = first.position
                                    prevCount = 1

                                    while (true) {
                                        val event = awaitPointerEvent()
                                        for (change in event.changes) {
                                            if (change.pressed) pointers[change.id] = change.position
                                            else pointers.remove(change.id)
                                            // 消费事件：锁死底层列表/切页手势（consume 是挂起函数，只能在挂起上下文直接调）
                                            change.consume()
                                        }
                                        if (pointers.isEmpty()) break

                                        val live = HomeBgEditor.draft.value
                                        val values = pointers.values.toList()
                                        when {
                                            values.size >= 2 -> {
                                                // 双指：缩放（指距比例累积）+ 中点平移
                                                val pa = values[0]
                                                val pb = values[1]
                                                val dist = hypot(pa.x - pb.x, pa.y - pb.y)
                                                val midNow = Offset((pa.x + pb.x) / 2f, (pa.y + pb.y) / 2f)
                                                if (pinchRef <= 0f) {
                                                    pinchRef = dist
                                                    pinchMid = midNow
                                                    baseOffsetX = live?.offsetX ?: 0f
                                                    baseOffsetY = live?.offsetY ?: 0f
                                                    scaleAccum = live?.scale ?: 1f
                                                } else {
                                                    scaleAccum = (scaleAccum * dist / pinchRef).coerceIn(1f, 3f)
                                                    baseOffsetX += midNow.x - pinchMid.x
                                                    baseOffsetY += midNow.y - pinchMid.y
                                                    pinchRef = dist
                                                    pinchMid = midNow
                                                    val lim = panLimit(scaleAccum)
                                                    HomeBgEditor.update {
                                                        it.copy(
                                                            scale = scaleAccum,
                                                            offsetX = baseOffsetX.coerceIn(-lim.x, lim.x),
                                                            offsetY = baseOffsetY.coerceIn(-lim.y, lim.y)
                                                        )
                                                    }
                                                }
                                            }
                                            values.size == 1 -> {
                                                val p = values[0]
                                                if (prevCount >= 2) {
                                                    // 从双指退回单指：重置单指基准（否则沿用双指前的旧基准，一拖就跳）
                                                    singleStart = p
                                                    pinchRef = 0f
                                                    live?.let {
                                                        singleBaseX = it.offsetX
                                                        singleBaseY = it.offsetY
                                                    }
                                                }
                                                // 单指：区内两轴自由移动背景（斜拖同时改 XY）；越界幅度受缩放量限制
                                                live?.let {
                                                    val lim = panLimit(it.scale)
                                                    HomeBgEditor.update {
                                                        it.copy(
                                                            offsetX = (singleBaseX + (p.x - singleStart.x))
                                                                .coerceIn(-lim.x, lim.x),
                                                            offsetY = (singleBaseY + (p.y - singleStart.y))
                                                                .coerceIn(-lim.y, lim.y)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        prevCount = values.size
                                    }
                                }
                            }
                    )
                    }
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .windowInsetsPadding(WindowInsets.navigationBars)
                    ) {
                        HomeBgEditPanel(
                            opacity = editingBg.opacity,
                            blur = editingBg.blur,
                            adjustMode = bgAdjustMode,
                            onToggleAdjust = { bgAdjustMode = !bgAdjustMode },
                            onOpacityChanged = { v ->
                                HomeBgEditor.update { it.copy(opacity = v) }
                            },
                            onBlurChanged = { v ->
                                HomeBgEditor.update { it.copy(blur = v) }
                            },
                            onReset = { HomeBgEditor.reset() },
                            onCancel = { showBgCancelConfirm = true },
                            onSave = {
                                HomeBgEditor.commit(context, coroutineScope)
                                showBgCancelConfirm = false
                                bgAdjustMode = false
                                // 保存成功留在主页：设置页在 startHomeBgEdit 时已弹出，这里无需再弹。
                            }
                        )
                    }
                }

                BackHandler(enabled = showBgCancelConfirm) {
                    showBgCancelConfirm = false
                }
                if (showBgCancelConfirm) {
                    AlertDialog(
                        onDismissRequest = { showBgCancelConfirm = false },
                        title = { Text("放弃主页背景修改？") },
                        text = { Text("已调整的背景尚未保存，离开编辑模式会被丢弃。") },
                        confirmButton = {
                            TextButton(onClick = {
                                showBgCancelConfirm = false
                                bgAdjustMode = false
                                HomeBgEditor.cancel(context)
                                // 放弃 → 回设置页并自动打开「外观」弹窗。
                                // 栈手术：把 Library（home）以上全弹掉再 push Settings，
                                // Settings 只可能孤立在 home 之下（launchSingleTop 防叠层），
                                // 之后从 home 按返回不会再冒出残留设置页。
                                android.util.Log.d(
                                    "JussicordsBg",
                                    "cancel nav: ${navController.currentBackStack.value.map { it.destination.route }}"
                                )
                                SettingsAppearanceBus.request()
                                navController.navigate(Screen.Settings.route) {
                                    popUpTo(Screen.Library.route) { inclusive = true }
                                    launchSingleTop = true
                                }
                            }) {
                                Text("放弃")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showBgCancelConfirm = false }) {
                                Text("继续编辑")
                            }
                        }
                    )
                }
                // 系统返回：编辑中先弹确认，不直接丢弃。
                BackHandler(enabled = !showBgCancelConfirm) {
                    showBgCancelConfirm = true
                }
            }
        }
    )
}
