package com.minjun.healthbridge.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 미리내 크루 디자인 시스템 (시드 "하늘" #6c9bd2, 리퀴드 글래스) 역할 색 */
object HbColors {
    val light: ColorScheme = lightColorScheme(
        primary = Color(0xFF36618E), onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFD2E4FF), onPrimaryContainer = Color(0xFF1A4975),
        secondary = Color(0xFF535F70), onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFD7E3F8), onSecondaryContainer = Color(0xFF3C4858),
        tertiary = Color(0xFF6B5778), onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFF3DAFF), onTertiaryContainer = Color(0xFF533F5F),
        error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF93000A),
        background = Color(0xFFF3F5FC), onBackground = Color(0xFF191C20),
        surface = Color(0xFFF8F9FF), onSurface = Color(0xFF191C20),
        onSurfaceVariant = Color(0xFF43474E),
        surfaceContainerLow = Color(0xFFF2F3FA), surfaceContainer = Color(0xFFECEEF4),
        surfaceContainerHigh = Color(0xFFE6E8EE),
        outline = Color(0xFF73777F), outlineVariant = Color(0xFFC3C6CF),
        inverseSurface = Color(0xFF2E3135), inverseOnSurface = Color(0xFFEFF0F7), inversePrimary = Color(0xFFA1CAFD),
    )
    val dark: ColorScheme = darkColorScheme(
        primary = Color(0xFFA1CAFD), onPrimary = Color(0xFF003259),
        primaryContainer = Color(0xFF1A4975), onPrimaryContainer = Color(0xFFD2E4FF),
        secondary = Color(0xFFBBC7DB), onSecondary = Color(0xFF253140),
        secondaryContainer = Color(0xFF3C4858), onSecondaryContainer = Color(0xFFD7E3F8),
        tertiary = Color(0xFFD7BEE4), onTertiary = Color(0xFF3B2947),
        tertiaryContainer = Color(0xFF533F5F), onTertiaryContainer = Color(0xFFF3DAFF),
        error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
        background = Color(0xFF1A2027), onBackground = Color(0xFFE1E2E8),
        surface = Color(0xFF111418), onSurface = Color(0xFFE1E2E8),
        onSurfaceVariant = Color(0xFFC3C6CF),
        surfaceContainerLow = Color(0xFF191C20), surfaceContainer = Color(0xFF1D2024),
        surfaceContainerHigh = Color(0xFF272A2F),
        outline = Color(0xFF8D9199), outlineVariant = Color(0xFF43474E),
        inverseSurface = Color(0xFFE1E2E8), inverseOnSurface = Color(0xFF2E3135), inversePrimary = Color(0xFF36618E),
    )
}

/** 리퀴드 글래스 전용 색 (app-background, glass-card, glass-card-border) */
@Immutable
data class GlassColors(val appBackground: Color, val card: Color, val cardBorder: Color) {
    companion object
}

object Glass {
    val light = GlassColors(Color(0xFFF3F5FC), Color(0xFFF2F3FA).copy(alpha = 0.72f), Color(0xFF36618E).copy(alpha = 0.24f))
    val dark = GlassColors(Color(0xFF1A2027), Color(0xFF191C20).copy(alpha = 0.72f), Color(0xFFA1CAFD).copy(alpha = 0.24f))
}

/** radius 토큰 */
object HbShapes {
    val chip = 19.5.dp
    val input = 21.6.dp
    val list = 23.4.dp
    val base = 30.dp
    val sheet = 32.dp
}

val LocalGlass = staticCompositionLocalOf { Glass.light }

private val typography = Typography().let { t ->
    t.copy(
        // v5_theme.dart: 본문 15px / 1.6, 보조 13px / 1.6
        bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal),
        bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 20.8.sp, fontWeight = FontWeight.Normal),
    )
}

private val shapes = Shapes(
    small = RoundedCornerShape(HbShapes.chip),
    medium = RoundedCornerShape(HbShapes.input),
    large = RoundedCornerShape(HbShapes.base),
    extraLarge = RoundedCornerShape(HbShapes.sheet),
)

@Composable
fun HealthBridgeTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalGlass provides if (dark) Glass.dark else Glass.light) {
        MaterialTheme(
            colorScheme = if (dark) HbColors.dark else HbColors.light,
            typography = typography,
            shapes = shapes,
            content = content,
        )
    }
}
