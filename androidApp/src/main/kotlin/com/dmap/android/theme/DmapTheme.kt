package com.dmap.android.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * Design tokens are the one UI thing worth sharing between platforms — but as
 * values, not as components. When the palette settles, move the colour
 * definitions into `shared/` and have this and the SwiftUI theme both read
 * them. Until then this is a placeholder so the scaffold renders.
 */
@Composable
fun DmapTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme(),
        content = content,
    )
}
