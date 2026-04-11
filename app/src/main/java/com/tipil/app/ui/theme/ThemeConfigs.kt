package com.tipil.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

fun themeConfigFor(theme: AppTheme): ThemeConfig = when (theme) {
    AppTheme.NEON_METAL -> neonMetalConfig
    AppTheme.RETRO_C64 -> retroC64Config
    AppTheme.MOTORHEAD -> motorheadConfig
    AppTheme.BARBIE -> barbieConfig
    AppTheme.OCEAN_DEPTHS -> oceanDepthsConfig
    AppTheme.FOREST_INK -> forestInkConfig
    AppTheme.SOLAR_FLARE -> solarFlareConfig
}

// ═══════════════════════════════════════════════════════════
//  1. NEON METAL — Cyberpunk chrome & purple neon
// ═══════════════════════════════════════════════════════════

private val neonMetalConfig = ThemeConfig(
    lightColors = lightColorScheme(
        primary = Color(0xFF5C2D91),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFF9B6FCF),
        secondary = Color(0xFFB388FF),
        onSecondary = Color(0xFF1A0033),
        secondaryContainer = Color(0xFFD1B3FF),
        tertiary = Color(0xFFE040FB),
        onTertiary = Color(0xFFFFFFFF),
        background = Color(0xFFF3EDF7),
        onBackground = Color(0xFF1B1021),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF1B1021),
        surfaceVariant = Color(0xFFE8DEF2),
        onSurfaceVariant = Color(0xFF4A4458),
        error = Color(0xFFFF1744)
    ),
    darkColors = darkColorScheme(
        primary = Color(0xFFBB86FC),
        onPrimary = Color(0xFF3A1066),
        primaryContainer = Color(0xFF5C2D91),
        secondary = Color(0xFFD1B3FF),
        onSecondary = Color(0xFF1A0033),
        secondaryContainer = Color(0xFF7C4DFF),
        tertiary = Color(0xFFE040FB),
        onTertiary = Color(0xFFFFFFFF),
        background = Color(0xFF0D0B14),
        onBackground = Color(0xFFE2D9F0),
        surface = Color(0xFF1A1525),
        onSurface = Color(0xFFE2D9F0),
        surfaceVariant = Color(0xFF2D2640),
        onSurfaceVariant = Color(0xFFB0B0C0),
        error = Color(0xFFFF1744)
    ),
    typography = Typography(
        displayLarge = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 34.sp, lineHeight = 42.sp, letterSpacing = 2.sp),
        headlineLarge = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = 1.5.sp),
        headlineMedium = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = 1.sp),
        headlineSmall = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 28.sp, letterSpacing = 0.8.sp),
        titleLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 26.sp),
        titleMedium = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp),
        bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 20.sp),
        bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp, lineHeight = 16.sp),
        labelLarge = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.5.sp),
        labelSmall = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp)
    ),
    extraColors = ExtraColors(
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
    ),
    forceDark = false
)

// ═══════════════════════════════════════════════════════════
//  2. RETRO C64 — Commodore 64 blue screen & blocky mono
// ═══════════════════════════════════════════════════════════

private val c64Blue = Color(0xFF4040E0)
private val c64LightBlue = Color(0xFF6C6CF8)
private val c64Bg = Color(0xFF40318D)
private val c64Text = Color(0xFFA0A0FF)
private val c64White = Color(0xFFD0D0FF)
private val c64Dark = Color(0xFF1A1050)

