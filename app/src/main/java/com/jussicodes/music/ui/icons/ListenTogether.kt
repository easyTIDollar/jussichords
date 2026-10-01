package com.jussicodes.music.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** 一起听：五根高低错落的声浪竖条（音频均衡器样式），表示同步聆听。纯直线段。 */
val ListenTogether: ImageVector
    get() {
        if (_ListenTogether != null) {
            return _ListenTogether!!
        }
        _ListenTogether = ImageVector.Builder(
            name = "ListenTogether",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f
        ).apply {
            // 五根竖条，圆角用矩形近似（直线段），高度依次 矮-高-最高-高-矮
            path(fill = SolidColor(Color(0xFF5F6368))) {
                // bar1  x 96-192,  顶部 384 底部 576（矮）
                moveTo(96f, 576f)
                lineTo(96f, 384f)
                horizontalLineTo(192f)
                verticalLineTo(576f)
                close()
                // bar2  x 240-336, 顶部 288 底部 672
                moveTo(240f, 672f)
                lineTo(240f, 288f)
                horizontalLineTo(336f)
                verticalLineTo(672f)
                close()
                // bar3  x 384-480, 顶部 192 底部 768（最高，居中）
                moveTo(384f, 768f)
                lineTo(384f, 192f)
                horizontalLineTo(480f)
                verticalLineTo(768f)
                close()
                // bar4  x 528-624, 顶部 288 底部 672
                moveTo(528f, 672f)
                lineTo(528f, 288f)
                horizontalLineTo(624f)
                verticalLineTo(672f)
                close()
                // bar5  x 672-768, 顶部 384 底部 576（矮）
                moveTo(672f, 576f)
                lineTo(672f, 384f)
                horizontalLineTo(768f)
                verticalLineTo(576f)
                close()
            }
        }.build()

        return _ListenTogether!!
    }

@Suppress("ObjectPropertyName")
private var _ListenTogether: ImageVector? = null
