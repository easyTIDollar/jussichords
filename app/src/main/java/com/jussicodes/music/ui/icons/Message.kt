package com.jussicodes.music.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** 消息中心：两个重叠的对话气泡（Lucide messages-square 官方 path 原样转写）。 */
val Message: ImageVector
    get() {
        if (_Message != null) {
            return _Message!!
        }
        _Message = ImageVector.Builder(
            name = "Message",
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
                moveTo(16f, 10f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 14f, 12f)
                horizontalLineTo(6.828f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 5.414f, 12.586f)
                lineTo(3.212f, 14.788f)
                arcTo(0.71f, 0.71f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2f, 14.286f)
                verticalLineTo(4f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 4f, 2f)
                horizontalLineTo(14f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 16f, 4f)
                close()

                moveTo(20f, 9f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 22f, 11f)
                verticalLineTo(21.286f)
                arcTo(0.71f, 0.71f, 0f, isMoreThanHalf = false, isPositiveArc = true, 20.788f, 21.788f)
                lineTo(18.586f, 19.586f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 17.172f, 19f)
                horizontalLineTo(10f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 8f, 17f)
                verticalLineTo(16f)
            }
        }.build()

        return _Message!!
    }

@Suppress("ObjectPropertyName")
private var _Message: ImageVector? = null
