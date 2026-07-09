package com.jussicodes.music.ui.components

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import com.jussicodes.music.R
import com.jussicodes.music.ui.icons.AccountCircle
import com.jussicodes.music.ui.icons.ExploreCompass
import com.jussicodes.music.ui.navigation.Screen

sealed class BottomBarTab(@StringRes val titleRes: Int, val icon: ImageVector, val route: String) {
    data object Library : BottomBarTab(
        titleRes = R.string.mine,
        icon = AccountCircle,
        route = Screen.Library.route
    )

    data object Explore : BottomBarTab(
        titleRes = R.string.explore,
        icon = ExploreCompass,
        route = Screen.Explore.route
    )
}

val tabs = listOf(
    BottomBarTab.Library,
    BottomBarTab.Explore,
)
