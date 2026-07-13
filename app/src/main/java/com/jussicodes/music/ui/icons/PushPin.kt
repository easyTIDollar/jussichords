package com.jussicodes.music.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val PushPin: ImageVector
    get() {
        if (_PushPin != null) {
            return _PushPin!!
        }
        _PushPin = ImageVector.Builder(
            name = "PushPin",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f
        ).apply {
            path(fill = SolidColor(Color(0xFF5F6368))) {
                moveTo(640f, 80f)
                verticalLineToRelative(80f)
                horizontalLineToRelative(-40f)
                verticalLineToRelative(280f)
                lineToRelative(80f, 80f)
                verticalLineToRelative(80f)
                horizontalLineTo(520f)
                verticalLineToRelative(280f)
                horizontalLineToRelative(-80f)
                verticalLineTo(600f)
                horizontalLineTo(280f)
                verticalLineToRelative(-80f)
                lineToRelative(80f, -80f)
                verticalLineTo(160f)
                horizontalLineToRelative(-40f)
                verticalLineTo(80f)
                horizontalLineToRelative(320f)
                close()
                moveTo(394f, 520f)
                horizontalLineToRelative(172f)
                lineToRelative(-46f, -46f)
                verticalLineTo(160f)
                horizontalLineToRelative(-80f)
                verticalLineToRelative(314f)
                lineToRelative(-46f, 46f)
                close()
            }
        }.build()

        return _PushPin!!
    }

@Suppress("ObjectPropertyName")
private var _PushPin: ImageVector? = null
