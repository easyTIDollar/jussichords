package com.jussicodes.music.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** 「从歌单中移除」垃圾桶图标（本地绘制，避免依赖 material-icons-core 是否含 Outlined.Delete）。 */
val Delete: ImageVector
    get() {
        if (_Delete != null) {
            return _Delete!!
        }
        _Delete = ImageVector.Builder(
            name = "Delete",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color(0xFF5F6368))) {
                moveTo(19f, 6f)
                lineToRelative(0f, -1f)
                curveToRelative(0f, -0.55f, -0.45f, -1f, -1f, -1f)
                horizontalLineTo(7.25f)
                lineToRelative(-0.36f, -0.87f)
                curveToRelative(-0.28f, -0.69f, -0.92f, -1.13f, -1.69f, -1.13f)
                horizontalLineToRelative(-0.42f)
                lineTo(5f, 4f)
                curveToRelative(-0.55f, 0f, -1f, 0.45f, -1f, 1f)
                lineTo(4f, 7f)
                lineTo(3f, 7f)
                curveToRelative(-0.55f, 0f, -1f, 0.45f, -1f, 1f)
                lineTo(2f, 8f)
                lineTo(3f, 9f)
                lineTo(21f, 9f)
                lineTo(22f, 8f)
                lineTo(22f, 7f)
                curveTo(22f, 6.45f, 21.55f, 6f, 21f, 6f)
                close()
                moveTo(5f, 10f)
                verticalLineToRelative(9f)
                curveToRelative(0f, 2.21f, 1.79f, 4f, 4f, 4f)
                horizontalLineToRelative(6f)
                curveToRelative(2.21f, 0f, 4f, -1.79f, 4f, -4f)
                verticalLineTo(10f)
                close()
                moveTo(15f, 19f)
                horizontalLineTo(9f)
                curveTo(8.45f, 19f, 8f, 18.55f, 8f, 18f)
                verticalLineTo(11f)
                horizontalLineTo(16f)
                verticalLineTo(18f)
                curveTo(16f, 18.55f, 15.55f, 19f, 15f, 19f)
                close()
            }
        }.build()

        return _Delete!!
    }

@Suppress("ObjectPropertyName")
private var _Delete: ImageVector? = null
