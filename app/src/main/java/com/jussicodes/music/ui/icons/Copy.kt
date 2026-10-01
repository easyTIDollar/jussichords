package com.jussicodes.music.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** 复制（Lucide `copy`）：一键复制房间号 / 邀请链接。 */
val Copy: ImageVector
    get() {
        if (_Copy != null) {
            return _Copy!!
        }
        _Copy = ImageVector.Builder(
            name = "Copy",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color(0xFF000000)),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(8f, 8f)
                horizontalLineToRelative(14f)
                verticalLineToRelative(14f)
                close()
            }
            path(
                stroke = SolidColor(Color(0xFF000000)),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(16f, 4f)
                horizontalLineToRelative(-12f)
                verticalLineToRelative(16f)
                horizontalLineToRelative(12f)
            }
        }.build()

        return _Copy!!
    }

@Suppress("ObjectPropertyName")
private var _Copy: ImageVector? = null