private val retroC64Config = ThemeConfig(
    lightColors = lightColorScheme(
        primary = c64Blue,
        onPrimary = Color.White,
        primaryContainer = c64LightBlue,
        secondary = Color(0xFF70A4B2),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFA0D0E0),
        tertiary = Color(0xFFE0E040),
        onTertiary = Color.Black,
        background = Color(0xFFD0D0FF),
        onBackground = Color(0xFF1A1050),
        surface = Color(0xFFE8E8FF),
        onSurface = Color(0xFF1A1050),
        surfaceVariant = Color(0xFFC0C0E0),
        onSurfaceVariant = Color(0xFF3A3070),
        error = Color(0xFFE04040)
    ),
    darkColors = darkColorScheme(
        primary = c64LightBlue,
        onPrimary = c64Dark,
        primaryContainer = c64Blue,
        secondary = Color(0xFF70A4B2),
        onSecondary = c64Dark,
        secondaryContainer = Color(0xFF405060),
        tertiary = Color(0xFFE0E040),
        onTertiary = Color.Black,
        background = c64Bg,
        onBackground = c64Text,
        surface = Color(0xFF352878),
        onSurface = c64White,
        surfaceVariant = Color(0xFF483890),
        onSurfaceVariant = c64Text,
        error = Color(0xFFE04040)
    ),
    typography = Typography(
        displayLarge = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = 3.sp),
        headlineLarge = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 34.sp, letterSpacing = 2.sp),
        headlineMedium = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 30.sp, letterSpacing = 2.sp),
        headlineSmall = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 26.sp, letterSpacing = 1.5.sp),
        titleLarge = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 1.sp),
        titleMedium = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 22.sp, letterSpacing = 1.sp),
        bodyLarge = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, lineHeight = 22.sp, letterSpacing = 0.5.sp),
        bodyMedium = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 20.sp, letterSpacing = 0.5.sp),
        bodySmall = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
        labelLarge = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 1.sp),
        labelSmall = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 10.sp, lineHeight = 14.sp, letterSpacing = 1.sp)
    ),
    extraColors = ExtraColors(
        readIndicator = Color(0xFF50E050),
        unreadIndicator = Color(0xFFE0E040),
        accentGlow1 = c64LightBlue,
        accentGlow2 = Color(0xFFE0E040),
        accentGlow3 = Color(0xFF70A4B2),
        fictionBadge = Color(0xFFE0E040),
        nonFictionBadge = Color(0xFF70A4B2),
        signInGradientStart = Color(0xFF1A1050),
        signInGradientMid = c64Bg,
        signInGradientEnd = Color(0xFF282060),
        scanLineColor = Color(0xFF6060A0).copy(alpha = 0.08f)
    ),
    forceDark = false
)

// ═══════════════════════════════════════════════════════════
//  3. MOTORHEAD — Black, bone white, whiskey gold, grime
// ═══════════════════════════════════════════════════════════

private val mhBlack = Color(0xFF0A0A0A)
private val mhDarkGray = Color(0xFF1A1A1A)
private val mhBone = Color(0xFFE8DCC8)
private val mhGold = Color(0xFFD4A017)
private val mhDirtyGold = Color(0xFF8B6914)

private val motorheadConfig = ThemeConfig(
    lightColors = lightColorScheme(
        primary = Color(0xFF2A2A2A),
        onPrimary = mhBone,
        primaryContainer = Color(0xFF3A3A3A),
        secondary = mhDirtyGold,
        onSecondary = Color.Black,
        secondaryContainer = Color(0xFFBFA050),
        tertiary = Color(0xFFCC0000),
        onTertiary = Color.White,
        background = mhBone,
        onBackground = Color(0xFF1A1A1A),
        surface = Color(0xFFF0E8D8),
        onSurface = Color(0xFF1A1A1A),
        surfaceVariant = Color(0xFFD8CDB8),
        onSurfaceVariant = Color(0xFF4A4A4A),
        error = Color(0xFFCC0000)
    ),
    darkColors = darkColorScheme(
        primary = mhGold,
        onPrimary = mhBlack,
        primaryContainer = mhDirtyGold,
        secondary = mhBone,
        onSecondary = mhBlack,
        secondaryContainer = Color(0xFF3A3A3A),
        tertiary = Color(0xFFCC0000),
        onTertiary = Color.White,
        background = mhBlack,
        onBackground = mhBone,
        surface = mhDarkGray,
        onSurface = mhBone,
        surfaceVariant = Color(0xFF2A2A2A),
        onSurfaceVariant = Color(0xFF999080),
        error = Color(0xFFCC0000)
    ),
    typography = Typography(
        displayLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Black, fontSize = 36.sp, lineHeight = 44.sp, letterSpacing = 3.sp),
        headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Black, fontSize = 30.sp, lineHeight = 38.sp, letterSpacing = 2.sp),
        headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = 1.5.sp),
        headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, lineHeight = 28.sp, letterSpacing = 1.sp),
        titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 26.sp),
        titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 24.sp),
        bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
        bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
        labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Black, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 1.5.sp),
        labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 1.sp)
    ),
    extraColors = ExtraColors(
        readIndicator = Color(0xFF6B8E23),
        unreadIndicator = mhGold,
        accentGlow1 = mhGold,
        accentGlow2 = Color(0xFFCC0000),
        accentGlow3 = mhBone,
        fictionBadge = mhGold,
        nonFictionBadge = mhBone,
        signInGradientStart = Color(0xFF000000),
        signInGradientMid = Color(0xFF1A1A0A),
        signInGradientEnd = Color(0xFF0A0A0A),
        scanLineColor = mhGold.copy(alpha = 0.04f)
    ),
    forceDark = true
)

