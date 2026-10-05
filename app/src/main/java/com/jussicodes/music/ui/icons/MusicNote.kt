package com.jussicodes.music.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** 音符（音乐源 / 关于）。忠实誊自参考示意 glyph，stroke 版，渲染时靠 tint 上色。 */
val MusicNote: ImageVector
    get() {
        if (_MusicNote != null) {
            return _MusicNote!!
        }
        _MusicNote = ImageVector.Builder(
            name = "MusicNote",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            // 谱线 + 两茎 (M9 18 V6 l11 -2 v12)
            path(
                stroke = SolidColor(Color(0xFF000000)),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(9f, 18f)
                verticalLineTo(6f)
                lineToRelative(11f, -2f)
                verticalLineToRelative(12f)
            }
            // 两个符头 (circle 6.5,18 r2.5 / 17.5,16 r2.5)
            path(
                stroke = SolidColor(Color(0xFF000000)),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(9f, 18f)
                arcToRelative(2.5f, 2.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, -5f, 0f)
                arcToRelative(2.5f, 2.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 5f, 0f)
                close()
            }
            path(
                stroke = SolidColor(Color(0xFF000000)),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(20f, 16f)
                arcToRelative(2.5f, 2.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, -5f, 0f)
                arcToRelative(2.5f, 2.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 5f, 0f)
                close()
            }
        }.build()

        return _MusicNote!!
    }

@Suppress("ObjectPropertyName")
private var _MusicNote: ImageVector? = null
