package com.jussicodes.music.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 耳机线：一条从头像侧后方垂下来的细弧线（参考图里头像左右两侧那两条浅灰线）。
 *
 * 画法刻意是**开口弧**而不是闭合图形——闭合轮廓在小尺寸下会读成"一个物体"
 * （例如先前试过的充电盒轮廓），而开口弧配合"被头像遮住中段"的叠放，
 * 才会读成一根线从圆盘后面绕出来，和参考图一致。
 *
 * 坐标以 24×24 为基准，实际使用时通常纵向拉伸（[Modifier.scale]）到比头像更高，
 * 让线的上下两端都露在头像外面。
 * 左侧那根用 `scale(scaleX = -1f)` 镜像本图得到，不要另画一份。
 */
val HeadphoneWire: ImageVector
    get() {
        if (_HeadphoneWire != null) {
            return _HeadphoneWire!!
        }
        _HeadphoneWire = ImageVector.Builder(
            name = "HeadphoneWire",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            // 只描边、无填充、圆头圆角：细线笔触，和头像那圈描边同一套语言。
            path(
                stroke = SolidColor(Color(0xFFFFFFFF)),
                strokeLineWidth = 1.3f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // 起点在头像右上后方 → 向外鼓出 → 收在左下，形成一段自然的耳机线垂弧。
                // 注意：PathBuilder 没有 cubicTo，三次贝塞尔叫 curveTo(x1,y1,x2,y2,x3,y3)。
                moveTo(20.2f, 2.5f)
                curveTo(24.2f, 6.5f, 24.2f, 14.5f, 20.4f, 18.8f)
                curveTo(18.9f, 20.6f, 17.2f, 21.4f, 15.6f, 21.2f)
            }
        }.build()

        return _HeadphoneWire!!
    }

@Suppress("ObjectPropertyName")
private var _HeadphoneWire: ImageVector? = null