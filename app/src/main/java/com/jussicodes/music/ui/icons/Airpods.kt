package com.jussicodes.music.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * AirPods 充电盒轮廓（左右对称的盒身 + 两只入耳式耳机）。
 *
 * 素材：Tabler Icons `device-airpods`（MIT License，24×24 网格、2px 描边，
 * 作者 Paweł Kuna）。只取描边、无填充，形状语言与本项目其它线性图标一致。
 *
 * 用法上与 [Users] / [Crown] 这类图标不同：它不是"点一下的按钮图标"，
 * 而是作为一起听房间成员头像两侧的**装饰性陪衬**（见 ListenTogetherMemberChip）——
 * 尺寸略大于头像、压在头像之下、颜色更淡，模拟参考图中"从头像后面露出来的耳机线"。
 */
val AirpodsCase: ImageVector
    get() {
        if (_AirpodsCase != null) {
            return _AirpodsCase!!
        }
        _AirpodsCase = ImageVector.Builder(
            name = "AirpodsCase",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            // 描边而非填充：与参考图的细线观感一致，也让染色（Icon tint / ColorFilter）更干净。
            path(
                stroke = SolidColor(Color(0xFFFFFFFF)),
                strokeLineWidth = 1.6f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // 左侧：耳机 + 半个盒身
                moveTo(6f, 4f)
                arcToRelative(4f, 4f, 0f, true, true, 4f, 3.8f)
                lineTo(10f, 17.7f)
                arcToRelative(1.5f, 1.5f, 0f, true, true, -3f, 0f)
                lineTo(7f, 12f)
                // 右半支：整段镜像回来
                moveTo(7f, 12f)
                horizontalLineTo(6f)
                arcToRelative(4f, 4f, 0f, true, true, -4f, -3.8f)
                lineTo(2f, 8f)
                arcToRelative(4f, 4f, 0f, true, true, 4f, -4f)
                moveTo(18f, 4f)
                arcToRelative(4f, 4f, 0f, true, false, -4f, 3.8f)
                lineTo(14f, 17.7f)
                arcToRelative(1.5f, 1.5f, 0f, true, false, 3f, 0f)
                lineTo(17f, 12f)
                moveTo(17f, 12f)
                horizontalLineTo(18f)
                arcToRelative(4f, 4f, 0f, true, false, 4f, -3.8f)
                lineTo(22f, 8f)
                arcToRelative(4f, 4f, 0f, true, false, -4f, -4f)
            }
        }.build().also { _AirpodsCase = it }
    }

private var _AirpodsCase: ImageVector? = null