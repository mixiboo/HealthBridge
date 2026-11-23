package com.minjun.healthbridge.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.minjun.healthbridge.ui.theme.Glass
import com.minjun.healthbridge.ui.theme.HbColors
import com.minjun.healthbridge.ui.theme.HbShapes
import org.junit.Assert.assertEquals
import org.junit.Test

/** 미리내 크루 디자인 시스템 tokens.json 값과 일치하는지 */
class ThemeTokensTest {
    private fun hex(c: Color) = "#%06x".format(c.toArgb() and 0xFFFFFF)

    @Test fun light_role_colors_match_tokens() {
        val c = HbColors.light
        assertEquals("#36618e", hex(c.primary))
        assertEquals("#d7e3f8", hex(c.secondaryContainer))
        assertEquals("#f8f9ff", hex(c.surface))
        assertEquals("#191c20", hex(c.onSurface))
        assertEquals("#43474e", hex(c.onSurfaceVariant))
        assertEquals("#ba1a1a", hex(c.error))
    }

    @Test fun dark_role_colors_match_tokens() {
        val c = HbColors.dark
        assertEquals("#a1cafd", hex(c.primary))
        assertEquals("#003259", hex(c.onPrimary))
        assertEquals("#111418", hex(c.surface))
        assertEquals("#e1e2e8", hex(c.onSurface))
    }

    @Test fun glass_tokens() {
        assertEquals("#f3f5fc", hex(Glass.light.appBackground))
        assertEquals("#1a2027", hex(Glass.dark.appBackground))
        assertEquals(0.72f, Glass.light.card.alpha, 0.01f)
        assertEquals("#f2f3fa", hex(Glass.light.card.copy(alpha = 1f)))
        assertEquals(0.24f, Glass.dark.cardBorder.alpha, 0.01f)
        assertEquals("#a1cafd", hex(Glass.dark.cardBorder.copy(alpha = 1f)))
    }

    @Test fun radius_tokens() {
        assertEquals(30.dp, HbShapes.base)
        assertEquals(19.5.dp, HbShapes.chip)
        assertEquals(32.dp, HbShapes.sheet)
    }
}
