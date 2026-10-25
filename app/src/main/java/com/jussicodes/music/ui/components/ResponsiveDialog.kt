package com.jussicodes.music.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * 二级弹窗（Dialog / AlertDialog）的横屏适配统一入口。
 *
 * 为什么需要：`AlertDialog` / `window.Dialog` 是**独立窗口**，不吃任何界面缩放或
 * 尺寸预览，Material3 给 AlertDialog 的默认宽度是「屏宽 - 56dp，封顶 560dp」，
 * 高度则完全由内容决定 —— 手机横屏只有 ~411dp 高，带环形图 / 多段设置的弹窗
 * （缓存、外观）内容比屏幕还高，会被直接裁掉且无法滚动。
 *
 * 方案（两种形态，竖屏像素级不变）：
 * - 竖屏：原样委托给 [AlertDialog] / [Dialog]，与改造前逐字节一致；
 * - 横屏：`usePlatformDefaultWidth = false` 的自绘面板 —— 宽度取可用宽的 62%
 *   （夹在 360..640dp），高度封顶为「可用高 - 32dp」，正文区
 *   `weight(1f, fill = false) + verticalScroll`，所以内容再长也只是内部滚动，
 *   面板永不超出屏幕、标题与按钮永远可见。
 *
 * 判定用 `resources.configuration.orientation`（跟 [ResponsiveModalBottomSheet]
 * 同源），不用宽度分级 —— 手机横屏宽度也是 ~900dp，按宽度会被误判成平板。
 */
@Composable
internal fun isLandscapeOrientation(): Boolean =
    LocalContext.current.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

/** 横屏面板宽度：可用宽的 62%，夹在 360..640dp。 */
private fun landscapePanelWidth(availableWidth: Dp) =
    (availableWidth * 0.62f).coerceIn(360.dp, 640.dp)

/**
 * [AlertDialog] 的横屏适配版：参数是 M3 `AlertDialog` 的常用子集（本仓库所有调用点
 * 都只用这几个），竖屏行为完全等价。
 */
@Composable
fun ResponsiveAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    if (!isLandscapeOrientation()) {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            confirmButton = confirmButton,
            modifier = modifier,
            dismissButton = dismissButton,
            title = title,
            text = text,
        )
        return
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            // safeDrawingPadding：横屏时刘海/挖孔在屏幕侧边，不避让的话面板会被切掉一条。
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                // 带输入框的弹窗（Cookie / 定时播放）：横屏下键盘弹出时面板上移，
                // 不被键盘遮住下半截。
                .imePadding(),
            contentAlignment = Alignment.Center,
        ) {
            val panelWidth = landscapePanelWidth(maxWidth)
            val panelMaxHeight = (maxHeight - 32.dp).coerceAtLeast(160.dp)

            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
                modifier = modifier
                    .width(panelWidth)
                    .heightIn(max = panelMaxHeight),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)
                ) {
                    if (title != null) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            // 与 M3 AlertDialog 的标题排版对齐（它内部给 title 槽套的是
                            // headlineSmall + onSurface），否则横屏标题会突然变小一号。
                            CompositionLocalProvider(
                                LocalTextStyle provides MaterialTheme.typography.headlineSmall
                            ) {
                                title()
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                    if (text != null) {
                        // fill = false：内容短时面板不撑满，内容长时封顶并可滚动。
                        Box(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                        ) {
                            CompositionLocalProvider(
                                LocalTextStyle provides MaterialTheme.typography.bodyMedium
                            ) {
                                text()
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        dismissButton?.invoke()
                        confirmButton()
                    }
                }
            }
        }
    }
}

/**
 * `window.Dialog { ScaledDialogContent { Card { ... } } }` 这类自绘卡片的横屏适配外壳。
 *
 * 竖屏：`Dialog { content() }` —— 与改造前完全一致（内层 Card 自己负责形状与配色）。
 * 横屏：透明容器给内容一个「有界宽 + 有界高 + 可滚动」的窗口，长内容不再被裁掉。
 */
@Composable
fun ResponsiveDialogWindow(
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit,
) {
    if (!isLandscapeOrientation()) {
        Dialog(onDismissRequest = onDismissRequest) { content() }
        return
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                // 带输入框的弹窗（Cookie / 定时播放）：横屏下键盘弹出时面板上移，
                // 不被键盘遮住下半截。
                .imePadding(),
            contentAlignment = Alignment.Center,
        ) {
            val panelWidth = landscapePanelWidth(maxWidth)
            val panelMaxHeight = (maxHeight - 32.dp).coerceAtLeast(160.dp)

            // clip 成和内层 Card 同一套圆角：只给滚动容器 heightIn 的话，内容滚出
            // 边界时被按矩形裁掉，卡片上下就变成一条直边（用户说的「被裁切成直边」）。
            // 裁切形状跟 Card 的 extraLarge 对齐后，滚到中途上下仍是圆角。
            Box(
                modifier = Modifier
                    .width(panelWidth)
                    .heightIn(max = panelMaxHeight)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.TopCenter,
            ) {
                content()
            }
        }
    }
}
