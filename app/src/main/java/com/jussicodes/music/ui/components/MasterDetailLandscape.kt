package com.jussicodes.music.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jussicodes.music.constants.LandscapeDetailLeftFraction
import com.jussicodes.music.constants.ThumbnailCornerRadius

/**
 * 横屏（矮屏）详情页的「主从双栏」骨架：左侧展示栏 + 右侧曲目列表。
 *
 * 比例取 **左 40% / 右 60%**（[LandscapeDetailLeftFraction]）。
 *
 * 左栏内部是**横向两块**（不是上下堆叠）：
 * 1. 左块：封面 —— 边长取「剩余高度」与「半块宽」的较小值，居中放置。
 *    不能用 fillMaxWidth + aspectRatio(1f)：会撑破 411dp 高的视口。
 * 2. 右块：标题 / 艺术家 / 时间 / 简介 + **竖向**排的 3 枚药丸按钮（收藏 /
 *    播放 / 评论）。简介超过 3 行截断，点击标题或简介弹出全屏简介浮层
 *    （半透明压暗背景，点背景或关闭按钮退出）。
 *
 * 左栏顶部仍有操作按钮行（返回 / 置顶 / 分享等，横跨整栏）。
 * 右栏：可选列表头部（搜索框）+ 纵向滚动列表。
 */
@Composable
fun MasterDetailLandscape(
    modifier: Modifier = Modifier,
    /** 左栏顶部的操作按钮行。 */
    topActions: @Composable RowScope.() -> Unit = {},
    /** 封面内容（调用方自行提供 AsyncImage / 共享元素与点击行为）。 */
    cover: @Composable () -> Unit = {},
    title: String,
    subtitle: (@Composable () -> Unit) = {},
    meta: String? = null,
    description: String? = null,
    /**
     * 药丸按钮内容（**竖向**排布在封面右侧）。无接收者：按钮不参与 weight
     * 分配，各自自适应内容大小。
     */
    pillActions: @Composable () -> Unit = {},
    /** 右栏列表上方的固定区域（搜索框等）。 */
    listHeader: (@Composable () -> Unit) = {},
    /**
     * 右栏曲目列表内容。
     *
     * 必须是**非 composable** 的扩展函数类型 `LazyListScope.() -> Unit`（与
     * LazyColumn 的 content 参数同一形态）：`LazyColumn { }` 的 builder lambda
     * 本身不是 composable 上下文，只有每个 item 的行内容才是，所以任何
     * @Composable 形态的 listContent 在这里都会报
     * 「@Composable invocations can only happen from the context of a @Composable function」。
     * 行内容（itemsIndexed 的 lambda）自带 @Composable，不受影响。
     */
    listContent: LazyListScope.() -> Unit,
) {
    var showDescriptionOverlay by remember { mutableStateOf(false) }
    val hasDescription = !description.isNullOrBlank()

    Box(modifier = modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize()) {
            // ── 左侧展示栏（40%） ──────────────────────────────
            Column(
                modifier = Modifier
                    .weight(LandscapeDetailLeftFraction)
                    .fillMaxHeight()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    content = topActions
                )
                // 整块在左栏剩余空间里垂直居中。封面 + 右侧按钮列等高对齐，
                // 简介在封面下方单独一层（层级弱化），消除大片突兀空白。
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    BoxWithConstraints(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.TopStart
                    ) {
                        // coverSize 在最外层算一次：封面正方形与按钮列共用同一个值，
                        // 按钮列上下两端与封面齐平。
                        val coverSize = minOf(maxWidth, maxHeight) * 0.78f
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                        // 封面 + 覆盖其上的标题：正方形。标题放在封面 Box 内部
                        // 对齐 BottomCenter，只覆盖封面自身的宽度。
                            Box(
                                modifier = Modifier
                                    .size(coverSize)
                                    .clip(RoundedCornerShape(ThumbnailCornerRadius))
                            ) {
                                cover()
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .align(Alignment.BottomCenter)
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    Color.Transparent,
                                                    Color.Black.copy(alpha = 0.72f)
                                                )
                                            )
                                        )
                                        .clickable(enabled = hasDescription) {
                                            showDescriptionOverlay = true
                                        }
                                ) {
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White,
                                        overflow = TextOverflow.Ellipsis,
                                        maxLines = 2,
                                        modifier = Modifier.padding(
                                            horizontal = 10.dp, vertical = 8.dp
                                        )
                                    )
                                }
                            }
                        // 3 枚按钮：竖向排列、无文字（圆角矩形描边），上下两端
                        // 与封面齐平 —— 固定高度 = coverSize + SpaceBetween 均匀撑开。
                            Column(
                                modifier = Modifier.height(coverSize),
                                verticalArrangement = Arrangement.SpaceBetween,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                pillActions()
                            }
                        }
                    }
                    // 简介在封面下方独立一层：弱化层级（小字号 + 低对比度），
                    // 2 行截断，点击展开全屏浮层
                    description?.let { desc ->
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 2,
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .clickable(enabled = hasDescription) {
                                    showDescriptionOverlay = true
                                }
                        )
                    }
                    meta?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 1
                        )
                    }
                    subtitle()
                }
            }
            // ── 右侧曲目列表（60%） ────────────────────────────
            Column(
                modifier = Modifier
                    .weight(1f - LandscapeDetailLeftFraction)
                    .fillMaxHeight()
            ) {
                listHeader()
                // LazyColumn 的 content 是接收者函数类型，直接传值，别包一层 lambda。
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    content = listContent
                )
            }
        }

        // ── 全屏简介浮层：半透明压暗背景 + 居中可滚动简介 ──
        AnimatedVisibility(
            visible = showDescriptionOverlay,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable { showDescriptionOverlay = false },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 64.dp, vertical = 32.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { showDescriptionOverlay = false }) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "关闭",
                                tint = Color.White
                            )
                        }
                    }
                    description?.let { desc ->
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.92f),
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState())
                                .padding(top = 12.dp)
                        )
                    }
                }
            }
        }
    }
}

/** 按钮排的标准高度：圆形图标按钮的直径 = 胶囊按钮的高度，两者等高对齐。 */
val DetailActionRowHeight = 36.dp

/**
 * 主操作胶囊按钮（「播放」）：浅绿（primaryContainer）填充高亮，图标 + 文字，
 * 宽度**自适应内容** —— 不做 weight/弹性伸缩，永远是舒展的药丸形。
 */
@Composable
fun DetailPillButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(percent = 50),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
        modifier = modifier.height(DetailActionRowHeight)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * 次要操作圆角矩形线框图标按钮（「已收藏」/「评论」）：无文字，深底细描边，
 * 尺寸与主胶囊等高 [DetailActionRowHeight] 的正方形（圆角矩形）。
 */
@Composable
fun DetailCircleIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .size(DetailActionRowHeight)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                shape = shape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
    }
}
