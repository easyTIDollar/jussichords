package com.jussicodes.music.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.jussicodes.music.LocalPlayerController
import com.jussicodes.music.LocalPlayerState
import com.jussicodes.music.constants.MediaSessionConstants
import com.jussicodes.music.R
import com.jussicodes.music.extensions.playMediaAtId
import com.jussicodes.music.extensions.setPlaylist
import com.jussicodes.music.ui.components.SongListItem
import com.jussicodes.music.ui.components.SwipableTabPager
import com.jussicodes.music.viewModel.RecordScreenViewModel
import com.rcmiku.ncmapi.api.account.SongRecordType
import com.rcmiku.ncmapi.model.RecordSong
import kotlinx.coroutines.flow.collectLatest

/**
 * 听歌排行：两个 tab（最近一周 / 所有时间）复用 SwipableTabPager 跟手滑动 tab（与关注列表一致）。
 * 数据只拉一次，两页共用同一 RecordResponse，切页只是换 weekData / allData。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordScreen(
    navController: NavHostController,
    recordScreenViewModel: RecordScreenViewModel = hiltViewModel()
) {
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })
    val songRecord by recordScreenViewModel.songRecord.collectAsState()
    val mediaController = LocalPlayerController.current.controller
    val playerState = LocalPlayerState.current
    val isPlaying = playerState?.isPlaying == true
    val currentMediaId = playerState?.currentMediaItem?.mediaId?.toLongOrNull()

    val pageTitles = listOf(stringResource(R.string.week_record), stringResource(R.string.all_record))

    // 切页时按需取数（VM 内 collectLatest 自动发请求）
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collectLatest { page ->
            recordScreenViewModel.updateSongRecordType(
                if (page == 0) SongRecordType.WEEK else SongRecordType.ALL
            )
        }
    }

    // 各页要展示的数据
    fun recordDataForPage(page: Int): List<RecordSong> =
        songRecord?.let { record -> if (page == 0) record.weekData else record.allData }.orEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.record))
                },
                navigationIcon = {
                    IconButton(onClick = {
                        navController.navigateUp()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null
                        )
                    }
                },
            )
        },
    ) { padding ->
        SwipableTabPager(
            titles = pageTitles,
            pagerState = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) { page ->
            val data = recordDataForPage(page)
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                itemsIndexed(data) { index, item ->
                    SongListItem(
                        song = item.song,
                        isPlaying = isPlaying,
                        isActive = currentMediaId == item.song.id,
                        songIndex = index + 1,
                        modifier = Modifier.clickable {
                            mediaController?.setPlaylist(
                                data.map { it.song },
                                sourceName = "听歌排行",
                                sourceType = MediaSessionConstants.SOURCE_TYPE_RECORD,
                                navId = recordScreenViewModel.navUid ?: 0L
                            )
                            mediaController?.playMediaAtId(item.song.id)
                        },
                        trailingContent = {
                            Text(
                                text = stringResource(R.string.play_count, item.playCount),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    )
                }
            }
        }
    }
}
