package com.jussicodes.music.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Pencil: ImageVector
    get() {
        if (_Pencil != null) {
            return _Pencil!!
        }
        _Pencil = ImageVector.Builder(
            name = "Pencil",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f
        ).apply {
            path(fill = SolidColor(Color(0xFF5F6368))) {
                moveTo(120f, 690f)
                verticalLineTo(840f)
                horizontalLineTo(270f)
                lineTo(712f, 398f)
                lineTo(562f, 248f)
                close()
                moveTo(800f, 284f)
                lineTo(716f, 200f)
                lineTo(644f, 272f)
                lineTo(728f, 356f)
                close()
            }
        }.build()

        return _Pencil!!
    }

@Suppress("ObjectPropertyName")
private var _Pencil: ImageVector? = null