// ═══════════════════════════════════════════════════════════
//  4. BARBIE — Hot pink, dreamy pastels, playful
// ═══════════════════════════════════════════════════════════

private val barbiePink = Color(0xFFE91E8C)
private val barbieLightPink = Color(0xFFF48FB1)
private val barbiePastel = Color(0xFFFCE4EC)
private val barbieMagenta = Color(0xFFC2185B)

private val barbieConfig = ThemeConfig(
    lightColors = lightColorScheme(
        primary = barbiePink,
        onPrimary = Color.White,
        primaryContainer = barbieLightPink,
        secondary = Color(0xFF9C27B0),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFE1BEE7),
        tertiary = Color(0xFFFF6090),
        onTertiary = Color.White,
        background = barbiePastel,
        onBackground = Color(0xFF4A0028),
        surface = Color(0xFFFFF0F5),
        onSurface = Color(0xFF4A0028),
        surfaceVariant = Color(0xFFF8D7E8),
        onSurfaceVariant = Color(0xFF7A3050),
        error = Color(0xFFD32F2F)
    ),
    darkColors = darkColorScheme(
        primary = barbieLightPink,
        onPrimary = Color(0xFF4A0028),
        primaryContainer = barbieMagenta,
        secondary = Color(0xFFCE93D8),
        onSecondary = Color(0xFF3A0050),
        secondaryContainer = Color(0xFF7B1FA2),
        tertiary = Color(0xFFFF80AB),
        onTertiary = Color(0xFF4A0028),
        background = Color(0xFF200010),
        onBackground = Color(0xFFF8D7E8),
        surface = Color(0xFF301020),
        onSurface = Color(0xFFF8D7E8),
        surfaceVariant = Color(0xFF401830),
        onSurfaceVariant = Color(0xFFD0A0B8),
        error = Color(0xFFFF5252)
    ),
    typography = Typography(
        displayLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = 0.5.sp),
        headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 36.sp),
        headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp),
        headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp),
        titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 18.sp, lineHeight = 26.sp),
        titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp),
        bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
        bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Light, fontSize = 12.sp, lineHeight = 16.sp),
        labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
        labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp)
    ),
    extraColors = ExtraColors(
        readIndicator = Color(0xFF66BB6A),
        unreadIndicator = Color(0xFFFFB74D),
        accentGlow1 = barbiePink,
        accentGlow2 = Color(0xFFFF80AB),
        accentGlow3 = Color(0xFF9C27B0),
        fictionBadge = barbiePink,
        nonFictionBadge = Color(0xFF9C27B0),
        signInGradientStart = Color(0xFF4A0028),
        signInGradientMid = barbieMagenta,
        signInGradientEnd = Color(0xFF880E4F),
        scanLineColor = barbiePink.copy(alpha = 0.03f)
    ),
    forceDark = false
)

// ═══════════════════════════════════════════════════════════
//  5. OCEAN DEPTHS — Deep sea blues & bioluminescence
// ═══════════════════════════════════════════════════════════

private val oceanDeep = Color(0xFF0D1B2A)
private val oceanMid = Color(0xFF1B2838)
private val oceanCyan = Color(0xFF00BCD4)
private val oceanBio = Color(0xFF00E5FF)
private val oceanGreen = Color(0xFF26A69A)

