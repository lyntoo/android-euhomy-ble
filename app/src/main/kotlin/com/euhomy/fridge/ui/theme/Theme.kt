package com.euhomy.fridge.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary        = IcyBlue40,
    onPrimary      = ColdSurface,
    primaryContainer = IcyBlue80,
    secondary      = IcyBlueGrey40,
    onSecondary    = ColdSurface,
    secondaryContainer = IcyBlueGrey80,
    background     = ColdBackground,
    surface        = ColdSurface,
    error          = ErrorRed,
)

private val DarkColors = darkColorScheme(
    primary        = IcyBlue80,
    onPrimary      = IcyBlue40,
    primaryContainer = IcyBlue40,
    secondary      = IcyBlueGrey80,
    onSecondary    = IcyBlueGrey40,
    secondaryContainer = IcyBlueGrey40,
    error          = ErrorRed,
)

@Composable
fun EuhomyTheme(
    darkTheme:          Boolean = isSystemInDarkTheme(),
    dynamicColor:       Boolean = true,
    content:            @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme  -> DarkColors
        else       -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = AppTypography,
        content     = content,
    )
}
