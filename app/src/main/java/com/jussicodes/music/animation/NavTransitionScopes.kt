@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.jussicodes.music.animation

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

val LocalNavSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }
val LocalNavAnimatedContentScope = staticCompositionLocalOf<AnimatedContentScope?> { null }

@Composable
fun navSharedElementModifier(key: Any?): Modifier {
    val sharedTransitionScope = LocalNavSharedTransitionScope.current
    val animatedContentScope = LocalNavAnimatedContentScope.current
    if (key == null || sharedTransitionScope == null || animatedContentScope == null) {
        return Modifier
    }

    return with(sharedTransitionScope) {
        Modifier.sharedElement(
            rememberSharedContentState(key = key),
            animatedVisibilityScope = animatedContentScope,
            boundsTransform = AlbumArtBoundsTransform,
            clipInOverlayDuringTransition = OverlayClip(SharedArtworkShape),
        )
    }
}
