package com.jussicodes.music.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jussicodes.music.ui.icons.ExploreCompass
import com.jussicodes.music.ui.icons.History
import com.rcmiku.ncmapi.model.SongMusicMemory
import com.rcmiku.ncmapi.model.SongWikiCreatorRole
import com.rcmiku.ncmapi.model.SongWikiData

/**
 * 大图预览底部 Peek 胶囊条
 */
@Composable
fun SongWikiPeekPill(
    songWiki: SongWikiData?,
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val memory = songWiki?.musicMemory
    val summaryText = remember(songWiki, isLoading) {
        when {
            isLoading -> "正在获取歌曲百科与回忆..."
            memory != null && (memory.firstListenDate.isNotBlank() || memory.playCount > 0) -> {
                val date = memory.firstListenDate.substringBefore(' ').trim()
                if (date.isNotBlank() && memory.playCount > 0) {
                    "📍 $date 首听 · 播放 ${memory.playCount} 次"
                } else if (date.isNotBlank()) {
                    "📍 $date 首次听到这首歌"
                } else {
                    "📍 已播放 ${memory.playCount} 次"
                }
            }
            songWiki != null && !songWiki.isEmpty -> "歌曲详情与百科"
            else -> "查看歌曲详情"
        }
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color(0xCC1A1A20),
        border = BorderStroke(1.dp, Color(0x33FFFFFF)),
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = Color.White.copy(alpha = 0.8f)
                )
            } else {
                val icon = if (memory != null && (memory.firstListenDate.isNotBlank() || memory.playCount > 0)) {
                    History
                } else {
                    ExploreCompass
                }
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = summaryText,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * 歌曲百科与回忆坐标底部抽屉（BottomSheet）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongWikiBottomSheet(
    songWiki: SongWikiData?,
    isLoading: Boolean,
    onDismissRequest: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    ResponsiveModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp)
        ) {
            // 顶栏标题
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = ExploreCompass,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "歌曲档案与回忆坐标",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "正在调取歌曲百科...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (songWiki == null || songWiki.isEmpty) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无该歌曲的网易云百科或回忆数据",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // 1. 回忆坐标卡片
                    songWiki.musicMemory?.takeIf { it.firstListenDate.isNotBlank() || it.playCount > 0 }?.let { memory ->
                        item {
                            MusicMemoryCard(memory = memory)
                        }
                    }

                    // 2. 歌曲基础信息卡片
                    item {
                        SongDetailCard(songWiki = songWiki)
                    }

                    // 3. 制作人员列表
                    if (songWiki.creatorRoles.isNotEmpty()) {
                        item {
                            SongCreatorsCard(creatorRoles = songWiki.creatorRoles)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 回忆坐标卡片
 */
@Composable
private fun MusicMemoryCard(
    memory: SongMusicMemory,
    modifier: Modifier = Modifier
) {
    val emphasisStyle = SpanStyle(
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold
    )
    val memoryText = remember(memory) {
        buildMusicMemoryAnnotatedText(memory, emphasisStyle)
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "回忆坐标",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = memoryText,
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 22.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * 歌曲详情信息卡片
 */
@Composable
private fun SongDetailCard(
    songWiki: SongWikiData,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "歌曲详情",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (songWiki.style.isNotBlank()) {
                SongDetailItemRow(label = "曲风", value = songWiki.style)
            }
            if (songWiki.album.isNotBlank()) {
                SongDetailItemRow(label = "专辑", value = songWiki.album)
            }
            if (songWiki.language.isNotBlank()) {
                SongDetailItemRow(label = "语种", value = songWiki.language)
            }
            if (songWiki.publishTime.isNotBlank()) {
                SongDetailItemRow(label = "发行时间", value = songWiki.publishTime)
            }
            if (songWiki.bpm.isNotBlank()) {
                SongDetailItemRow(label = "BPM", value = songWiki.bpm)
            }
            if (songWiki.entertainment.isNotBlank()) {
                SongDetailItemRow(label = "影综", value = songWiki.entertainment)
            }
            if (songWiki.awards.isNotEmpty()) {
                val awardCountText = if (songWiki.awardTotal > songWiki.awards.size) {
                    " (共 ${songWiki.awardTotal} 项)"
                } else ""
                SongDetailItemRow(
                    label = "获奖成就",
                    value = songWiki.awards.joinToString(" / ") + awardCountText
                )
            }
        }
    }
}

/**
 * 制作人员卡片
 */
@Composable
private fun SongCreatorsCard(
    creatorRoles: List<SongWikiCreatorRole>,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "制作团队",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            creatorRoles.forEach { role ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = role.roleName,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(68.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = role.artistNames.joinToString(" / "),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun SongDetailItemRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(68.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * 组装回忆坐标富文本
 */
internal fun buildMusicMemoryAnnotatedText(
    memory: SongMusicMemory,
    emphasis: SpanStyle
): AnnotatedString = buildAnnotatedString {
    val date = memory.firstListenDate.substringBefore(' ').trim()
    if (date.isNotEmpty()) {
        append(date)
        val moment = listOf(memory.season, memory.period)
            .filter { it.isNotEmpty() }
            .joinToString("的")
        if (moment.isNotEmpty()) {
            append("，一个")
            withStyle(emphasis) { append(moment) }
        }
        append("，你第一次听到这首歌。")
    }
    if (memory.playCount > 0) {
        append(if (date.isNotEmpty()) "至今播放 " else "这首歌你已播放 ")
        withStyle(emphasis) { append("${memory.playCount} 次") }
        val analogy = memory.playCountText.trimEnd('。', '，', '！', '.', ',', '!')
        if (analogy.isNotEmpty()) {
            append("，$analogy")
        }
        append("。")
    }
}