private val oceanDepthsConfig = ThemeConfig(
    lightColors = lightColorScheme(
        primary = Color(0xFF006064),
        onPrimary = Color.White,
        primaryContainer = Color(0xFF80DEEA),
        secondary = oceanGreen,
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFB2DFDB),
        tertiary = Color(0xFF0097A7),
        onTertiary = Color.White,
        background = Color(0xFFE0F7FA),
        onBackground = Color(0xFF0D1B2A),
        surface = Color(0xFFF0FAFB),
        onSurface = Color(0xFF0D1B2A),
        surfaceVariant = Color(0xFFB2EBF2),
        onSurfaceVariant = Color(0xFF204050),
        error = Color(0xFFD32F2F)
    ),
    darkColors = darkColorScheme(
        primary = oceanCyan,
        onPrimary = oceanDeep,
        primaryContainer = Color(0xFF006064),
        secondary = oceanGreen,
        onSecondary = oceanDeep,
        secondaryContainer = Color(0xFF1A4040),
        tertiary = oceanBio,
        onTertiary = oceanDeep,
        background = oceanDeep,
        onBackground = Color(0xFFB0E0E8),
        surface = oceanMid,
        onSurface = Color(0xFFB0E0E8),
        surfaceVariant = Color(0xFF1E3040),
        onSurfaceVariant = Color(0xFF80B0C0),
        error = Color(0xFFFF5252)
    ),
    typography = Typography(
        displayLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = 1.sp),
        headlineLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 36.sp),
        headlineMedium = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp),
        headlineSmall = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp),
        titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 18.sp, lineHeight = 26.sp),
        titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp),
        bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 20.sp),
        bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp, lineHeight = 16.sp),
        labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
        labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp)
    ),
    extraColors = ExtraColors(
        readIndicator = Color(0xFF00E676),
        unreadIndicator = Color(0xFFFFAB40),
        accentGlow1 = oceanCyan,
        accentGlow2 = oceanBio,
        accentGlow3 = oceanGreen,
        fictionBadge = oceanBio,
        nonFictionBadge = oceanGreen,
        signInGradientStart = Color(0xFF050D15),
        signInGradientMid = oceanDeep,
        signInGradientEnd = Color(0xFF0A1520),
        scanLineColor = oceanCyan.copy(alpha = 0.03f)
    ),
    forceDark = false
)

// ═══════════════════════════════════════════════════════════
//  6. FOREST INK — Dark greens, parchment, old manuscript
// ═══════════════════════════════════════════════════════════

private val forestDark = Color(0xFF0A1A0A)
private val forestGreen = Color(0xFF2E7D32)
private val forestLight = Color(0xFF66BB6A)
private val parchment = Color(0xFFF5F0E0)
private val inkBrown = Color(0xFF4E342E)

