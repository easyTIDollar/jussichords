package com.jussicodes.music.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** 播放历史：带回溯箭头的时钟（Lucide history 官方 path 原样转写）。 */
val History: ImageVector
    get() {
        if (_History != null) {
            return _History!!
        }
        _History = ImageVector.Builder(
            name = "History",
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
                moveTo(3f, 12f)
                arcTo(9f, 9f, 0f, isMoreThanHalf = true, isPositiveArc = false, 12f, 3f)
                arcTo(9.75f, 9.75f, 0f, isMoreThanHalf = false, isPositiveArc = false, 5.26f, 5.74f)
                lineTo(3f, 8f)

                moveTo(3f, 3f)
                verticalLineTo(8f)
                horizontalLineTo(8f)

                moveTo(12f, 7f)
                verticalLineTo(12f)
                lineTo(16f, 14f)
            }
        }.build()

        return _History!!
    }

@Suppress("ObjectPropertyName")
private var _History: ImageVector? = null
