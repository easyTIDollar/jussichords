package com.jussicodes.music.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

// material Symbols "Settings" (filled, 24px)，齿轮。path 由官方 SVG（@material-design-icons/svg filled/settings.svg）转换。
val SettingsGear: ImageVector
    get() {
        if (_SettingsGear != null) {
            return _SettingsGear!!
        }
        _SettingsGear = ImageVector.Builder(
            name = "SettingsGear",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color(0xFF000000))) {
                moveTo(19.14f, 12.94f)
                cubicTo(19.18f, 12.64f, 19.2f, 12.33f, 19.2f, 12f)
                cubicTo(19.2f, 11.68f, 19.18f, 11.36f, 19.13f, 11.06f)
                lineTo(21.16f, 9.48f)
                arcTo(21.1f, 8.76f, 22.08f, 9.74f, 101.13f, -180f, false)
                lineTo(19.36f, 5.55f)
                arcTo(18.71f, 4.6f, 19.68f, 5.58f, 20.45f, -180f, false)
                lineTo(16.38f, 6.29f)
                cubicTo(15.88f, 5.91f, 15.35f, 5.59f, 14.76f, 5.35f)
                lineTo(14.4f, 2.81f)
                arcTo(13.91f, 1.84f, 14.88f, 2.81f, 40.5f, -180f, false)
                lineTo(10.08f, 2.4f)
                cubicTo(9.84f, 2.4f, 9.65f, 2.57f, 9.61f, 2.81f)
                lineTo(9.25f, 5.35f)
                cubicTo(8.66f, 5.59f, 8.12f, 5.92f, 7.63f, 6.29f)
                lineTo(5.24f, 5.33f)
                cubicTo(5.02f, 5.25f, 4.77f, 5.33f, 4.65f, 5.55f)
                lineTo(2.74f, 8.87f)
                cubicTo(2.62f, 9.08f, 2.66f, 9.34f, 2.86f, 9.48f)
                lineTo(4.89f, 11.06f)
                cubicTo(4.84f, 11.36f, 4.8f, 11.69f, 4.8f, 12f)
                cubicTo(4.8f, 12.31f, 4.82f, 12.64f, 4.87f, 12.94f)
                lineTo(2.84f, 14.52f)
                arcTo(1.92f, 14.26f, 2.9f, 15.24f, 281.13f, -180f, false)
                lineTo(4.64f, 18.45f)
                cubicTo(4.76f, 18.67f, 5.01f, 18.74f, 5.23f, 18.67f)
                lineTo(7.62f, 17.71f)
                cubicTo(8.12f, 18.09f, 8.65f, 18.41f, 9.24f, 18.65f)
                lineTo(9.6f, 21.19f)
                cubicTo(9.65f, 21.43f, 9.84f, 21.6f, 10.08f, 21.6f)
                lineTo(13.92f, 21.6f)
                cubicTo(14.16f, 21.6f, 14.36f, 21.43f, 14.39f, 21.19f)
                lineTo(14.75f, 18.65f)
                cubicTo(15.34f, 18.41f, 15.88f, 18.09f, 16.37f, 17.71f)
                lineTo(18.76f, 18.67f)
                cubicTo(18.98f, 18.75f, 19.23f, 18.67f, 19.35f, 18.45f)
                lineTo(21.27f, 15.13f)
                cubicTo(21.39f, 14.91f, 21.34f, 14.66f, 21.15f, 14.52f)
                lineTo(19.14f, 12.94f)
                close()
                moveTo(12f, 15.6f)
                cubicTo(10.02f, 15.6f, 8.4f, 13.98f, 8.4f, 12f)
                cubicTo(8.4f, 10.02f, 10.02f, 8.4f, 12f, 8.4f)
                cubicTo(13.98f, 8.4f, 15.6f, 10.02f, 15.6f, 12f)
                cubicTo(15.6f, 13.98f, 13.98f, 15.6f, 12f, 15.6f)
                close()
            }
        }.build()

        return _SettingsGear!!
    }

@Suppress("ObjectPropertyName")
private var _SettingsGear: ImageVector? = null
