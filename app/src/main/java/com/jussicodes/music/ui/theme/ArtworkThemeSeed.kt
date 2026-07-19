package com.jussicodes.music.ui.theme

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun rememberArtworkSeed(artwork: Any?): Color? {
    val context = LocalContext.current.applicationContext
    return produceState<Color?>(initialValue = null, artwork, context) {
        if (artwork == null) {
            value = null
            return@produceState
        }

        value = withContext(Dispatchers.IO) {
            try {
                val request = ImageRequest.Builder(context)
                    .data(artwork)
                    .allowHardware(false)
                    .build()
                val result = context.imageLoader.execute(request) as? SuccessResult
                result?.image
                    ?.toBitmap(width = SampleSize, height = SampleSize)
                    ?.let(::extractArtworkSeed)
            } catch (_: Exception) {
                null
            }
        }
    }.value
}

private fun extractArtworkSeed(bitmap: Bitmap): Color? {
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

    val weights = FloatArray(HueBucketCount)
    val red = FloatArray(HueBucketCount)
    val green = FloatArray(HueBucketCount)
    val blue = FloatArray(HueBucketCount)
    val hsv = FloatArray(3)

    pixels.forEach { pixel ->
        if (android.graphics.Color.alpha(pixel) < 160) return@forEach
        android.graphics.Color.colorToHSV(pixel, hsv)
        val saturation = hsv[1]
        val brightness = hsv[2]
        if (brightness < 0.08f || (brightness > 0.97f && saturation < 0.06f)) {
            return@forEach
        }

        val bucket = ((hsv[0] / 360f) * HueBucketCount)
            .toInt()
            .coerceIn(0, HueBucketCount - 1)
        val weight = (0.25f + saturation * 0.75f) * (0.65f + brightness * 0.35f)
        weights[bucket] += weight
        red[bucket] += android.graphics.Color.red(pixel) * weight
        green[bucket] += android.graphics.Color.green(pixel) * weight
        blue[bucket] += android.graphics.Color.blue(pixel) * weight
    }

    val bucket = weights.indices.maxByOrNull(weights::get) ?: return null
    val weight = weights[bucket]
    if (weight <= 0f) return null

    val average = android.graphics.Color.rgb(
        (red[bucket] / weight).toInt().coerceIn(0, 255),
        (green[bucket] / weight).toInt().coerceIn(0, 255),
        (blue[bucket] / weight).toInt().coerceIn(0, 255),
    )
    android.graphics.Color.colorToHSV(average, hsv)
    hsv[1] = hsv[1].coerceIn(0.22f, 0.86f)
    hsv[2] = hsv[2].coerceIn(0.36f, 0.88f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

private const val SampleSize = 72
private const val HueBucketCount = 24
