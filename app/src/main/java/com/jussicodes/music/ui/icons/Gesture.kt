package com.jussicodes.music.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** 手势 / 触控（播放器手势教程）。忠实誊自参考示意 glyph，stroke 版，渲染时靠 tint 上色。 */
val Gesture: ImageVector
    get() {
        if (_Gesture != null) {
            return _Gesture!!
        }
        _Gesture = ImageVector.Builder(
            name = "Gesture",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            // 指尖 1 (M9 11 V5 a2 2 0 0 1 4 0 v6)
            path(
                stroke = SolidColor(Color(0xFF000000)),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(9f, 11f)
                verticalLineTo(5f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 4f, 0f)
                verticalLineToRelative(6f)
            }
            // 指尖 2 + 手掌 + 拇指 (M13 10 a2 2 0 0 1 4 0 v4 a6 6 0 0 1 -6 6 c-3 0 -4 -2 -6 -5 l1 -1 l3 2)
            path(
                stroke = SolidColor(Color(0xFF000000)),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(13f, 10f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 4f, 0f)
                verticalLineToRelative(4f)
                arcToRelative(6f, 6f, 0f, isMoreThanHalf = false, isPositiveArc = true, -6f, 6f)
                curveToRelative(-3f, 0f, -4f, -2f, -6f, -5f)
                lineToRelative(1f, -1f)
                lineToRelative(3f, 2f)
            }
        }.build()

        return _Gesture!!
    }

@Suppress("ObjectPropertyName")
private var _Gesture: ImageVector? = null
