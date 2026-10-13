package com.jussicodes.music.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** LX 脚本音源：文档 + 代码括号，本地绘制（material-icons-core 不含 Code/Script）。 */
val Script: ImageVector
    get() {
        if (_Script != null) {
            return _Script!!
        }
        _Script = ImageVector.Builder(
            name = "Script",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color(0xFF000000)),
                strokeLineWidth = 1.8f,
                strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
                strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
            ) {
                // 文档外框（右上角折角）
                moveTo(6f, 3f)
                horizontalLineTo(13f)
                lineTo(18f, 8f)
                verticalLineTo(19f)
                quadTo(18f, 21f, 16f, 21f)
                horizontalLineTo(6f)
                quadTo(4f, 21f, 4f, 19f)
                verticalLineTo(5f)
                quadTo(4f, 3f, 6f, 3f)
                close()
                // 折角
                moveTo(13f, 3f)
                verticalLineTo(6f)
                horizontalLineTo(18f)
            }
            // 代码括号
            path(
                stroke = SolidColor(Color(0xFF000000)),
                strokeLineWidth = 1.8f,
                strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
                strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
            ) {
                moveTo(10f, 12f)
                lineTo(8f, 14f)
                lineTo(10f, 16f)
                moveTo(14f, 12f)
                lineTo(16f, 14f)
                lineTo(14f, 16f)
            }
        }.build()

        return _Script!!
    }

@Suppress("ObjectPropertyName")
private var _Script: ImageVector? = null