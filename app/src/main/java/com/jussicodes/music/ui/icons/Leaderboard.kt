package com.jussicodes.music.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** 听歌排行：奖杯（Lucide trophy 官方 path 原样转写）。 */
val Leaderboard: ImageVector
    get() {
        if (_Leaderboard != null) {
            return _Leaderboard!!
        }
        _Leaderboard = ImageVector.Builder(
            name = "Leaderboard",
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
                moveTo(10f, 14.66f)
                verticalLineTo(17f)
                arcTo(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = true, 9f, 18f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 7f, 20f)
                verticalLineTo(22f)

                moveTo(14f, 14.66f)
                verticalLineTo(17f)
                arcTo(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, 15f, 18f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 17f, 20f)
                verticalLineTo(22f)

                moveTo(17.916f, 10f)
                horizontalLineTo(19.5f)
                arcTo(2.5f, 2.5f, 0f, isMoreThanHalf = false, isPositiveArc = false, 22f, 7.5f)
                verticalLineTo(5f)
                arcTo(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, 21f, 4f)
                horizontalLineTo(18f)

                moveTo(4f, 22f)
                horizontalLineTo(20f)

                moveTo(6f, 9f)
                arcTo(6f, 6f, 0f, isMoreThanHalf = false, isPositiveArc = false, 18f, 9f)
                verticalLineTo(3f)
                arcTo(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, 17f, 2f)
                horizontalLineTo(7f)
                arcTo(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, 6f, 3f)
                close()

                moveTo(6.084f, 10f)
                horizontalLineTo(4.5f)
                arcTo(2.5f, 2.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2f, 7.5f)
                verticalLineTo(5f)
                arcTo(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = true, 3f, 4f)
                horizontalLineTo(6f)
            }
        }.build()

        return _Leaderboard!!
    }

@Suppress("ObjectPropertyName")
private var _Leaderboard: ImageVector? = null
