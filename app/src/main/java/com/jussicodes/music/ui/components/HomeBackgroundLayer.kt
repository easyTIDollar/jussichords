package com.jussicodes.music.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.jussicodes.music.constants.homeBgBlurKey
import com.jussicodes.music.constants.homeBgImageKey
import com.jussicodes.music.constants.homeBgOffsetXKey
import com.jussicodes.music.constants.homeBgOffsetYKey
import com.jussicodes.music.constants.homeBgOpacityKey
import com.jussicodes.music.constants.homeBgScaleKey
import com.jussicodes.music.utils.dataStore
import kotlinx.coroutines.flow.map
import java.io.File

/**
 * 主页背景图（"我的"/"探索"）配置；path 为空 = 未设置，页面回落纯色底。
 * offset 单位为 px（graphicsLayer.translation 是像素），以图片中心为原点。
 */
data class HomeBackgroundConfig(
    val path: String = "",
    /** 缩放倍率，1f = 100%，以图片中心为原点。 */
    val scale: Float = 1f,
    /** 透明度 0..1。 */
    val opacity: Float = 1f,
    /** 高斯模糊半径（dp）。 */
    val blur: Float = 0f,
    /** 水平平移（px）。 */
    val offsetX: Float = 0f,
    /** 垂直平移（px）。 */
    val offsetY: Float = 0f,
) {
    val enabled: Boolean get() = path.isNotBlank()
}

/** 从 DataStore 读取主页背景图配置，写入即响应式更新。 */
@Composable
fun rememberHomeBackground(): HomeBackgroundConfig {
    val context = LocalContext.current
    val flow = remember {
        context.dataStore.data.map { prefs ->
            HomeBackgroundConfig(
                path = prefs[homeBgImageKey] ?: "",
                scale = prefs[homeBgScaleKey] ?: 1f,
                opacity = prefs[homeBgOpacityKey] ?: 1f,
                blur = prefs[homeBgBlurKey] ?: 0f,
                offsetX = prefs[homeBgOffsetXKey] ?: 0f,
                offsetY = prefs[homeBgOffsetYKey] ?: 0f,
            )
        }
    }
    return flow.collectAsState(HomeBackgroundConfig()).value
}

/**
 * 主页底层背景图层：Coil 加载本地文件（[config.path] 为绝对路径），
 * 缩放 / 透明度 / 模糊 / 平移均可调。未设置时整层不渲染。
 */
@Composable
fun HomeBackgroundLayer(
    config: HomeBackgroundConfig,
    modifier: Modifier = Modifier,
) {
    if (!config.enabled) return
    val file = remember(config.path) { File(config.path) }
    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
    ) {
        AsyncImage(
            model = file,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            filterQuality = FilterQuality.High,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    alpha = config.opacity.coerceIn(0f, 1f),
                    scaleX = config.scale,
                    scaleY = config.scale,
                    translationX = config.offsetX,
                    translationY = config.offsetY,
                )
                .then(if (config.blur > 0.01f) Modifier.blur(config.blur.dp) else Modifier)
        )
    }
}
