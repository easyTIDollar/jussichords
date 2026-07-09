package com.jussicodes.music.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val PersonalRadio: ImageVector
    get() {
        if (_PersonalRadio != null) {
            return _PersonalRadio!!
        }
        _PersonalRadio = ImageVector.Builder(
            name = "PersonalRadio",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color(0xFF5F6368))) {
                moveTo(4.2f, 21f)
                curveToRelative(-0.62f, 0f, -1.14f, -0.21f, -1.56f, -0.64f)
                curveTo(2.21f, 19.94f, 2f, 19.42f, 2f, 18.8f)
                verticalLineTo(9.2f)
                curveToRelative(0f, -0.62f, 0.21f, -1.14f, 0.64f, -1.56f)
                curveTo(3.06f, 7.21f, 3.58f, 7f, 4.2f, 7f)
                horizontalLineToRelative(9.75f)
                lineToRelative(3.45f, -3.45f)
                curveToRelative(0.2f, -0.2f, 0.43f, -0.3f, 0.7f, -0.3f)
                reflectiveCurveToRelative(0.5f, 0.1f, 0.7f, 0.3f)
                reflectiveCurveToRelative(0.3f, 0.43f, 0.3f, 0.7f)
                reflectiveCurveToRelative(-0.1f, 0.5f, -0.3f, 0.7f)
                lineTo(16.75f, 7f)
                horizontalLineToRelative(3.05f)
                curveToRelative(0.62f, 0f, 1.14f, 0.21f, 1.56f, 0.64f)
                curveToRelative(0.43f, 0.42f, 0.64f, 0.94f, 0.64f, 1.56f)
                verticalLineToRelative(9.6f)
                curveToRelative(0f, 0.62f, -0.21f, 1.14f, -0.64f, 1.56f)
                curveToRelative(-0.42f, 0.43f, -0.94f, 0.64f, -1.56f, 0.64f)
                horizontalLineTo(4.2f)
                close()
                moveTo(4.2f, 19f)
                horizontalLineToRelative(15.6f)
                verticalLineTo(9f)
                horizontalLineTo(4.2f)
                verticalLineToRelative(10f)
                close()
                moveTo(8f, 17f)
                curveToRelative(1.1f, 0f, 2.04f, -0.39f, 2.83f, -1.17f)
                curveTo(11.61f, 15.04f, 12f, 14.1f, 12f, 13f)
                reflectiveCurveToRelative(-0.39f, -2.04f, -1.17f, -2.83f)
                curveTo(10.04f, 9.39f, 9.1f, 9f, 8f, 9f)
                reflectiveCurveToRelative(-2.04f, 0.39f, -2.83f, 1.17f)
                curveTo(4.39f, 10.96f, 4f, 11.9f, 4f, 13f)
                reflectiveCurveToRelative(0.39f, 2.04f, 1.17f, 2.83f)
                curveTo(5.96f, 16.61f, 6.9f, 17f, 8f, 17f)
                close()
                moveTo(16f, 12f)
                horizontalLineToRelative(2f)
                curveToRelative(0.28f, 0f, 0.52f, -0.1f, 0.71f, -0.29f)
                curveTo(18.9f, 11.52f, 19f, 11.28f, 19f, 11f)
                reflectiveCurveToRelative(-0.1f, -0.52f, -0.29f, -0.71f)
                curveTo(18.52f, 10.1f, 18.28f, 10f, 18f, 10f)
                horizontalLineToRelative(-2f)
                curveToRelative(-0.28f, 0f, -0.52f, 0.1f, -0.71f, 0.29f)
                curveTo(15.1f, 10.48f, 15f, 10.72f, 15f, 11f)
                reflectiveCurveToRelative(0.1f, 0.52f, 0.29f, 0.71f)
                curveTo(15.48f, 11.9f, 15.72f, 12f, 16f, 12f)
                close()
                moveTo(16f, 16f)
                horizontalLineToRelative(2f)
                curveToRelative(0.28f, 0f, 0.52f, -0.1f, 0.71f, -0.29f)
                curveTo(18.9f, 15.52f, 19f, 15.28f, 19f, 15f)
                reflectiveCurveToRelative(-0.1f, -0.52f, -0.29f, -0.71f)
                curveTo(18.52f, 14.1f, 18.28f, 14f, 18f, 14f)
                horizontalLineToRelative(-2f)
                curveToRelative(-0.28f, 0f, -0.52f, 0.1f, -0.71f, 0.29f)
                curveTo(15.1f, 14.48f, 15f, 14.72f, 15f, 15f)
                reflectiveCurveToRelative(0.1f, 0.52f, 0.29f, 0.71f)
                curveTo(15.48f, 15.9f, 15.72f, 16f, 16f, 16f)
                close()
                moveTo(8f, 15f)
                curveToRelative(-0.55f, 0f, -1.02f, -0.2f, -1.41f, -0.59f)
                curveTo(6.2f, 14.02f, 6f, 13.55f, 6f, 13f)
                reflectiveCurveToRelative(0.2f, -1.02f, 0.59f, -1.41f)
                curveTo(6.98f, 11.2f, 7.45f, 11f, 8f, 11f)
                reflectiveCurveToRelative(1.02f, 0.2f, 1.41f, 0.59f)
                curveTo(9.8f, 11.98f, 10f, 12.45f, 10f, 13f)
                reflectiveCurveToRelative(-0.2f, 1.02f, -0.59f, 1.41f)
                curveTo(9.02f, 14.8f, 8.55f, 15f, 8f, 15f)
                close()
            }
        }.build()

        return _PersonalRadio!!
    }

@Suppress("ObjectPropertyName")
private var _PersonalRadio: ImageVector? = null
