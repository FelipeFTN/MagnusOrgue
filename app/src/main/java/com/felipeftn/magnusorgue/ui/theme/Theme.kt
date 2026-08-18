package com.felipeftn.magnusorgue.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Dark only. Organ consoles live in dim churches; nobody wants a white
// screen glaring back at them mid-service.

val Gold = Color(0xFFC99A3A)
val Ivory = Color(0xFFE9E2D0)
val NearBlack = Color(0xFF121212)
val Charcoal = Color(0xFF1D1D22)

private val OrganColors = darkColorScheme(
    primary = Gold,
    onPrimary = Color(0xFF1A1408),
    background = NearBlack,
    onBackground = Ivory,
    surface = Charcoal,
    onSurface = Ivory,
    surfaceVariant = Charcoal,
    onSurfaceVariant = Color(0xFF9A937F),
    error = Color(0xFFCF6679),
    onError = Color(0xFF1A0808),
)

@Composable
fun MagnusOrgueTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = OrganColors, content = content)
}
