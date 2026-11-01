package com.jussicodes.music.ui.components

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import com.jussicodes.music.R
import com.jussicodes.music.constants.floatingNavBarEnabledKey
import com.jussicodes.music.data.download.DownloadStatus
import com.jussicodes.music.data.download.DownloadStore
import com.jussicodes.music.ui.navigation.Screen
import com.jussicodes.music.utils.rememberPreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBar(
    navController: NavHostController,
    @StringRes titleRes: Int,
    /** false 时顶栏透明（用于"我的"/"探索"页叠加在背景图上）。默认保持原来的表面色。 */
    transparent: Boolean = false,
    /** 标题/图标文字色（透明顶栏覆盖主页背景时可选白/黑字）；null = 用默认 onSurface。 */
    contentColor: Color? = null,
) {
    val floatingNavBarEnabled by rememberPreference(floatingNavBarEnabledKey, true)
    val tasks by DownloadStore.tasks.collectAsState()
    val activeDownloadCount = tasks.count {
        it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.RESOLVING || it.status == DownloadStatus.PENDING
    }

    TopAppBar(
        title = { Text(stringResource(titleRes)) },
        colors = if (transparent) {
            TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                titleContentColor = contentColor ?: Color.Unspecified,
                actionIconContentColor = contentColor ?: Color.Unspecified,
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
            if (floatingNavBarEnabled) {
                IconButton(onClick = {
                    navController.navigate(Screen.DownloadManager.route)
                }) {
                    BadgedBox(
                        badge = {
                            if (activeDownloadCount > 0) {
                                Badge {
                                    Text(if (activeDownloadCount > 99) "99+" else activeDownloadCount.toString())
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FileDownload,
                            contentDescription = "下载管理"
                        )
                    }
                }
            } else {
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
            }
        }
    )
}
