package com.jussicodes.music.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

// Material Icons "toll"（outlined，24dp）官方矢量数据。
val TablerCoin: ImageVector
    get() {
        if (_TablerCoin != null) {
            return _TablerCoin!!
        }
        _TablerCoin = ImageVector.Builder(
            name = "TablerCoin",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color(0xFF000000))) {
                moveTo(15f, 4f)
                curveTo(10.58f, 4f, 7f, 7.58f, 7f, 12f)
                curveTo(7f, 16.42f, 10.58f, 20f, 15f, 20f)
                curveTo(19.42f, 20f, 23f, 16.42f, 23f, 12f)
                curveTo(23f, 7.58f, 19.42f, 4f, 15f, 4f)
                close()
                moveTo(15f, 18f)
                curveTo(11.69f, 18f, 9f, 15.31f, 9f, 12f)
                curveTo(9f, 8.69f, 11.69f, 6f, 15f, 6f)
                curveTo(18.31f, 6f, 21f, 8.69f, 21f, 12f)
                curveTo(21f, 15.31f, 18.31f, 18f, 15f, 18f)
                close()
                moveTo(3f, 12f)
                arcTo(5.99f, 5.99f, 0f, false, true, 7f, 6.35f)
                lineTo(7f, 4.26f)
                curveTo(3.55f, 5.15f, 1f, 8.27f, 1f, 12f)
                curveTo(1f, 15.73f, 3.55f, 18.85f, 7f, 19.74f)
                lineTo(7f, 17.65f)
                arcTo(5.99f, 5.99f, 0f, false, true, 3f, 12f)
                close()
            }
        }.build()

        return _TablerCoin!!
    }

@Suppress("ObjectPropertyName")
private var _TablerCoin: ImageVector? = null
