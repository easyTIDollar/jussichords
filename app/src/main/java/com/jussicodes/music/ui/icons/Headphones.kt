package com.jussicodes.music.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 一起听：耳机（🎧）图标。
 *
 * 形状取自 Google Material Icons（Apache-2.0）的 `headphones`，
 * 与项目里其它 960 视口的图标（Repeat / Shuffle / Timer…）同一套绘制风格。
 * 语义：一起听 = 戴上一副耳机同步听歌。
 */
val Headphones: ImageVector
    get() {
        if (_Headphones != null) {
            return _Headphones!!
        }
        _Headphones = ImageVector.Builder(
            name = "Headphones",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f
        ).apply {
            path(fill = SolidColor(Color(0xFF5F6368))) {
                moveTo(480f, 120f)
                curveTo(281.2f, 120f, 120f, 281.2f, 120f, 480f)
                verticalLineTo(760f)
                curveTo(120f, 804f, 156f, 840f, 200f, 840f)
                horizontalLineTo(360f)
                verticalLineTo(520f)
                horizontalLineTo(200f)
                verticalLineTo(480f)
                curveTo(200f, 325.2f, 325.2f, 200f, 480f, 200f)
                curveTo(634.8f, 200f, 760f, 325.2f, 760f, 480f)
                verticalLineTo(520f)
                horizontalLineTo(600f)
                verticalLineTo(840f)
                horizontalLineTo(760f)
                curveTo(804f, 840f, 840f, 804f, 840f, 760f)
                verticalLineTo(480f)
                curveTo(840f, 281.2f, 678.8f, 120f, 480f, 120f)
                close()
            }
        }.build()

        return _Headphones!!
    }

@Suppress("ObjectPropertyName")
private var _Headphones: ImageVector? = null
