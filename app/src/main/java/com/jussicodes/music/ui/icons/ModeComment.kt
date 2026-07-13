package com.jussicodes.music.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val ModeComment: ImageVector
    get() {
        if (_ModeComment != null) {
            return _ModeComment!!
        }
        _ModeComment = ImageVector.Builder(
            name = "ModeComment",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f
        ).apply {
            path(fill = SolidColor(Color(0xFF5F6368))) {
                moveTo(80f, 880f)
                verticalLineTo(160f)
                quadToRelative(0f, -33f, 23.5f, -56.5f)
                reflectiveQuadTo(160f, 80f)
                horizontalLineToRelative(640f)
                quadToRelative(33f, 0f, 56.5f, 23.5f)
                reflectiveQuadTo(880f, 160f)
                verticalLineToRelative(480f)
                quadToRelative(0f, 33f, -23.5f, 56.5f)
                reflectiveQuadTo(800f, 720f)
                horizontalLineTo(240f)
                lineTo(80f, 880f)
                close()
                moveTo(206f, 640f)
                horizontalLineToRelative(594f)
                verticalLineTo(160f)
                horizontalLineTo(160f)
                verticalLineToRelative(526f)
                lineToRelative(46f, -46f)
                close()
            }
        }.build()

        return _ModeComment!!
    }

@Suppress("ObjectPropertyName")
private var _ModeComment: ImageVector? = null
