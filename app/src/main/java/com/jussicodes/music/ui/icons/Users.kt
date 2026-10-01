package com.jussicodes.music.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** 人群（Lucide `users`）：双人剪影，表示房间人数。 */
val Users: ImageVector
    get() {
        if (_Users != null) {
            return _Users!!
        }
        _Users = ImageVector.Builder(
            name = "Users",
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
                moveTo(16f, 21f)
                lineTo(16f, 19f)
                arcToRelative(4f, 4f, 0f, false, false, -4f, -4f)
                lineTo(6f, 15f)
                arcToRelative(4f, 4f, 0f, false, false, -4f, 4f)
                lineTo(2f, 21f)
            }
            path(
                stroke = SolidColor(Color(0xFF000000)),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(16f, 3.128f)
                arcToRelative(4f, 4f, 0f, false, true, 0f, 7.744f)
            }
            path(
                stroke = SolidColor(Color(0xFF000000)),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(22f, 21f)
                lineTo(22f, 19f)
                arcToRelative(4f, 4f, 0f, false, false, -3f, -3.87f)
            }
            path(
                stroke = SolidColor(Color(0xFF000000)),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(13f, 7f)
                arcToRelative(4f, 4f, 0f, false, true, -8f, 0f)
                arcToRelative(4f, 4f, 0f, false, true, 8f, 0f)
            }
        }.build()

        return _Users!!
    }

@Suppress("ObjectPropertyName")
private var _Users: ImageVector? = null