private val forestInkConfig = ThemeConfig(
    lightColors = lightColorScheme(
        primary = forestGreen,
        onPrimary = Color.White,
        primaryContainer = Color(0xFFA5D6A7),
        secondary = inkBrown,
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFBCAAA4),
        tertiary = Color(0xFF8D6E63),
        onTertiary = Color.White,
        background = parchment,
        onBackground = Color(0xFF1A1A10),
        surface = Color(0xFFFAF8F0),
        onSurface = Color(0xFF1A1A10),
        surfaceVariant = Color(0xFFE8E0C8),
        onSurfaceVariant = Color(0xFF4A4430),
        error = Color(0xFFC62828)
    ),
    darkColors = darkColorScheme(
        primary = forestLight,
        onPrimary = forestDark,
        primaryContainer = forestGreen,
        secondary = Color(0xFFBCAAA4),
        onSecondary = forestDark,
        secondaryContainer = inkBrown,
        tertiary = Color(0xFFA1887F),
        onTertiary = forestDark,
        background = forestDark,
        onBackground = Color(0xFFD0C8A8),
        surface = Color(0xFF1A2A1A),
        onSurface = Color(0xFFD0C8A8),
        surfaceVariant = Color(0xFF2A3A2A),
        onSurfaceVariant = Color(0xFF90A080),
        error = Color(0xFFEF5350)
    ),
    typography = Typography(
        displayLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 42.sp, letterSpacing = 0.5.sp),
        headlineLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 36.sp),
        headlineMedium = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp),
        headlineSmall = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp),
        titleLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium, fontSize = 18.sp, lineHeight = 26.sp),
        titleMedium = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp),
        bodyLarge = TextStyle(fontFamily = FontFamily.Serif, fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = TextStyle(fontFamily = FontFamily.Serif, fontSize = 14.sp, lineHeight = 20.sp),
        bodySmall = TextStyle(fontFamily = FontFamily.Serif, fontSize = 12.sp, lineHeight = 16.sp),
        labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
        labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp)
    ),
    extraColors = ExtraColors(
        readIndicator = forestLight,
        unreadIndicator = Color(0xFFD4A017),
        accentGlow1 = forestLight,
        accentGlow2 = Color(0xFF8D6E63),
        accentGlow3 = Color(0xFFD4A017),
        fictionBadge = forestLight,
        nonFictionBadge = Color(0xFF8D6E63),
        signInGradientStart = Color(0xFF050A05),
        signInGradientMid = forestDark,
        signInGradientEnd = Color(0xFF0A150A),
        scanLineColor = forestLight.copy(alpha = 0.03f)
    ),
    forceDark = false
)

// ═══════════════════════════════════════════════════════════
//  7. SOLAR FLARE — Warm amber, ember red, dark charcoal
// ═══════════════════════════════════════════════════════════

private val solarAmber = Color(0xFFFF8F00)
private val solarRed = Color(0xFFE64A19)
private val solarDark = Color(0xFF1A1210)
private val solarCharcoal = Color(0xFF2D2420)
private val solarGold = Color(0xFFFFD54F)

private val solarFlareConfig = ThemeConfig(
    lightColors = lightColorScheme(
        primary = Color(0xFFE65100),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFFFCC80),
        secondary = Color(0xFFF57C00),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFFFE0B2),
        tertiary = solarRed,
        onTertiary = Color.White,
        background = Color(0xFFFFF8E8),
        onBackground = Color(0xFF1A1210),
        surface = Color(0xFFFFFCF0),
        onSurface = Color(0xFF1A1210),
        surfaceVariant = Color(0xFFF0E0C8),
        onSurfaceVariant = Color(0xFF5A4A38),
        error = Color(0xFFD32F2F)
    ),
    darkColors = darkColorScheme(
        primary = solarAmber,
        onPrimary = solarDark,
        primaryContainer = Color(0xFFE65100),
        secondary = solarGold,
        onSecondary = solarDark,
        secondaryContainer = Color(0xFF5A4020),
        tertiary = solarRed,
        onTertiary = Color.White,
        background = solarDark,
        onBackground = Color(0xFFE8D0B0),
        surface = solarCharcoal,
        onSurface = Color(0xFFE8D0B0),
        surfaceVariant = Color(0xFF3A3028),
        onSurfaceVariant = Color(0xFFA89080),
        error = Color(0xFFFF5252)
    ),
    typography = Typography(
        displayLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 42.sp, letterSpacing = 1.sp),
        headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 36.sp),
        headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp),
        headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp),
        titleLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 26.sp),
        titleMedium = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp),
        bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 20.sp),
        bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp, lineHeight = 16.sp),
        labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.5.sp),
        labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp)
    ),
    extraColors = ExtraColors(
        readIndicator = Color(0xFF66BB6A),
        unreadIndicator = solarAmber,
        accentGlow1 = solarAmber,
        accentGlow2 = solarRed,
        accentGlow3 = solarGold,
        fictionBadge = solarAmber,
        nonFictionBadge = solarGold,
        signInGradientStart = Color(0xFF0A0805),
        signInGradientMid = solarDark,
        signInGradientEnd = Color(0xFF1A1008),
        scanLineColor = solarAmber.copy(alpha = 0.03f)
    ),
    forceDark = false
)
