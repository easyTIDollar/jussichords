package com.jussicodes.music.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** 消息中心：前后两个分离的对话气泡（右上气泡尾部朝下，左下气泡尾部朝下）。 */
val Message: ImageVector
    get() {
        if (_Message != null) {
            return _Message!!
        }
        _Message = ImageVector.Builder(
            name = "Message",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f
        ).apply {
            path(fill = SolidColor(Color(0xFF5F6368))) {
                // back bubble: rounded rect x 384-880, y 80-416, r 64, tail down from bottom edge
                moveTo(448f, 80f)
                horizontalLineTo(816f)
                quadToRelative(64f, 0f, 64f, 64f)
                verticalLineTo(352f)
                quadToRelative(0f, 64f, -64f, 64f)
                horizontalLineTo(768f)
                lineTo(704f, 512f)
                lineTo(640f, 416f)
                horizontalLineTo(448f)
                quadToRelative(-64f, 0f, -64f, -64f)
                verticalLineTo(144f)
                quadToRelative(0f, -64f, 64f, -64f)
                close()
                // front bubble: rounded rect x 80-576, y 448-784, r 64, tail down from bottom edge
                moveTo(144f, 448f)
                horizontalLineTo(512f)
                quadToRelative(64f, 0f, 64f, 64f)
                verticalLineTo(720f)
                quadToRelative(0f, 64f, -64f, 64f)
                horizontalLineTo(336f)
                lineTo(272f, 880f)
                lineTo(208f, 784f)
                horizontalLineTo(144f)
                quadToRelative(-64f, 0f, -64f, -64f)
                verticalLineTo(512f)
                quadToRelative(0f, -64f, 64f, -64f)
                close()
            }
        }.build()

        return _Message!!
    }

@Suppress("ObjectPropertyName")
private var _Message: ImageVector? = null
