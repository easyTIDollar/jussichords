package com.jussicodes.music.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val NavigationPill: ImageVector
    get() {
        if (_NavigationPill != null) {
            return _NavigationPill!!
        }
        _NavigationPill = ImageVector.Builder(
            name = "NavigationPill",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            // 外圈圆角胶囊药丸 (rx=6, 宽=20, 高=12, x=2, y=6)
            path(
                stroke = SolidColor(Color(0xFF5F6368)),
                strokeLineWidth = 1.8f
            ) {
                moveTo(8f, 6f)
                horizontalLineTo(16f)
                arcTo(6f, 6f, 0f, false, true, 22f, 12f)
                arcTo(6f, 6f, 0f, false, true, 16f, 18f)
                horizontalLineTo(8f)
                arcTo(6f, 6f, 0f, false, true, 2f, 12f)
                arcTo(6f, 6f, 0f, false, true, 8f, 6f)
                close()
            }
            // 内部左侧激活滑块小胶囊
            path(fill = SolidColor(Color(0xFF5F6368))) {
                moveTo(8f, 8.5f)
                horizontalLineTo(9.5f)
                arcTo(3.5f, 3.5f, 0f, false, true, 13f, 12f)
                arcTo(3.5f, 3.5f, 0f, false, true, 9.5f, 15.5f)
                horizontalLineTo(8f)
                arcTo(3.5f, 3.5f, 0f, false, true, 4.5f, 12f)
                arcTo(3.5f, 3.5f, 0f, false, true, 8f, 8.5f)
                close()
            }
            // 内部右侧小圆点
            path(fill = SolidColor(Color(0xFF5F6368))) {
                moveTo(16.5f, 10.5f)
                arcTo(1.5f, 1.5f, 0f, false, true, 18f, 12f)
                arcTo(1.5f, 1.5f, 0f, false, true, 16.5f, 13.5f)
                arcTo(1.5f, 1.5f, 0f, false, true, 15f, 12f)
                arcTo(1.5f, 1.5f, 0f, false, true, 16.5f, 10.5f)
                close()
            }
        }.build()
        return _NavigationPill!!
    }

private var _NavigationPill: ImageVector? = null
