package com.ct.explorer.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val PureWhiteColorScheme = lightColorScheme(
    primary = CtOrange,
    onPrimary = Color.White,
    primaryContainer = CtOrangeSubtle,
    onPrimaryContainer = CtOrangeDark,
    secondary = MiBlue,
    onSecondary = Color.White,
    secondaryContainer = MiBlueLight,
    onSecondaryContainer = MiBlue,
    background = Color.White,
    onBackground = Gray900,
    surface = Color.White,
    onSurface = Gray900,
    surfaceVariant = Color(0xFFF3F4F6),
    onSurfaceVariant = Gray600,
    surfaceTint = Color.Transparent,
    surfaceContainer = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainerLowest = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color.White,
    surfaceBright = Color.White,
    surfaceDim = Color.White,
    outline = Gray300,
    outlineVariant = Gray200
)

val LightColorScheme = PureWhiteColorScheme
val DarkColorScheme = PureWhiteColorScheme
val AmoledColorScheme = PureWhiteColorScheme

@Composable
fun CentExplorerTheme(
    darkTheme: Boolean = false,
    amoledMode: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = PureWhiteColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = Color.White.toArgb()
                window.navigationBarColor = Color.White.toArgb()
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = true
                insetsController.isAppearanceLightNavigationBars = true
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
