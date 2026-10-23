package com.jussicodes.music.ui.components

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jussicodes.music.constants.LandscapeDetailLeftFraction

/**
 * 横屏（矮屏）详情页的「主从双栏」骨架：左侧信息栏 + 右侧列表栏。
 *
 * 竖屏详情页是「上下堆叠」（封面在上、列表在下），横屏必须换成分栏，否则
 * 411dp 的可视高度里封面吃掉大半、列表首屏看不到东西。
 *
 * 比例取 **左 40% / 右 60%**（[LandscapeDetailLeftFraction]）——
 * 左栏只放封面 + 文案 + 3 枚药丸按钮，右栏留给可滚动的曲目列表。
 *
 * 左栏内部结构（自上而下）：
 * 1. 顶部操作按钮行（返回 / 置顶 / 分享等，由调用方给内容）
 * 2. 封面：边长取「剩余高度」与「栏宽」的较小值 —— **不能**用
 *    `fillMaxWidth().aspectRatio(1f)`：左栏 ~330dp 宽会让封面变成 330dp 的
 *    正方形，直接撑破 411dp 高的视口。
 * 3. 标题 / 副标题 / 时间 / 简介
 * 4. 药丸按钮行
 *
 * 右栏：可选的列表头部（如搜索框）+ 纵向滚动的列表。
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
    /** 药丸按钮行。 */
    pillActions: @Composable RowScope.() -> Unit = {},
    /** 右栏列表上方的固定区域（搜索框等）。 */
    listHeader: (@Composable () -> Unit) = {},
    /**
     * 右栏曲目列表内容。
     *
     * 必须是**非 composable** 的扩展函数类型 `LazyListScope.() -> Unit`（与
     * LazyColumn 尾随 lambda 同一形态）：`LazyColumn { }` 的 builder lambda 本身
     * 不是 composable 上下文，只有每个 item 的行内容才是，所以任何 @Composable
     * 形态的 listContent 在这里调用都会报
     * 「@Composable invocations can only happen from the context of a @Composable function」。
     * 行内容（itemsIndexed 的 lambda）自带 @Composable，不受影响。
     */
    listContent: LazyListScope.() -> Unit,
) {
    Row(modifier = modifier.fillMaxSize()) {
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
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                val coverSize = minOf(maxWidth, maxHeight)
                Box(
                    modifier = Modifier.size(coverSize)
                ) { cover() }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                overflow = TextOverflow.Ellipsis,
                maxLines = 2
            )
            subtitle()
            meta?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1
                )
            }
            description?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 3
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                content = pillActions
            )
        }
        Column(
            modifier = Modifier
                .weight(1f - LandscapeDetailLeftFraction)
                .fillMaxHeight()
        ) {
            listHeader()
            // 不要给 LazyColumn 传可空的 state + 具名 content：Compose 里 LazyColumn 有
            // 一个内部重载 `state: LazyListState`（非空）+ 非 composable 的 content，
            // 传可空 state 会命中它并报
            // 「actual LazyListState? but LazyListState was expected」+
            // 「content 期望 Function1<LazyListScope, Unit>」。这里用尾随 lambda，
            // 只匹配公开重载。
            // LazyColumn 的 content 是接收者函数类型 `LazyListScope.() -> Unit`，
            // 尾随 lambda 里不能声明参数（只能隐式用 this）。listContent 是**普通**
            // 函数类型 `(LazyListScope) -> Unit`，所以可以显式传 this。
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                content = listContent
            )
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
