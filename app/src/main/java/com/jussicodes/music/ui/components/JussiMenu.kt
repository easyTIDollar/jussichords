package com.jussicodes.music.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/**
 * 全 App 统一下拉菜单外壳。
 *
 * M3 默认菜单是 4dp 小圆角、无描边的灰浮层，和 App 里 16–20dp 圆角、带描边的卡片语言割裂。
 * 这里统一成：20dp 圆角 + surfaceContainerHigh 底 + 1dp outlineVariant 描边 + 12dp 阴影，
 * 与 SongMenuBottomSheet / 各类卡片同一套取色。
 */
@Composable
fun JussiDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 6.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        offset = offset,
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
        shadowElevation = 12.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        content = content,
    )
}

/**
 * 统一下拉菜单项：左侧 20dp 图标 + 14sp Medium 文字。
 *
 * - [selected] 用于单选/多选当前项：文字与图标走主色，未提供 [trailing] 时右侧补一个勾。
 * - [destructive] 用于删除/取消收藏类操作：文字与图标走 error 色。
 * - [trailing] 保留给 Switch / Checkbox 等原有控件。
 */
@Composable
fun JussiMenuItem(
    text: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    destructive: Boolean = false,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    val disabledAlpha = 0.38f
    val labelColor: Color = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = disabledAlpha)
        destructive -> MaterialTheme.colorScheme.error
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    val iconColor: Color = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = disabledAlpha)
        destructive -> MaterialTheme.colorScheme.error
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val leading: (@Composable () -> Unit)? = icon?.let { vector ->
        @Composable {
            Icon(
                imageVector = vector,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(20.dp),
            )
        }
    }

    val trailingSlot: (@Composable () -> Unit)? = trailing ?: if (selected) {
        @Composable {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    } else null

    DropdownMenuItem(
        text = {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = labelColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        leadingIcon = leading,
        trailingIcon = trailingSlot,
        contentPadding = PaddingValues(start = 14.dp, end = 16.dp),
    )
}

/** 菜单内分组分隔线（带左右内缩，跟菜单项图标列对齐）。 */
@Composable
fun JussiMenuDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}
