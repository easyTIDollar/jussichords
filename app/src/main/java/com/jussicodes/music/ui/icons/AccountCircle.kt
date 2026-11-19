package com.jussicodes.music.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

// Material Icons "account_circle"（outlined，24dp）官方矢量数据。
val AccountCircle: ImageVector
    get() {
        if (_AccountCircle != null) {
            return _AccountCircle!!
        }
        _AccountCircle = ImageVector.Builder(
            name = "AccountCircle",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color(0xFF000000))) {
                moveTo(12f, 2f)
                curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
                curveTo(2f, 17.52f, 6.48f, 22f, 12f, 22f)
                curveTo(17.52f, 22f, 22f, 17.52f, 22f, 12f)
                curveTo(22f, 6.48f, 17.52f, 2f, 12f, 2f)
                close()
                moveTo(7.35f, 18.5f)
                curveTo(8.66f, 17.56f, 10.26f, 17f, 12f, 17f)
                curveTo(13.74f, 17f, 15.34f, 17.56f, 16.65f, 18.5f)
                curveTo(15.34f, 19.44f, 13.74f, 20f, 12f, 20f)
                curveTo(10.26f, 20f, 8.66f, 19.44f, 7.35f, 18.5f)
                close()
                moveTo(18.14f, 17.12f)
                arcTo(9.947f, 9.947f, 0f, false, false, 5.86f, 17.12f)
                arcTo(7.957f, 7.957f, 0f, false, true, 4f, 12f)
                curveTo(4f, 7.58f, 7.58f, 4f, 12f, 4f)
                curveTo(16.42f, 4f, 20f, 7.58f, 20f, 12f)
                curveTo(20f, 13.95f, 19.3f, 15.73f, 18.14f, 17.12f)
                close()
            }
            path(fill = SolidColor(Color(0xFF000000))) {
                moveTo(12f, 6f)
                curveTo(10.07f, 6f, 8.5f, 7.57f, 8.5f, 9.5f)
                curveTo(8.5f, 11.43f, 10.07f, 13f, 12f, 13f)
                curveTo(13.93f, 13f, 15.5f, 11.43f, 15.5f, 9.5f)
                curveTo(15.5f, 7.57f, 13.93f, 6f, 12f, 6f)
                close()
                moveTo(12f, 11f)
                curveTo(11.17f, 11f, 10.5f, 10.33f, 10.5f, 9.5f)
                curveTo(10.5f, 8.67f, 11.17f, 8f, 12f, 8f)
                curveTo(12.83f, 8f, 13.5f, 8.67f, 13.5f, 9.5f)
                curveTo(13.5f, 10.33f, 12.83f, 11f, 12f, 11f)
                close()
            }
        }.build()

        return _AccountCircle!!
    }

@Suppress("ObjectPropertyName")
private var _AccountCircle: ImageVector? = null
