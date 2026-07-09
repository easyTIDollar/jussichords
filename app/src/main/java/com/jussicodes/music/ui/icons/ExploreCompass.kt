package com.jussicodes.music.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val ExploreCompass: ImageVector
    get() {
        if (_ExploreCompass != null) {
            return _ExploreCompass!!
        }
        _ExploreCompass = ImageVector.Builder(
            name = "ExploreCompass",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color(0xFF5F6368))) {
                moveTo(12f, 2f)
                curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
                reflectiveCurveToRelative(4.48f, 10f, 10f, 10f)
                reflectiveCurveToRelative(10f, -4.48f, 10f, -10f)
                reflectiveCurveTo(17.52f, 2f, 12f, 2f)
                close()
                moveTo(15.9f, 15.9f)
                lineTo(8f, 19f)
                lineToRelative(3.1f, -7.9f)
                lineTo(19f, 8f)
                lineToRelative(-3.1f, 7.9f)
                close()
                moveTo(12f, 13.3f)
                curveToRelative(0.72f, 0f, 1.3f, -0.58f, 1.3f, -1.3f)
                reflectiveCurveToRelative(-0.58f, -1.3f, -1.3f, -1.3f)
                reflectiveCurveToRelative(-1.3f, 0.58f, -1.3f, 1.3f)
                reflectiveCurveToRelative(0.58f, 1.3f, 1.3f, 1.3f)
                close()
            }
        }.build()

        return _ExploreCompass!!
    }

@Suppress("ObjectPropertyName")
private var _ExploreCompass: ImageVector? = null
