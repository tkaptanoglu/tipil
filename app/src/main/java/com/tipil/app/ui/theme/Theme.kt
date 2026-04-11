package com.tipil.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
fun TipilTheme(
    appTheme: AppTheme = AppTheme.NEON_METAL,
    systemDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val config = themeConfigFor(appTheme)
    val useDark = config.forceDark || systemDarkTheme
    val colorScheme = if (useDark) config.darkColors else config.lightColors
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !useDark
        }
    }

    CompositionLocalProvider(LocalExtraColors provides config.extraColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = config.typography,
            content = content
        )
    }
}
