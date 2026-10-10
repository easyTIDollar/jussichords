package com.jussicodes.music.ui.components

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import com.jussicodes.music.R
import com.jussicodes.music.ui.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBar(
    navController: NavHostController,
    @StringRes titleRes: Int,
    /** false 时顶栏透明（用于"我的"/"探索"页叠加在背景图上）。默认保持原来的表面色。 */
    transparent: Boolean = false,
) = TopAppBar(
    title = { Text(stringResource(titleRes)) },
    colors = if (transparent) {
        TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
        )
    } else {
        TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
        )
    },
    actions = {
        IconButton(onClick = {
            navController.navigate(Screen.Search.route)
        }) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = stringResource(
                    R.string.search
                )
            )
        }
        IconButton(onClick = {
            navController.navigate(Screen.Settings.route)
        }) {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = stringResource(
                    R.string.settings
                )
            )
        }
    })
