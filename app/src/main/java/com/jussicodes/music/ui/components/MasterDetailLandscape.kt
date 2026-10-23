package com.jussicodes.music.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jussicodes.music.constants.LandscapeDetailLeftFraction

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
     * 药丸按钮内容（**横向**排布，RowScope 接收者 —— 按钮可用 weight 分配宽度）。
     */
    pillActions: @Composable RowScope.() -> Unit = {},
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
                // 关键：顶部对齐（Alignment.Top）—— 右侧文字区高度由内容决定
                // 并被压缩到与封面正方形基本持平，消除封面左上角的突兀空白。
                // 封面本身从顶部开始画，底边即封面正方形的底。
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    // 封面：左半块，正方形 —— 边长取「剩余高度」与「半块宽」的较小值。
                    // BoxWithConstraints 高度跟随内容（封面），不再 fillMaxHeight，
                    // 否则它会把右侧文字区也拉成整列高。
                    BoxWithConstraints(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.TopStart
                    ) {
                        val coverSize = minOf(maxWidth, maxHeight)
                        Box(modifier = Modifier.size(coverSize)) { cover() }
                    }
                    // 信息 + 横排药丸按钮：右半块，顶部对齐
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 2,
                            lineHeight = MaterialTheme.typography.titleMedium.lineHeight,
                            modifier = Modifier.clickable(enabled = hasDescription) {
                                showDescriptionOverlay = true
                            }
                        )
                        subtitle()
                        meta?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 1
                            )
                        }
                        description?.let { desc ->
                            Text(
                                text = desc,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 2,
                                modifier = Modifier.clickable(enabled = hasDescription) {
                                    showDescriptionOverlay = true
                                }
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        // 3 枚药丸按钮：横向并排 —— 主操作「播放」在左（实心高亮），
                        // 「收藏」「评论」描边样式在右。由调用方决定顺序与填充。
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            content = pillActions
                        )
                    }
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

/**
 * 药丸按钮（完全圆角）。`filled = true` 时用主色实心（用于主推的「播放」），
 * 否则用描边（收藏 / 评论）。
 */
@Composable
fun DetailPillButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    filled: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(percent = 50)
    val contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
    if (filled) {
        Button(
            onClick = onClick,
            shape = shape,
            contentPadding = contentPadding,
            modifier = modifier
        ) {
            DetailPillButtonContent(text = text, icon = icon)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            shape = shape,
            contentPadding = contentPadding,
            modifier = modifier
        ) {
            DetailPillButtonContent(text = text, icon = icon)
        }
    }
}

/** 药丸按钮内部：图标 + 间距 + 文字。独立成 @Composable 函数，避免局部 composable lambda。 */
@Composable
private fun DetailPillButtonContent(text: String, icon: ImageVector) {
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
