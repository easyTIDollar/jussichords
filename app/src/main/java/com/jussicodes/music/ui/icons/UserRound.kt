package com.jussicodes.music.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

// Material Icons "person"（outlined，24dp）官方矢量数据。
val UserRound: ImageVector
    get() {
        if (_UserRound != null) {
            return _UserRound!!
        }
        _UserRound = ImageVector.Builder(
            name = "UserRound",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color(0xFF000000))) {
                moveTo(12f, 6f)
                curveTo(13.1f, 6f, 14f, 6.9f, 14f, 8f)
                curveTo(14f, 9.1f, 13.1f, 10f, 12f, 10f)
                curveTo(10.9f, 10f, 10f, 9.1f, 10f, 8f)
                curveTo(10f, 6.9f, 10.9f, 6f, 12f, 6f)
                moveTo(12f, 16f)
                curveTo(14.7f, 16f, 17.8f, 17.29f, 18f, 18f)
                lineTo(6f, 18f)
                curveTo(6.23f, 17.28f, 9.31f, 16f, 12f, 16f)
                moveTo(12f, 4f)
                curveTo(9.79f, 4f, 8f, 5.79f, 8f, 8f)
                curveTo(8f, 10.21f, 9.79f, 12f, 12f, 12f)
                curveTo(14.21f, 12f, 16f, 10.21f, 16f, 8f)
                curveTo(16f, 5.79f, 14.21f, 4f, 12f, 4f)
                close()
                moveTo(12f, 14f)
                curveTo(9.33f, 14f, 4f, 15.34f, 4f, 18f)
                lineTo(4f, 20f)
                lineTo(20f, 20f)
                lineTo(20f, 18f)
                curveTo(20f, 15.34f, 14.67f, 14f, 12f, 14f)
                close()
            }
        }.build()

        return _UserRound!!
    }

@Suppress("ObjectPropertyName")
private var _UserRound: ImageVector? = null
