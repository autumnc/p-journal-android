package com.pjournal.app.ui.theme

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = WarmPrimary,
    secondary = WarmSecondary,
    tertiary = WarmTertiary,
    background = WarmBackground,
    surface = WarmSurface,
    onBackground = WarmOnBackground,
    onSurface = WarmOnSurface,
    surfaceVariant = WarmDivider,
    outline = WarmDim
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkWarmPrimary,
    secondary = DarkWarmSecondary,
    tertiary = DarkWarmTertiary,
    background = DarkWarmBackground,
    surface = DarkWarmSurface,
    onBackground = DarkWarmOnBackground,
    onSurface = DarkWarmOnSurface,
    surfaceVariant = DarkWarmDivider,
    outline = DarkWarmDim
)

private val EinkColorScheme = lightColorScheme(
    primary = EinkPrimary,
    secondary = EinkSecondary,
    tertiary = EinkTertiary,
    background = EinkBackground,
    surface = EinkSurface,
    onBackground = EinkOnBackground,
    onSurface = EinkOnSurface,
    surfaceVariant = EinkDivider,
    outline = EinkDim
)

@Composable
fun PJournalTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    einkMode: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        einkMode -> EinkColorScheme
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars =
                !darkTheme || einkMode
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = PJournalTypography,
        content = content
    )
}

/** Returns a scaled duration: instant in e-ink mode, normal otherwise. */
fun einkDuration(normalMs: Int, einkMode: Boolean, scale: Float = 0f): Int {
    return if (einkMode) (normalMs * scale).toInt() else normalMs
}
