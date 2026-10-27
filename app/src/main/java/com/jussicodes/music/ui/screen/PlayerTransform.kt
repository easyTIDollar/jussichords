package com.jussicodes.music.ui.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaMetadata
import androidx.navigation.NavHostController
import com.jussicodes.music.animation.AlbumArtBoundsTransform
import com.jussicodes.music.animation.MiniArtworkPlaceHolder
import com.jussicodes.music.animation.MotionDuration
import com.jussicodes.music.animation.MotionEasing
import com.jussicodes.music.animation.PanelBoundsPlaceHolder
import com.jussicodes.music.animation.PlayerPanelTransition
import com.jussicodes.music.animation.MotionSpec
import com.jussicodes.music.animation.SharedArtwork
import com.jussicodes.music.ui.components.Lyric
import com.jussicodes.music.ui.components.MiniPlayer
import com.jussicodes.music.ui.components.Player
import com.jussicodes.music.ui.components.PlayerQueue

const val FULL_PLAYER = 0
const val PLAY_QUEUE = 1
const val MINI_PLAYER = 2
const val LYRIC_VIEW = 3

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun PlayerTransform(
    onClick: () -> Unit = {},
    onBackPressed: () -> Unit = {},
    mediaMetadata: MediaMetadata,
    position: Long,
    duration: Long,
    onPositionUpdate: (Long) -> Unit,
    navController: NavHostController,
    expandSignal: Long = 0L,
    miniBottomPadding: Dp = 0.dp,
) {

    var show by remember {
        mutableIntStateOf(MINI_PLAYER)
    }

    // 外部信号（如私信一起听进房成功）请求展开全屏播放界面。
    // 只在 expandSignal 相对「已展开过的 token」真实递增时展开，避免进程重建时
    // rememberSaveable 恢复的旧 token（>0）误触展开（PlayerExpandBus 是内存单例，重建后归 0）。
    var lastExpanded by remember { mutableStateOf(expandSignal) }
    LaunchedEffect(expandSignal) {
        if (expandSignal > lastExpanded) {
            lastExpanded = expandSignal
            show = FULL_PLAYER
        }
    }

    val containerBgColor by animateColorAsState(
        targetValue = if (show == MINI_PLAYER) {
            Color.Transparent
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        animationSpec = tween(
            durationMillis = MotionDuration.SharedArtworkBounds,
            easing = MotionEasing.decelerate
        ),
        label = "PlayerContainerBgColor"
    )

    SharedTransitionLayout(
        modifier = Modifier
            .fillMaxSize()
            .background(containerBgColor)
    ) {
        AnimatedContent(
            targetState = show,
            contentAlignment = Alignment.BottomCenter,
            transitionSpec = {
                if (initialState == MINI_PLAYER && targetState == FULL_PLAYER) {
                    (fadeIn(
                        animationSpec = tween(
                            durationMillis = MotionDuration.PanelFadeIn,
                            delayMillis = 60,
                            easing = MotionEasing.decelerate
                        )
                    ) + slideInVertically(
                        animationSpec = tween(
                            durationMillis = MotionDuration.SharedArtworkBounds,
                            easing = MotionEasing.emphasizedPath
                        )
                    ) { fullHeight -> (fullHeight * 0.06f).toInt() }
                    ).togetherWith(
                        fadeOut(
                            animationSpec = tween(
                                durationMillis = MotionDuration.PanelFadeOut,
                                easing = MotionEasing.accelerate
                            )
                        )
                    )
                } else if (initialState == FULL_PLAYER && targetState == MINI_PLAYER) {
                    fadeIn(
                        animationSpec = tween(
                            durationMillis = MotionDuration.PanelFadeIn,
                            delayMillis = MotionDuration.PanelFadeOut,
                            easing = MotionEasing.decelerate
                        )
                    ).togetherWith(
                        fadeOut(
                            animationSpec = tween(
                                durationMillis = MotionDuration.PanelFadeOut,
                                easing = MotionEasing.accelerate
                            )
                        ) + slideOutVertically(
                            animationSpec = tween(
                                durationMillis = MotionDuration.SharedArtworkBounds,
                                easing = MotionEasing.accelerate
                            )
                        ) { fullHeight -> (fullHeight * 0.06f).toInt() }
                    )
                } else {
                    fadeIn(
                        MotionSpec.panelFadeIn<Float>(delayMillis = MotionDuration.ScreenExit)
                    ) togetherWith fadeOut(
                        MotionSpec.panelFadeOut<Float>()
                    )
                }
            }
        ) {
            when (it) {
                FULL_PLAYER -> {
                    Player(
                        navController = navController,
                        mediaMetadata = mediaMetadata,
                        duration = duration,
                        position = position,
                        imageModifier = Modifier.sharedElement(
                            state = rememberSharedContentState(
                                key = SharedArtwork.ofMetadata(mediaMetadata)
                            ),
                            animatedVisibilityScope = this,
                            placeHolderSize = PanelBoundsPlaceHolder,
                            boundsTransform = AlbumArtBoundsTransform,
                        ),
                        modifier = Modifier.sharedBounds(
                            sharedContentState = rememberSharedContentState(
                                key = PlayerPanelTransition.CONTAINER_KEY
                            ),
                            animatedVisibilityScope = this,
                            placeHolderSize = PanelBoundsPlaceHolder,
                            boundsTransform = AlbumArtBoundsTransform,
                            enter = fadeIn(
                                MotionSpec.panelFadeIn<Float>(delayMillis = MotionDuration.ScreenExit)
                            ),
                            exit = fadeOut(
                                MotionSpec.panelFadeOut<Float>()
                            )
                        ),
                        onBackPressed = {
                            show = MINI_PLAYER
                            onBackPressed()
                        }, onClick = {
                            show = LYRIC_VIEW
                        },
                        onContainerClick = {
                            show = PLAY_QUEUE
                        },
                        onPositionUpdate = { position ->
                            onPositionUpdate(position)
                        }
                    )
                }

                PLAY_QUEUE -> {
                    PlayerQueue(
                        mediaMetadata = mediaMetadata,
                        imageModifier = Modifier.sharedElement(
                            state = rememberSharedContentState(
                                key = SharedArtwork.ofMetadata(mediaMetadata)
                            ),
                            animatedVisibilityScope = this,
                            placeHolderSize = PanelBoundsPlaceHolder,
                            boundsTransform = AlbumArtBoundsTransform,
                        ),
                        modifier = Modifier.sharedBounds(
                            sharedContentState = rememberSharedContentState(
                                key = PlayerPanelTransition.CONTAINER_KEY
                            ),
                            animatedVisibilityScope = this,
                            placeHolderSize = PanelBoundsPlaceHolder,
                            boundsTransform = AlbumArtBoundsTransform,
                            enter = fadeIn(
                                MotionSpec.panelFadeIn<Float>(delayMillis = MotionDuration.ScreenExit)
                            ),
                            exit = fadeOut(
                                MotionSpec.panelFadeOut<Float>()
                            )
                        ),
                        onBackPressed = { show = FULL_PLAYER },
                    )
                }

                LYRIC_VIEW -> {
                    Lyric(
                        position = position,
                        mediaMetadata = mediaMetadata,
                        imageModifier = Modifier.sharedElement(
                            state = rememberSharedContentState(
                                key = SharedArtwork.ofMetadata(mediaMetadata)
                            ),
                            animatedVisibilityScope = this,
                            placeHolderSize = PanelBoundsPlaceHolder,
                            boundsTransform = AlbumArtBoundsTransform,
                        ),
                        onBackPressed = { show = FULL_PLAYER },
                    )
                }

                else -> {
                    MiniPlayer(
                        mediaMetadata = mediaMetadata,
                        duration = duration,
                        position = position,
                        onClick = {
                            show = FULL_PLAYER
                            onClick()
                        },
                        imageModifier = Modifier.sharedElement(
                            state = rememberSharedContentState(
                                key = SharedArtwork.ofMetadata(mediaMetadata)
                            ),
                            animatedVisibilityScope = this,
                            placeHolderSize = MiniArtworkPlaceHolder,
                            boundsTransform = AlbumArtBoundsTransform,
                        ),
                        modifier = Modifier
                            .padding(bottom = miniBottomPadding)
                            .sharedBounds(
                            sharedContentState = rememberSharedContentState(
                                key = PlayerPanelTransition.CONTAINER_KEY
                            ),
                            animatedVisibilityScope = this,
                            placeHolderSize = SharedTransitionScope.PlaceHolderSize.contentSize,
                            boundsTransform = AlbumArtBoundsTransform,
                            enter = fadeIn(
                                MotionSpec.panelFadeIn<Float>(delayMillis = MotionDuration.PanelFadeIn)
                            ),
                            exit = fadeOut(
                                MotionSpec.panelFadeOut<Float>()
                            )
                        ),
                    )
                }
            }
        }
    }
}
