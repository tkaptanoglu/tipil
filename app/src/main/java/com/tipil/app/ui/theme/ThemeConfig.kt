package com.tipil.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class ThemeConfig(
    val lightColors: ColorScheme,
    val darkColors: ColorScheme,
    val typography: Typography,
    val extraColors: ExtraColors,
    val forceDark: Boolean = false   // some themes only make sense as dark
)

data class ExtraColors(
    val readIndicator: Color,
    val unreadIndicator: Color,
    val accentGlow1: Color,
    val accentGlow2: Color,
    val accentGlow3: Color,
    val fictionBadge: Color,
    val nonFictionBadge: Color,
    val signInGradientStart: Color,
    val signInGradientMid: Color,
    val signInGradientEnd: Color,
    val scanLineColor: Color
)

val LocalExtraColors = staticCompositionLocalOf {
    ExtraColors(
        readIndicator = Color(0xFF00E676),
        unreadIndicator = Color(0xFFFFAB40),
        accentGlow1 = Color(0xFFBB86FC),
        accentGlow2 = Color(0xFFE040FB),
        accentGlow3 = Color(0xFF00E5FF),
        fictionBadge = Color(0xFFE040FB),
        nonFictionBadge = Color(0xFF00E5FF),
        signInGradientStart = Color(0xFF0A0515),
        signInGradientMid = Color(0xFF2D1052),
        signInGradientEnd = Color(0xFF1A0A30),
        scanLineColor = Color.White.copy(alpha = 0.03f)
    )
}
