package com.jussicodes.music.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import com.jussicodes.music.constants.homeBgImageKey
import com.jussicodes.music.constants.themeColorSourceKey
import com.jussicodes.music.utils.rememberEnumPreference
import com.jussicodes.music.utils.rememberPreference
import com.jussicodes.music.widget.WidgetPaletteStore

@Composable
fun JetMeloTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    artwork: Any? = null,
    /** 为 true 时无论「取色来源」设置如何，都优先用封面 artwork 取色（用于局部面板跟随歌曲封面）。 */
    artworkForced: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val dynamicColorAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val themeColorSource by rememberEnumPreference(
        themeColorSourceKey,
        defaultValue = ThemeColorSource.WALLPAPER,
    )
    val useArtwork = artworkForced || themeColorSource == ThemeColorSource.ARTWORK
    val useHomeBg = !artworkForced && themeColorSource == ThemeColorSource.HOME_BG
    val homeBgPath by rememberPreference(homeBgImageKey, "")
    val artworkSeed = rememberArtworkSeed(
        artwork = artwork.takeIf { useArtwork },
    )
    // 仅 HOME_BG 来源时喂真实路径，其余传空串 → 种子 null，回落壁纸配色。
    val homeBgSeed = rememberHomeBgSeed(if (useHomeBg) homeBgPath else "")
    // 浅色/深色两套都算出来：当前用哪套由 darkTheme 决定，另一套给桌面组件在
    // 系统深浅色切换时直接取用（组件不在 App 组合树里，拿不到 MaterialTheme）。
    val schemes = remember(
        artworkSeed,
        homeBgSeed,
        useArtwork,
        useHomeBg,
        dynamicColorAvailable,
        context,
    ) {
        resolveScheme(
            dark = false,
            context = context,
            useArtwork = useArtwork,
            artworkSeed = artworkSeed,
            useHomeBg = useHomeBg,
            homeBgSeed = homeBgSeed,
            dynamicColorAvailable = dynamicColorAvailable,
        ) to resolveScheme(
            dark = true,
            context = context,
            useArtwork = useArtwork,
            artworkSeed = artworkSeed,
            useHomeBg = useHomeBg,
            homeBgSeed = homeBgSeed,
            dynamicColorAvailable = dynamicColorAvailable,
        )
    }
    val colorScheme = if (darkTheme) schemes.second else schemes.first
    // 桌面组件和 App 用同一套最终配色：把两套色板写进组件可同步读取的小表。
    SideEffect {
        WidgetPaletteStore.publish(context.applicationContext, schemes.first, schemes.second)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

/**
 * 主题色板解析：壁纸动态色 / 封面种子色 / 主页背景图种子色 / 默认种子色，四选一。
 * App 与桌面组件共用这一条路径，避免两边各算各的颜色。
 */
private fun resolveScheme(
    dark: Boolean,
    context: Context,
    useArtwork: Boolean,
    artworkSeed: Color?,
    useHomeBg: Boolean,
    homeBgSeed: Color?,
    dynamicColorAvailable: Boolean,
): ColorScheme {
    val generated = when {
        useHomeBg && homeBgSeed != null ->
            if (dark) createDarkScheme(homeBgSeed) else createLightScheme(homeBgSeed)

        useArtwork && artworkSeed != null ->
            if (dark) createDarkScheme(artworkSeed) else createLightScheme(artworkSeed)

        dynamicColorAvailable ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        dark -> createDarkScheme(DefaultThemeSeed)
        else -> createLightScheme(DefaultThemeSeed)
    }
    return generated.withNeutralTextColors()
}

/** 组件侧没有主题上下文时的回落：用同一个封面种子色生成 App 同款色板。 */
internal fun artworkColorScheme(seed: Color, dark: Boolean): ColorScheme =
    (if (dark) createDarkScheme(seed) else createLightScheme(seed)).withNeutralTextColors()

private val DefaultThemeSeed = Color(0xFF6650A4)

private fun ColorScheme.withNeutralTextColors(): ColorScheme {
    val isLight = surface.luminance() > 0.5f
    val mainText = if (isLight) Color(0xFF1B1B1F) else Color(0xFFE8E1E9)
    val secondaryText = if (isLight) Color(0xFF49454F) else Color(0xFFCBC4CC)

    return copy(
        onBackground = mainText,
        onSurface = mainText,
        onSurfaceVariant = secondaryText,
        inverseOnSurface = neutralContentColor(inverseSurface),
        onPrimary = neutralContentColor(primary),
        onPrimaryContainer = neutralContentColor(primaryContainer),
        onSecondary = neutralContentColor(secondary),
        onSecondaryContainer = neutralContentColor(secondaryContainer),
        onTertiary = neutralContentColor(tertiary),
        onTertiaryContainer = neutralContentColor(tertiaryContainer),
        onError = neutralContentColor(error),
        onErrorContainer = neutralContentColor(errorContainer),
    )
}

private fun neutralContentColor(background: Color): Color =
    if (background.luminance() > 0.46f) Color(0xFF17171B) else Color(0xFFF4EFF5)

private fun createLightScheme(seed: Color): ColorScheme {
    val primary = seed.blend(Color.Black, 0.06f)
    val secondary = seed.shiftHue(18f, saturationScale = 0.58f, valueScale = 0.92f)
        .blend(Color(0xFF6A6F7A), 0.18f)
    val tertiary = seed.shiftHue(54f, saturationScale = 0.64f, valueScale = 0.96f)
    val background = Color(0xFFFFFBFF).blend(seed, 0.035f)
    val surface = Color(0xFFFFFBFF).blend(seed, 0.025f)
    val surfaceVariant = Color(0xFFF3EDF7).blend(seed, 0.10f)
    val outline = Color(0xFF7A757F).blend(seed, 0.22f)

    val primaryContainer = seed.blend(Color.White, 0.78f)
    val secondaryContainer = secondary.blend(Color.White, 0.82f)
    val tertiaryContainer = tertiary.blend(Color.White, 0.80f)

    return lightColorScheme(
        primary = primary,
        onPrimary = contentColorFor(primary),
        primaryContainer = primaryContainer,
        onPrimaryContainer = Color(0xFF1C1A22),
        secondary = secondary,
        onSecondary = contentColorFor(secondary),
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = Color(0xFF1B1E25),
        tertiary = tertiary,
        onTertiary = contentColorFor(tertiary),
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = Color(0xFF211B18),
        background = background,
        onBackground = Color(0xFF1B1B1F),
        surface = surface,
        onSurface = Color(0xFF1B1B1F),
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = Color(0xFF49454F),
        outline = outline,
        outlineVariant = outline.blend(Color.White, 0.34f),
        surfaceTint = primary,
        inverseSurface = Color(0xFF303034),
        inverseOnSurface = Color(0xFFF2F0F4),
        inversePrimary = primary.blend(Color.White, 0.40f),
        surfaceBright = surface.blend(Color.White, 0.18f),
        surfaceDim = surface.blend(Color.Black, 0.03f),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = surface.blend(seed, 0.03f),
        surfaceContainer = surface.blend(seed, 0.06f),
        surfaceContainerHigh = surface.blend(seed, 0.09f),
        surfaceContainerHighest = surface.blend(seed, 0.12f),
    )
}

private fun createDarkScheme(seed: Color): ColorScheme {
    val primary = seed.blend(Color.White, 0.26f)
    val secondary = seed.shiftHue(18f, saturationScale = 0.56f, valueScale = 1.05f)
        .blend(Color(0xFFA7AFBD), 0.16f)
    val tertiary = seed.shiftHue(54f, saturationScale = 0.62f, valueScale = 1.10f)
    val background = Color(0xFF121318).blend(seed, 0.11f)
    val surface = Color(0xFF121318).blend(seed, 0.09f)
    val surfaceVariant = Color(0xFF49454F).blend(seed, 0.24f).blend(Color.Black, 0.28f)
    val outline = Color(0xFF948F99).blend(seed, 0.20f)

    val primaryContainer = seed.blend(Color.Black, 0.48f)
    val secondaryContainer = secondary.blend(Color.Black, 0.44f)
    val tertiaryContainer = tertiary.blend(Color.Black, 0.46f)

    return darkColorScheme(
        primary = primary,
        onPrimary = Color(0xFF17151D),
        primaryContainer = primaryContainer,
        onPrimaryContainer = seed.blend(Color.White, 0.74f),
        secondary = secondary,
        onSecondary = Color(0xFF171A20),
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = secondary.blend(Color.White, 0.70f),
        tertiary = tertiary,
        onTertiary = Color(0xFF1C1614),
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = tertiary.blend(Color.White, 0.72f),
        background = background,
        onBackground = Color(0xFFE5E1E6),
        surface = surface,
        onSurface = Color(0xFFE5E1E6),
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = Color(0xFFCAC4D0),
        outline = outline,
        outlineVariant = outline.blend(Color.Black, 0.32f),
        surfaceTint = primary,
        inverseSurface = Color(0xFFE5E1E6),
        inverseOnSurface = Color(0xFF303034),
        inversePrimary = seed.blend(Color.Black, 0.10f),
        surfaceBright = surface.blend(Color.White, 0.08f),
        surfaceDim = surface.blend(Color.Black, 0.14f),
        surfaceContainerLowest = surface.blend(Color.Black, 0.10f),
        surfaceContainerLow = surface.blend(seed, 0.06f),
        surfaceContainer = surface.blend(seed, 0.10f),
        surfaceContainerHigh = surface.blend(seed, 0.14f),
        surfaceContainerHighest = surface.blend(seed, 0.18f),
    )
}

private fun Color.blend(other: Color, ratio: Float): Color =
    Color(ColorUtils.blendARGB(toArgb(), other.toArgb(), ratio.coerceIn(0f, 1f)))

private fun Color.shiftHue(
    degrees: Float,
    saturationScale: Float = 1f,
    valueScale: Float = 1f,
): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), hsv)
    hsv[0] = (hsv[0] + degrees + 360f) % 360f
    hsv[1] = (hsv[1] * saturationScale).coerceIn(0f, 1f)
    hsv[2] = (hsv[2] * valueScale).coerceIn(0f, 1f)
    return Color(android.graphics.Color.HSVToColor((alpha * 255).toInt(), hsv))
}

private fun contentColorFor(background: Color): Color =
    if (background.luminance() > 0.42f) Color(0xFF17171B) else Color.White
