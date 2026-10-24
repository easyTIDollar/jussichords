package com.jussicodes.music.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResponsiveModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState,
    shape: Shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    scrimColor: Color = BottomSheetDefaults.ScrimColor,
    /**
     * 横屏分支是否给内容套一层 [verticalScroll]。
     *
     * 只对「内容是普通 Column / Row、自身不带滚动」的轻量面板开（单选列表等）：
     * 横屏面板高度封顶 360dp，超出的行会被裁掉，套一层滚动即可翻到。
     * **默认 false** —— 内容里有 LazyColumn / LazyRow 的面板（评论、歌曲菜单）
     * 绝对不能再套外层滚动：Lazy 组件在无限高约束下会直接抛
     * 「Vertically scrollable component was measured with an infinity maximum height constraints」。
     */
    landscapeScrollable: Boolean = false,
    content: @Composable () -> Unit
) {
    val isLandscape = LocalContext.current.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    if (isLandscape) {
        Dialog(
            onDismissRequest = onDismissRequest,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            // 尺寸保持既定的 460x360，只在更矮的视口（分屏横条）里向下收缩，
            // 并避让刘海/挖孔（横屏时它们在屏幕侧边）。
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding(),
                contentAlignment = Alignment.Center,
            ) {
                val panelMaxHeight = minOf(360.dp, (maxHeight - 32.dp).coerceAtLeast(160.dp))

                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = containerColor,
                    modifier = modifier
                        .width(460.dp)
                        .heightIn(max = panelMaxHeight)
                ) {
                    val inner = if (landscapeScrollable) {
                        Modifier.verticalScroll(rememberScrollState())
                    } else {
                        Modifier
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 22.dp, vertical = 26.dp)
                            .then(inner)
                    ) {
                        content()
                    }
                }
            }
        }
    } else {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            sheetState = sheetState,
            shape = shape,
            containerColor = containerColor,
            scrimColor = scrimColor,
            content = {
                content()
            }
        )
    }
}
