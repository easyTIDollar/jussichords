package com.jussicodes.music.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

// Material Icons "gesture"（outlined，24dp）官方矢量数据。
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
            path(fill = SolidColor(Color(0xFF000000))) {
                moveTo(4.59f, 6.89f)
                curveTo(5.29f, 6.18f, 5.99f, 5.54f, 6.3f, 5.67f)
                curveTo(6.8f, 5.87f, 6.3f, 6.7f, 6f, 7.19f)
                curveTo(5.75f, 7.61f, 3.14f, 11.08f, 3.14f, 13.5f)
                curveTo(3.14f, 14.78f, 3.62f, 15.84f, 4.48f, 16.48f)
                curveTo(5.23f, 17.04f, 6.22f, 17.21f, 7.12f, 16.94f)
                curveTo(8.19f, 16.63f, 9.07f, 15.54f, 10.18f, 14.17f)
                curveTo(11.39f, 12.68f, 13.01f, 10.73f, 14.26f, 10.73f)
                curveTo(15.89f, 10.73f, 15.91f, 11.74f, 16.02f, 12.52f)
                curveTo(12.24f, 13.16f, 10.64f, 16.19f, 10.64f, 17.89f)
                curveTo(10.64f, 19.59f, 12.08f, 20.98f, 13.85f, 20.98f)
                curveTo(15.48f, 20.98f, 18.14f, 19.65f, 18.54f, 14.88f)
                lineTo(21f, 14.88f)
                lineTo(21f, 12.38f)
                lineTo(18.53f, 12.38f)
                curveTo(18.38f, 10.73f, 17.44f, 8.18f, 14.5f, 8.18f)
                curveTo(12.25f, 8.18f, 10.32f, 10.09f, 9.56f, 11.02f)
                curveTo(8.98f, 11.75f, 7.5f, 13.5f, 7.27f, 13.74f)
                curveTo(7.02f, 14.04f, 6.59f, 14.58f, 6.16f, 14.58f)
                curveTo(5.71f, 14.58f, 5.44f, 13.75f, 5.8f, 12.66f)
                curveTo(6.15f, 11.57f, 7.2f, 9.8f, 7.65f, 9.14f)
                curveTo(8.43f, 8f, 8.95f, 7.22f, 8.95f, 5.86f)
                curveTo(8.95f, 3.69f, 7.31f, 3f, 6.44f, 3f)
                curveTo(5.12f, 3f, 3.97f, 4f, 3.72f, 4.25f)
                curveTo(3.36f, 4.61f, 3.06f, 4.91f, 2.84f, 5.18f)
                lineTo(4.59f, 6.89f)
                close()
                moveTo(13.88f, 18.55f)
                curveTo(13.57f, 18.55f, 13.14f, 18.29f, 13.14f, 17.83f)
                curveTo(13.14f, 17.23f, 13.87f, 15.63f, 16.01f, 15.07f)
                curveTo(15.71f, 17.76f, 14.58f, 18.55f, 13.88f, 18.55f)
                close()
            }
        }.build()

        return _Gesture!!
    }

@Suppress("ObjectPropertyName")
private var _Gesture: ImageVector? = null
