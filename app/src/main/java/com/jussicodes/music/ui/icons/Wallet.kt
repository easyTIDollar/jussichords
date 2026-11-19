package com.jussicodes.music.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

// Material Icons "account_balance_wallet"（outlined，24dp）官方矢量数据。
val Wallet: ImageVector
    get() {
        if (_Wallet != null) {
            return _Wallet!!
        }
        _Wallet = ImageVector.Builder(
            name = "Wallet",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color(0xFF000000))) {
                moveTo(21f, 7.28f)
                lineTo(21f, 5f)
                curveTo(21f, 3.9f, 20.1f, 3f, 19f, 3f)
                lineTo(5f, 3f)
                arcTo(2f, 2f, 0f, false, false, 3f, 5f)
                lineTo(3f, 19f)
                arcTo(2f, 2f, 0f, false, false, 5f, 21f)
                lineTo(19f, 21f)
                curveTo(20.1f, 21f, 21f, 20.1f, 21f, 19f)
                lineTo(21f, 16.72f)
                arcTo(2f, 2f, 0f, false, false, 22f, 15f)
                lineTo(22f, 9f)
                arcTo(2f, 2f, 0f, false, false, 21f, 7.28f)
                close()
                moveTo(20f, 9f)
                lineTo(20f, 15f)
                lineTo(13f, 15f)
                lineTo(13f, 9f)
                lineTo(20f, 9f)
                close()
                moveTo(5f, 19f)
                lineTo(5f, 5f)
                lineTo(19f, 5f)
                lineTo(19f, 7f)
                lineTo(13f, 7f)
                curveTo(11.9f, 7f, 11f, 7.9f, 11f, 9f)
                lineTo(11f, 15f)
                curveTo(11f, 16.1f, 11.9f, 17f, 13f, 17f)
                lineTo(19f, 17f)
                lineTo(19f, 19f)
                lineTo(5f, 19f)
                close()
            }
        }.build()

        return _Wallet!!
    }

@Suppress("ObjectPropertyName")
private var _Wallet: ImageVector? = null
