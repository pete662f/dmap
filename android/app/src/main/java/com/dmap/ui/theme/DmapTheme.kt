package com.dmap.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DmapLightColorScheme = lightColorScheme(
    primary = Color(0xFF1D5DD8),
    onPrimary = Color.White,
    secondary = Color(0xFF567A67),
    onSecondary = Color.White,
    background = Color(0xFFF5F2EC),
    onBackground = Color(0xFF171A1E),
    surface = Color(0xFFFDFCFA),
    onSurface = Color(0xFF171A1E),
    surfaceVariant = Color(0xFFE8E2D8),
    onSurfaceVariant = Color(0xFF4A4E55),
    error = Color(0xFFB93838),
    onError = Color.White,
)

private val DmapDarkColorScheme = darkColorScheme(
    primary = Color(0xFF7AA8FF),
    onPrimary = Color(0xFF0A2E73),
    secondary = Color(0xFFA9C7B2),
    onSecondary = Color(0xFF163524),
    background = Color(0xFF121416),
    onBackground = Color(0xFFF3EFE8),
    surface = Color(0xFF1A1D21),
    onSurface = Color(0xFFF3EFE8),
    surfaceVariant = Color(0xFF2D3238),
    onSurfaceVariant = Color(0xFFD7D4CD),
    error = Color(0xFFFF908A),
    onError = Color(0xFF601410),
)

@Composable
fun DmapTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DmapDarkColorScheme else DmapLightColorScheme,
        content = content,
    )
}
