package com.jussicodes.music.ui

import com.jussicodes.music.ui.components.GlassCardColors
import org.junit.Assert.*
import org.junit.Test

class GlassCardColorsTest {
    @Test
    fun `light branch uses light scrim and stronger border`() {
        val c = GlassCardColors.glassCardColors(isLightSurface = true)
        assertTrue("浅色 scrim 应为亮色", c.fill.luminance() > 0.6f)
        assertTrue("浅色描边 alpha 不低于 0.5", c.borderAlpha >= 0.5f)
    }

    @Test
    fun `dark branch uses dark scrim and subtle border`() {
        val c = GlassCardColors.glassCardColors(isLightSurface = false)
        assertTrue("深色 scrim 应为暗色", c.fill.luminance() < 0.4f)
        assertTrue("深色描边 alpha 应低于浅色", c.borderAlpha < 0.5f)
        assertTrue("深色高光 sheen 应比浅色弱", c.sheenAlpha < 0.2f)
    }
}
