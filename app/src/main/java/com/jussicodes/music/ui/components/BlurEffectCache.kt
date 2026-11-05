package com.jussicodes.music.ui.components

import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.Shader as AndroidShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect

/**
 * 缓存昂贵的 RenderEffect 对象，避免动画每一帧都在底层 JNI 分配并销毁新的高斯模糊滤镜。
 * 配合在 Draw 阶段消费 miniPlayerDragProgress 并对物理像素做 roundToInt 量化，
 * 实现真正的实时动态模糊，同时彻底杜绝 Compose 重组与 GPU 冗余管线重建。
 */
class BlurEffectCache {
    private var lastRadiusPx: Float = Float.NaN
    private var cached: RenderEffect? = null

    @RequiresApi(Build.VERSION_CODES.S)
    fun get(radiusPx: Float): RenderEffect? {
        if (radiusPx <= 0f) {
            lastRadiusPx = 0f
            cached = null
            return null
        }
        if (radiusPx != lastRadiusPx) {
            lastRadiusPx = radiusPx
            cached = AndroidRenderEffect
                .createBlurEffect(radiusPx, radiusPx, AndroidShader.TileMode.CLAMP)
                .asComposeRenderEffect()
        }
        return cached
    }
}
